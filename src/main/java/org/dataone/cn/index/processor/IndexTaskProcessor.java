/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For 
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 * 
 * $Id$
 */

package org.dataone.cn.index.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
import org.dataone.client.ObjectFormatCache;
import org.dataone.cn.hazelcast.HazelcastClientInstance;
import org.dataone.cn.index.task.IndexTask;
import org.dataone.cn.index.task.IndexTaskRepository;
import org.dataone.cn.indexer.XPathDocumentParser;
import org.dataone.cn.indexer.resourcemap.ResourceMap;
import org.dataone.cn.indexer.solrhttp.HTTPService;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.configuration.Settings;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.ObjectFormat;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.SystemMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate3.HibernateOptimisticLockingFailureException;
import org.w3c.dom.Document;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.IMap;

/**
 * IndexTaskProcessor is the controller class for processing IndexTasks. These
 * tasks are generated by the IndexTaskGenerator class and associated
 * collaborators. IndexTaskProcessor uses the IndexTaskRepository to locate
 * IndexTasks for processing and delegates to IndexTaskProcessingStrategy
 * implementations for actual processing behavior.
 * 
 * @author sroseboo
 * 
 */
public class IndexTaskProcessor {

    private static Logger logger = Logger.getLogger(IndexTaskProcessor.class.getName());

    @Autowired
    private IndexTaskRepository repo;

    @Autowired
    private ArrayList<XPathDocumentParser> documentParsers;

    @Autowired
    private IndexTaskProcessingStrategy deleteProcessor;

    @Autowired
    private IndexTaskProcessingStrategy updateProcessor;

    @Autowired
    private HTTPService httpService;

    private static final String FORMAT_TYPE_DATA = "DATA";

    private HazelcastClient hzClient;

    private static final String HZ_OBJECT_PATH = Settings.getConfiguration().getString(
            "dataone.hazelcast.objectPath");

    private static final String HZ_SYSTEM_METADATA = Settings.getConfiguration().getString(
            "dataone.hazelcast.systemMetadata");

    private IMap<Identifier, String> objectPaths;
    private IMap<Identifier, SystemMetadata> systemMetadata;

    private String solrQueryUri;

    public IndexTaskProcessor() {
    }

    /**
     * Start a round of IndexTask processing. The IndexTask data store is
     * abstracted as a queue of tasks to process ordered by priority and
     * modification date. Typically invoked periodically by a quartz scheduled
     * job.
     */
    public void processIndexTaskQueue() {
        startHazelClient();
        List<IndexTask> queue = getIndexTaskQueue();
        IndexTask task = getNextIndexTask(queue);
        while (task != null) {
            processTask(task);
            task = getNextIndexTask(queue);
        }
    }

    private void processTask(IndexTask task) {
        try {
            if (task.isDeleteTask()) {
                deleteProcessor.process(task);
            } else {
                updateProcessor.process(task);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            handleFailedTask(task);
            return;
        }
        repo.delete(task);
    }

    private void handleFailedTask(IndexTask task) {
        task.markFailed();
        saveTask(task);
    }

    private IndexTask getNextIndexTask(List<IndexTask> queue) {
        IndexTask task = null;
        while (task == null && queue.isEmpty() == false) {
            task = queue.remove(0);

            task.markInProgress();
            task = saveTask(task);

            if (task != null && task.isDeleteTask()) {
                return task;
            }

            if (task != null && !isObjectPathReady(task)) {
                task.markNew();
                saveTask(task);
                task = null;
                continue;
            }

            if (task != null && !isResourceMapReadyToIndex(task, queue)) {
                task = null;
            }
        }
        return task;
    }

    private boolean isResourceMapReadyToIndex(IndexTask task, List<IndexTask> queue) {
        boolean ready = true;
        if (representsResourceMap(task)) {
            Document docObject = loadDocument(task);
            if (docObject == null) {
                logger.debug("unable to load resource at object path: " + task.getObjectPath()
                        + ".  Marking new and continuing...");
                task.markNew();
                saveTask(task);
                ready = false;
            } else if (docObject != null) {
                ResourceMap rm = null;
                try {
                    rm = new ResourceMap(docObject);
                } catch (XPathExpressionException e) {
                    logger.error(e.getMessage(), e);
                }
                List<String> referencedIds = rm.getAllDocumentIDs();
                referencedIds.remove(task.getPid());
                if (areAllReferencedDocsIndexed(referencedIds) == false) {
                    logger.info("Not all map resource references indexed for map: " + task.getPid()
                            + ".  Marking new and continuing...");
                    task.markNew();
                    saveTask(task);
                    ready = false;
                }
            }
        }
        return ready;
    }

    private boolean areAllReferencedDocsIndexed(List<String> referencedIds) {
        if (referencedIds == null || referencedIds.size() == 0) {
            return true; // empty reference map...ok/ready to index.
        }
        List<SolrDoc> updateDocuments = null;
        int numberOfIndexedOrRemovedReferences = 0;
        try {
            updateDocuments = httpService.getDocuments(this.solrQueryUri, referencedIds);
            numberOfIndexedOrRemovedReferences = updateDocuments.size();
            if (updateDocuments.size() != referencedIds.size()) {
                for (String id : referencedIds) {
                    boolean foundId = false;
                    for (SolrDoc solrDoc : updateDocuments) {
                        if (solrDoc.getIdentifier().equals(id)) {
                            foundId = true;
                            break;
                        }
                    }
                    if (foundId == false) {
                        Identifier pid = new Identifier();
                        pid.setValue(id);
                        SystemMetadata smd = systemMetadata.get(pid);
                        if (notVisibleInIndex(smd)) {
                            numberOfIndexedOrRemovedReferences++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return referencedIds.size() == numberOfIndexedOrRemovedReferences;
    }

    private boolean notVisibleInIndex(SystemMetadata smd) {
        return (!SolrDoc.visibleInIndex(smd) && smd != null);
    }

    private boolean representsResourceMap(IndexTask task) {
        return ResourceMap.representsResourceMap(task.getFormatId());
    }

    // if objectPath is not filled, attempt to fill it via hazelclient.
    // if object path is not available, do not process this task yet.
    // if object path is available, update task and process
    private boolean isObjectPathReady(IndexTask task) {
        boolean ok = true;
        boolean isDataObject = isDataObject(task);
        if (task.getObjectPath() == null && !isDataObject) {
            String objectPath = retrieveObjectPath(task.getPid());
            if (objectPath == null) {
                ok = false;
            }
            task.setObjectPath(objectPath);
        }

        if (task.getObjectPath() != null && !isDataObject) {
            File objectPathFile = new File(task.getObjectPath());
            if (!objectPathFile.exists()) {
                // object path is present but doesnt correspond to a file
                // this task is not ready to index.
                ok = false;
                logger.error("Object path exists for pid: "
                        + task.getPid()
                        + " however the file location: "
                        + task.getObjectPath()
                        + " does not exist.  Marking not ready - task will be marked new and retried.");
            }
        }
        return ok;
    }

    private boolean isDataObject(IndexTask task) {
        ObjectFormat format = null;
        try {
            ObjectFormatIdentifier formatId = new ObjectFormatIdentifier();
            formatId.setValue(task.getFormatId());
            format = ObjectFormatCache.getInstance().getFormat(formatId);
        } catch (BaseException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return FORMAT_TYPE_DATA.equals(format.getFormatType());
    }

    private String retrieveObjectPath(String pid) {
        Identifier PID = new Identifier();
        PID.setValue(pid);
        return objectPaths.get(PID);
    }

    private void startHazelClient() {
        if (this.hzClient == null) {
            this.hzClient = HazelcastClientInstance.getHazelcastClient();
            this.objectPaths = this.hzClient.getMap(HZ_OBJECT_PATH);
            this.systemMetadata = this.hzClient.getMap(HZ_SYSTEM_METADATA);
        }
    }

    private List<IndexTask> getIndexTaskQueue() {
        return repo.findByStatusOrderByPriorityAscTaskModifiedDateAsc(IndexTask.STATUS_NEW);
    }

    private XPathDocumentParser getXPathDocumentParser() {
        return documentParsers.get(0);
    }

    private IndexTask saveTask(IndexTask task) {
        try {
            task = repo.save(task);
        } catch (HibernateOptimisticLockingFailureException e) {
            logger.debug("Unable to update index task for pid: " + task.getPid() + ".");
            task = null;
        }
        return task;
    }

    private Document loadDocument(IndexTask task) {
        Document docObject = null;
        try {
            docObject = getXPathDocumentParser().loadDocument(task.getObjectPath());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        if (docObject == null) {
            logger.error("Could not load OBJECT file for ID,Path=" + task.getPid() + ", "
                    + task.getObjectPath());
        }
        return docObject;
    }

    public void setSolrQueryUri(String uri) {
        this.solrQueryUri = uri;
    }
}
