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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPath;

import org.dataone.cn.indexer.XPathDocumentParser;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.w3c.dom.Document;

/**
 * Base functionality for document processors. A document processor represents
 * an object which is configured with SolrFields for deriving search index field
 * data from xml documents which the document processor is configured to run against.
 * For example, there will be specific document processor objects each science metadata
 * format.
 * 
 * matchDocuments property contains a list of formatIds which is used to determine if an
 * instance of a science metadata document should be processed by this document processor.
 * Basically matches the expected 'formatId' to the formatId of a candidate document.
 * 
 * fieldList property contains the collection of ISolrFields configured for operating over
 * a particular science metadata format.
 * 
 * 
 * User: Porter
 * Date: 9/22/11
 * Time: 3:24 PM
 */
public class AbstractDocumentSubprocessor implements IDocumentSubprocessor {

    /**
     * If xpath returns true execute the processDocument Method
     */
    private List<String> matchDocuments = null;
    private List<ISolrField> fieldList = new ArrayList<ISolrField>();

    public List<String> getMatchDocuments() {
        return matchDocuments;
    }

    public void setMatchDocuments(List<String> matchDocuments) {
        this.matchDocuments = matchDocuments;
    }

    /**
     * Default functionality is to process fields like XPathDocumentProcessor
     * and add fields to Solr Document This method maybe overridden to add
     * functionality such as retrieving and updating existing documents in the
     * index.
     * 
     * @param identifier
     *            identifier of System Metadata Document
     * @param docs
     *            Map of Solr Index documents use @identifier to retrieve the
     *            original System Metadata Document
     * @param doc
     *            System Metadata Document
     * @return map of Documents including updated Solr index document
     */
    public Map<String, SolrDoc> processDocument(String identifier, Map<String, SolrDoc> docs,
            InputStream is) throws Exception {

        SolrDoc metaDocument = docs.get(identifier);

        // get the stream as a Document
        Document doc = XPathDocumentParser.generateXmlDocument(is);
        
        for (ISolrField solrField : fieldList) {
            try {
                metaDocument.getFieldList().addAll(solrField.getFields(doc, identifier));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return docs;
    }

    /**
     * Returns true if subprocessor should be run against object
     * 
     * @param formatId the the document to be processed
     * @return true if this processor can parse the formatId
     */
    public boolean canProcess(String formatId) {
        return matchDocuments.contains(formatId);
    }   

    public void initExpression(XPath xpathObject) {
        for (ISolrField solrField : fieldList) {
            solrField.initExpression(xpathObject);
        }
    }

    public List<ISolrField> getFieldList() {
        return fieldList;
    }

    public void setFieldList(List<ISolrField> fieldList) {
        this.fieldList = fieldList;
    }
}
