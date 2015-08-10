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

package org.dataone.cn.indexer.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.codec.EncoderException;
import org.apache.log4j.Logger;
import org.dataone.cn.hazelcast.HazelcastClientFactory;
import org.dataone.cn.index.processor.IndexTaskDeleteProcessor;
import org.dataone.cn.index.processor.IndexTaskProcessingStrategy;
import org.dataone.cn.index.task.IndexTask;
import org.dataone.cn.indexer.XmlDocumentUtility;
import org.dataone.cn.indexer.parser.utility.SeriesIdResolver;
import org.dataone.cn.indexer.resourcemap.ResourceMap;
import org.dataone.cn.indexer.resourcemap.ResourceMapFactory;
import org.dataone.cn.indexer.solrhttp.HTTPService;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v2.SystemMetadata;
import org.dspace.foresite.OREParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Resource Map Document processor.  Operates on ORE/RDF objects.  Maps 
 * 'documents', 'documentedBy', and 'aggregates' relationships.
 * 
 * Uses org.dataone.cn.indexer.resourcemap.ResourceMap to update individual
 * SolrDoc objects with values for 'documents', 'documentedBy', and 'resourceMap'
 * (aggregates) fields.
 * 
 * Updates entries for related documents in index. For document relational
 * information refer to
 * http://mule1.dataone.org/ArchitectureDocs-current/design/
 * SearchMetadata.html#id4
 * 
 * User: Porter
 * Date: 9/26/11
 * Time: 3:51 PM
 */
public class ResourceMapSubprocessor implements IDocumentSubprocessor {

    private static Logger logger = Logger.getLogger(ResourceMapSubprocessor.class.getName());

    @Autowired
    private HTTPService httpService = null;

    @Autowired
    private String solrQueryUri = null;
    
    //@Autowired
    private IndexTaskDeleteProcessor deleteProcessor;

    private List<String> matchDocuments = null;
    private List<String> fieldsToMerge = new ArrayList<String>();

    /**
     * Merge updates with existing solr documents
     * 
     * @param indexDocument
     * @return
     * @throws IOException
     * @throws EncoderException
     * @throws XPathExpressionException
     */
    public SolrDoc mergeWithIndexedDocument(SolrDoc indexDocument) throws IOException,
            EncoderException, XPathExpressionException {

        SolrDoc solrDoc = httpService.retrieveDocumentFromSolrServer(indexDocument.getIdentifier(),
                solrQueryUri);
        if (solrDoc != null) {
            for (SolrElementField field : solrDoc.getFieldList()) {
                if (fieldsToMerge.contains(field.getName())
                        && !indexDocument.hasFieldWithValue(field.getName(), field.getValue())) {
                    indexDocument.addField(field);
                }
            }
        }
        return indexDocument;
    }

    /**
     * Implements IDocumentSubprocessor.processDocument method.
     * Creates a map of D1 Identifier string values to SolrDoc objects which represent
     * the Solr search index documents for the data package defined by the ORE/RDF document.
     * Each solr record in the data package is updated with 'documents', 'documentedBy',
     * and 'resourceMap' field values contained in the incoming Document doc method parameter.
     */
    @Override
    public Map<String, SolrDoc> processDocument(String identifier, Map<String, SolrDoc> docs,
            InputStream is) throws XPathExpressionException, IOException, EncoderException {
        SolrDoc resourceMapDoc = docs.get(identifier);
        List<SolrDoc> processedDocs = new ArrayList<SolrDoc>();
        try {
            Document doc = XmlDocumentUtility.generateXmlDocument(is);
            processedDocs = processResourceMap(resourceMapDoc, doc);
        } catch (OREParserException oreException) {
            logger.error("Unable to parse resource map: " + identifier
                    + ".  Unrecoverable parse exception:  task will not be re-tried.");
        } catch (SAXException e) {
            logger.error("Unable to parse resource map: " + identifier
                    + ".  Unrecoverable parse exception:  task will not be re-tried.");
        }
        Map<String, SolrDoc> processedDocsMap = new HashMap<String, SolrDoc>();
        for (SolrDoc processedDoc : processedDocs) {
            processedDocsMap.put(processedDoc.getIdentifier(), processedDoc);
        }
        return processedDocsMap;
    }

    private List<SolrDoc> processResourceMap(SolrDoc indexDocument, Document resourceMapDocument)
            throws OREParserException, XPathExpressionException, IOException, EncoderException {

        ResourceMap resourceMap = ResourceMapFactory.buildResourceMap(resourceMapDocument);
        List<String> documentIds = resourceMap.getAllDocumentIDs();
        this.clearSidChain(indexDocument.getIdentifier(), documentIds);
        List<SolrDoc> updateDocuments = getHttpService().getDocumentsById(getSolrQueryUri(),
                documentIds);
        List<SolrDoc> mergedDocuments = resourceMap.mergeIndexedDocuments(updateDocuments);
        mergedDocuments.add(indexDocument);
        return mergedDocuments;
    }
    
    private void clearSidChain(String resourceMapIdentifier, List<String> relatedDocs) {
    	
		// check that we are, indeed, dealing with a SID-identified ORE
    	Identifier identifier = new Identifier();
    	identifier.setValue(resourceMapIdentifier);

    	boolean containsSeriesId = true;
        for (String relatedDoc : relatedDocs) {
        	Identifier relatedPid = new Identifier();
            relatedPid.setValue(relatedDoc);
            if (SeriesIdResolver.isSeriesId(relatedPid)) {
            	containsSeriesId = true;
            	break;
            }
        }
    	// if this package contains a SID, then we need to clear out old info
		if (containsSeriesId ) {
			Identifier pidToProcess = identifier;
			while (pidToProcess != null) {
				// queue a delete processing of all versions in the SID chain
	            SystemMetadata sysmeta = HazelcastClientFactory.getSystemMetadataMap().get(pidToProcess);
	            String objectPath = HazelcastClientFactory.getObjectPathMap().get(pidToProcess);
	            logger.debug("Removing pidToProcess===" + pidToProcess.getValue());
	            logger.debug("Removing objectPath===" + objectPath);
	            
	            IndexTask task = new IndexTask(sysmeta, objectPath);
	    		try {
	    			deleteProcessor.process(task);
	    		} catch (Exception e) {
	    			logger.error(e.getMessage(), e);
	    		}
	    		// go to the next one
	        	pidToProcess = sysmeta.getObsoletes();
			}
        	
    	}  
        
    }

    public HTTPService getHttpService() {
        return httpService;
    }

    public void setHttpService(HTTPService httpService) {
        this.httpService = httpService;
    }

    public String getSolrQueryUri() {
        return solrQueryUri;
    }

    public void setSolrQueryUri(String solrQueryUri) {
        this.solrQueryUri = solrQueryUri;
    }

    public List<String> getMatchDocuments() {
        return matchDocuments;
    }

    public void setMatchDocuments(List<String> matchDocuments) {
        this.matchDocuments = matchDocuments;
    }

    public boolean canProcess(String formatId) {
        return matchDocuments.contains(formatId);
    }

    public List<String> getFieldsToMerge() {
        return fieldsToMerge;
    }

    public void setFieldsToMerge(List<String> fieldsToMerge) {
        this.fieldsToMerge = fieldsToMerge;
    }
}
