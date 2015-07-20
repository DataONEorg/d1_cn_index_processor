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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.codec.EncoderException;
import org.dataone.client.v2.formats.ObjectFormatCache;
import org.dataone.cn.hazelcast.HazelcastClientFactory;
import org.dataone.cn.index.generator.IndexTaskGenerator;
import org.dataone.cn.index.processor.IndexTaskProcessor;
import org.dataone.cn.indexer.solrhttp.HTTPService;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v2.ObjectFormat;
import org.dataone.service.types.v2.SystemMetadata;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseReprocessSubprocessor implements IDocumentSubprocessor {

    @Autowired
    private HTTPService httpService;

    @Autowired
    private String solrQueryUri;
    
    @Autowired
    IndexTaskGenerator indexTaskGenerator;
    
    @Autowired
    IndexTaskProcessor indexTaskProcessor;
    
    private List<String> matchDocuments = null;

    private List<String> relationFields;

    public BaseReprocessSubprocessor() {
    }

	@Override
    public Map<String, SolrDoc> processDocument(String identifier, Map<String, SolrDoc> docs, InputStream is)
            throws Exception {
		
		Identifier id = new Identifier();
		id.setValue(identifier);
		SystemMetadata sysMeta = HazelcastClientFactory.getSystemMetadataMap().get(id);
		
		Identifier seriesId = sysMeta.getSeriesId();
		
		// only need to reprocess for series Id
		if (seriesId != null) {
			// find the other objects in the series
			List<SolrDoc> previousDocs = httpService.getDocumentsByField(solrQueryUri, Collections.singletonList(seriesId.getValue()), 
					SolrElementField.FIELD_SERIES_ID, true);
				        
	        if (previousDocs != null && !previousDocs.isEmpty()) {
	        	List<Identifier> pidsToProcess = new ArrayList<Identifier>();
	        	for (SolrDoc indexedDoc: previousDocs) {
		        	for (String fieldName: relationFields) {
		        		// are there relations that need to be reindexed?
			        	String relationFieldId = indexedDoc.getFirstFieldValue(fieldName);
			        	if (relationFieldId != null) {
				            Identifier relatedPid = new Identifier();
				            relatedPid.setValue(relationFieldId);
				            // only need to reprocess related docs once
				            if (!pidsToProcess.contains(relatedPid)) {
				            	pidsToProcess.add(relatedPid);
				            	// queue a reprocessing of this related document
								SystemMetadata relatedSysMeta = HazelcastClientFactory.getSystemMetadataMap().get(relatedPid);
					            String objectPath = HazelcastClientFactory.getObjectPathMap().get(relatedPid);
								indexTaskGenerator.processSystemMetaDataUpdate(relatedSysMeta, objectPath);
				            }
			        	}
		        	}
	        	}
	        }
		}
		
        return docs;
    }
	
	@Override
	public boolean canProcess(String formatId) {
		// if we are given match formats, use them
		if (matchDocuments != null) {
			return matchDocuments.contains(formatId);
		} 
		
		// otherwise just make sure it's not a RESOURCE type
		ObjectFormatIdentifier ofi = new ObjectFormatIdentifier();
		ofi.setValue(formatId);
		ObjectFormat objectFormat = null;
		try {
			objectFormat = ObjectFormatCache.getInstance().getFormat(ofi);
		} catch (BaseException e) {
			e.printStackTrace();
		}
		if (objectFormat != null) {
			return !objectFormat.getFormatType().equalsIgnoreCase("RESOURCE");
		}
		
		// no real harm processing again
		return true;
	}

	@Override
	public SolrDoc mergeWithIndexedDocument(SolrDoc indexDocument)
			throws IOException, EncoderException, XPathExpressionException {
		// just return the given document
		return indexDocument;
	}

	public List<String> getRelationFields() {
		return relationFields;
	}

	public void setRelationFields(List<String> relationFields) {
		this.relationFields = relationFields;
	}

}