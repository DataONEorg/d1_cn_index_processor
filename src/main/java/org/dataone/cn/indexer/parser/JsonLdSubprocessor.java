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

import java.io.*;
import java.util.*;

import javax.xml.xpath.XPathExpressionException;

import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.cn.index.util.PerformanceLogger;
import org.dataone.cn.indexer.annotation.SparqlField;
import org.dataone.cn.indexer.annotation.TripleStoreService;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.cn.indexer.solrhttp.SolrElementField;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.dataone.configuration.Settings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;

/**
 * This sub-processor handles the index of the json-ld documents.
 * The documents will be loaded into a triple store, then SPARQL will be run.
 * @author tao
 *
 */
public class JsonLdSubprocessor implements IDocumentSubprocessor {
    private static Log log = LogFactory.getLog(JsonLdSubprocessor.class);
    private static PerformanceLogger perfLog = PerformanceLogger.getInstance();
    private List<String> matchDocuments = null;
    private List<ISolrDataField> fieldList = new ArrayList<ISolrDataField>();

    private static String schemaOrghttpContextFn  = "jsonldcontext_http.jsonld";
    private static String schemaOrgHttpsContextFn = "jsonldcontext_https.jsonld";
    private static String schemaOrgHttpListContextFn = "jsonldcontext_http_list.jsonld";
    private static String schemaOrghttpContextPath =
            Settings.getConfiguration().getString("dataone.indexing.schema.org.httpcontext.path",
                    "/etc/dataone/index/schema-org-contexts/" + schemaOrghttpContextFn);
    private static String schemaOrgHttpsContextPath =
            Settings.getConfiguration().getString("dataone.indexing.schema.org.httpscontext.path",
                    "/etc/dataone/index/schema-org-contexts/" + schemaOrgHttpsContextFn);
    private static String schemaOrgHttpListContextPath =
            Settings.getConfiguration().getString("dataone.indexing.schema.org.httpListcontext.path",
                    "/etc/dataone/index/schema-org-contexts/" + schemaOrgHttpListContextFn);
    
    private static final String HTTP_SCHEMAORG = "http://schema.org";
    private static final String HTTPS_SCHEMAORG = "https://schema.org";

    /**
     * Returns true if subprocessor should be run against object
     * 
     * @param formatId the the document to be processed
     * @return true if this processor can parse the formatId
     */
    public boolean canProcess(String formatId) {
        return matchDocuments.contains(formatId);
    }

    /**
     * Get the list of format ids which the subprocessor can process
     * @return the list of format ids which the subprocessor can process
     */
    public List<String> getMatchDocuments() {
        return matchDocuments;
    }

    /**
     * Set the list of format ids which the subprocessor can process
     * @param matchDocuments the format ids will be set
     */
    public void setMatchDocuments(List<String> matchDocuments) {
        this.matchDocuments = matchDocuments;
    }

    /**
     * Get the list of the Solr field names the subprocessor can handle
     * @return the list of Solr field names
     */
    public List<ISolrDataField> getFieldList() {
        return fieldList;
    }

    /**
     * Set the list of the Solr field names which the subprocessor can handle
     * @param fieldList  the list of Solr field names which the subprocessor can handle
     */
    public void setFieldList(List<ISolrDataField> fieldList) {
        this.fieldList = fieldList;
    }

    @Override
    public Map<String, SolrDoc> processDocument(String identifier, Map<String, SolrDoc> docs,
            InputStream is) throws Exception {

        JsonLdOptions options;
        DocumentLoader dl;
        DocumentLoader dlAll;
        Map ctx;

        /**
         * Load the schema.org context files.
         * First check the DataONE configuration settings for the file paths, either a set value or
         * default config value will be checked. If those files don't exist, use config files from
         * the d1_cn_index_processor jar file.
         */
        File schemaOrghttp = new File(schemaOrghttpContextPath);
        File schemaOrghttps = new File(schemaOrgHttpsContextPath);
        File schemaOrghttpList = new File(schemaOrgHttpListContextPath);

        FileInputStream fis = null;
        InputStream resourceIS = null;
        String httpContextStr;
        String httpListContextStr;
        String httpsContextStr;
        if(schemaOrghttp.exists()) {
            log.info("reading schema files from the local file system " + schemaOrghttp.getCanonicalPath());
            try {
                fis = new FileInputStream(schemaOrghttp);
                httpContextStr = IOUtils.toString(fis, "UTF-8");
            } finally {
                fis.close();
            }
        } else {
            log.info("reading schema files from the jar file " + schemaOrghttpContextFn);
            try {
                resourceIS = this.getClass().getResourceAsStream("/contexts/" + schemaOrghttpContextFn);
                httpContextStr = IOUtils.toString(resourceIS, "UTF-8");
            } finally {
                resourceIS.close();
            }
        }

        if(schemaOrghttps.exists()) {
            log.info("reading schema files from the local file system " + schemaOrghttps.getCanonicalPath());
            try {
                fis = new FileInputStream(schemaOrghttps);
                httpsContextStr = IOUtils.toString(fis, "UTF-8");
            } finally {
                fis.close();
            }
        } else {
            log.info("reading schema files from the jar file " + schemaOrgHttpsContextFn);
            try {
                resourceIS = this.getClass().getResourceAsStream("/contexts/" + schemaOrgHttpsContextFn);
                httpsContextStr = IOUtils.toString(resourceIS, "UTF-8");
            } finally {
                resourceIS.close();
            }
        }

        if(schemaOrghttpList.exists()) {
            log.info("reading schema files from the local file system " + schemaOrghttpList.getCanonicalPath());
            try {
                fis = new FileInputStream(schemaOrghttpList);
                httpListContextStr = IOUtils.toString(fis, "UTF-8");
            } finally {
                fis.close();
            }
        } else {
            log.info("reading schema files from the jar file " + schemaOrgHttpListContextFn);
            try {
                resourceIS = this.getClass().getResourceAsStream("/contexts/" + schemaOrgHttpListContextFn);
                httpListContextStr = IOUtils.toString(resourceIS, "UTF-8");
            } finally {
                resourceIS.close();
            }
        }

        Object compactedJSONLD;
        Object object;
        try {
            object = JsonUtils.fromInputStream(is, "UTF-8");
        } finally {
            is.close();
        }

        // Perform any necessary pre-processing on the original JSONLD document before
        // indexing. The steps are:
        // - expand the input JSONLD document which expands all schema.org terms to IRIs
        // - compact the document to normalize it, so that a simple @context term is used
        // - expand the document again, forcing the expansion to http://schema.org, so only
        //   this namespace is present for all documents to be indexed.
        // Use document loader that maps to http://schema.org or https://schema.org
        dlAll = new DocumentLoader();
        dlAll.addInjectedDoc("http://schema.org",  httpContextStr);
        dlAll.addInjectedDoc("http://schema.org/", httpContextStr);
        dlAll.addInjectedDoc("http://schema.org/docs/jsonldcontext.jsonld", httpContextStr);
        dlAll.addInjectedDoc("https://schema.org",  httpsContextStr);
        dlAll.addInjectedDoc("https://schema.org/", httpsContextStr);
        dlAll.addInjectedDoc("https://schema.org/docs/jsonldcontext.jsonld", httpsContextStr);
        options = new JsonLdOptions();
        options.setDocumentLoader(dlAll);
        Object expandedJSONLD = JsonLdProcessor.expand(object, options);
        log.trace("JSON document after expand: ");
        log.trace(JsonUtils.toPrettyString(expandedJSONLD));
        if(isHttps((List) expandedJSONLD)) {
            log.debug("processing a JSONLD document containing an https://schema.org context");
            options = new JsonLdOptions();
            ctx = new HashMap();
            ctx.put("@context", "https://schema.org/");
            options.setDocumentLoader(dlAll);
            compactedJSONLD = JsonLdProcessor.compact(expandedJSONLD, ctx, options);
            log.trace("JSON document after compaction: ");
            log.trace(JsonUtils.toPrettyString(compactedJSONLD));
        } else {
            log.debug("processing a JSONLD document containing an http://schema.org context");
            options = new JsonLdOptions();
            //options.setDocumentLoader(dl);
            ctx = new HashMap();
            ctx.put("@context", "http://schema.org/");
            options.setDocumentLoader(dlAll);
            compactedJSONLD = JsonLdProcessor.compact(expandedJSONLD, ctx, options);
            log.trace("JSON document after compaction: ");
            log.trace(JsonUtils.toPrettyString(compactedJSONLD));
        }
        
        /**
         * Expand the document. Include a document loader that results in the http://schema.org 
         * context file to be used, for any schema.org context url that is in the compacted (input) object.
         * Note also that the jsonldcontext_l.jsonld context file also ensures that the JSONLD document
         * will be expanded such that creators are represented as lists, which is needed by the SPARQL
         * queries used for indexing indexing.
         */

        // Create a document loader where all context map to the http://schema.org context file,
        // so that we ensure that the expanded document contains http://schema.org
        dl = new DocumentLoader();
        dl.addInjectedDoc("http://schema.org",  httpListContextStr);
        dl.addInjectedDoc("http://schema.org/", httpListContextStr);
        dl.addInjectedDoc("https://schema.org", httpListContextStr);
        dl.addInjectedDoc("https://schema.org/", httpListContextStr);
        dl.addInjectedDoc("http://schema.org/docs/jsonldcontext.jsonld", httpListContextStr);
        dl.addInjectedDoc("https://schema.org/docs/jsonldcontext.jsonld", httpListContextStr);
        options = new JsonLdOptions();
        options.setDocumentLoader(dl);
        expandedJSONLD = JsonLdProcessor.expand(compactedJSONLD, options);

        String str = JsonUtils.toString(expandedJSONLD);
        log.trace("JSON document after expand: " + str);
        is = new ByteArrayInputStream(str.getBytes());

        SolrDoc metaDocument = docs.get(identifier);
        if (metaDocument == null) {
            metaDocument = new SolrDoc();
            docs.put(identifier, metaDocument);
        }
        long start = System.currentTimeMillis();
        Map<String, SolrDoc> mergedDocuments;
        Dataset dataset = TripleStoreService.getInstance().getDataset();
        try {
            perfLog.log("JsonLdSubprocessor.process gets a dataset from tripe store service ", System.currentTimeMillis() - start);
            long startOntModel = System.currentTimeMillis();
            Model model = ModelFactory.createDefaultModel() ;
            model.read(is, "", "JSON-LD");
            dataset.getDefaultModel().add(model);
            perfLog.log("JsonLdSubprocessor.process adds the model ", System.currentTimeMillis() - startOntModel);
            //Track timing of this process
            long startField = System.currentTimeMillis();
            
            //Process each field listed in the fieldList in this subprocessor
            for (ISolrDataField field : this.fieldList) {
                long filed = System.currentTimeMillis();
                String q = null;
                String separator = null;
                log.trace("Processing field: " + field.getName());
                
                //Process Sparql fields. The Solr field value is derived from a SPARQL query performed on the
                // JSON-LD document that has been serialized to RDF.
                if (field instanceof SparqlField) {
                    String fullValue = null;
                    Boolean concatValues = ((SparqlField) field).getConcatValues();
                    if (concatValues == null) concatValues = false;
                    separator = ((SparqlField) field).getSeparator();
                    if (separator == null) separator = " ";
                    //Get the Sparql query for this field
                    q = ((SparqlField) field).getQuery();
                    //Create a Query object
                    Query query = QueryFactory.create(q);
                    log.trace("Executing SPARQL query:\n" + query.toString());
                    //Execute the Sparql query
                    QueryExecution qexec = QueryExecutionFactory.create(query, dataset);
                    //Get the results of the query
                    ResultSet results = qexec.execSelect();
                    //Iterate over each query result and process it
                    while (results.hasNext()) {
                        //Create a SolrDoc for this query result
                        SolrDoc solrDoc = null;
                        QuerySolution solution = results.next();
                        log.trace(solution.toString());
                        //Get the index field name and value returned from the Sparql query
                        if (solution.contains(field.getName())) {
                            //Get the value for this field
                            String value = solution.get(field.getName()).toString();
                            if (((SparqlField) field).getConverter() != null) {
                                value = ((SparqlField) field).getConverter().convert(value);
                            }
                            if (concatValues) {
                                if (fullValue == null) {
                                    fullValue = value;
                                } else {
                                    fullValue = fullValue + separator + value;
                                }
                            } else {
                                //Create an index field for this field name and value
                                SolrElementField f = new SolrElementField(field.getName(), value);
                                log.trace("JsonLdSubprocessor.process process the field " + field.getName() + " with value " + value);
                                metaDocument.addField(f);
                            }
                        }
                    }
                    // Some result values are derived by concatenating the return values of multiple values from the document.
                    if (concatValues) {
                        //Create an index field for this field name and value
                        SolrElementField f = new SolrElementField(field.getName(), fullValue);
                        log.trace("JsonLdSubprocessor.process process the concat field " + field.getName() + " with value " + fullValue);
                        metaDocument.addField(f);
                    }
                } else if (field instanceof DerivedSolrField) {
                    //Get the Sparql query for this field
                    SolrDoc solrDoc = null;
                    List<SolrElementField> fields = ((DerivedSolrField) field).getFields(dataset);
                    for (SolrElementField sel: fields) {
                        log.trace("JsonLdSubprocessor.process processed the field " + sel.getName() + " with value " + sel.getValue());
                        metaDocument.addField(sel);
                    }
                }
                perfLog.log("JsonLdSubprocessor.process process the field " + field.getName(), System.currentTimeMillis() - filed);
            }
            perfLog.log("JsonLdSubprocessor.process() total take ", System.currentTimeMillis() - start);
        } finally {
            try {
                TripleStoreService.getInstance().destoryDataset(dataset);
            } catch (Exception e) {
                log.warn("A tdb directory can't be removed since "+e.getMessage(), e);
            }
        }
        return docs;
    }
    
    /**
     * Determine if the expanded jsonld object uses the schema of https://schema.org
     * @param expandedJsonld  the expanded Jsonld object
     * @return true if it uses https://schema.org; false if it uses http://schema.org
     */
    public boolean isHttps(List expandedJsonld) throws Exception {
        boolean https = false;
        Vector<String> schema = new Vector<String>();
        findSchemaDotOrg(expandedJsonld, schema);
        if (schema != null && schema.size() > 0 && schema.get(0).equals(HTTPS_SCHEMAORG)) {
            https = true;
        } else if (schema != null && schema.size() > 0 && schema.get(0).equals(HTTP_SCHEMAORG)) {
            https = false;
        } else {
            throw new Exception("The Processor cannot find the either prefix of https://schema.org or http://schema.org in the expanded json-ld object.");
        }
        return https;
    }
    
    /**
     * A recursive method to find the schema https://schema.org or http://schema.org
     * @param expandedJsonld  the expanded json-ld list
     * @param schema  the vector to store the schema value
     */
    private void findSchemaDotOrg(Object expandedJsonld, Vector<String> schema) {
        if (expandedJsonld instanceof List) {
            List list = (List) expandedJsonld;
            for (int i=0; i< list.size(); i++) {
                Object obj = list.get(i);
                if(obj instanceof Map) {
                    Map map = (Map) obj;
                    Set keys = map.keySet();
                    for (Object key : keys) {
                        log.debug("JsonLdSubProcess.findSchemaDotOrg - the key is " + key + " and value is " + map.get(key));
                        if (key instanceof String) {
                            if (((String)key).startsWith(HTTPS_SCHEMAORG)) {
                                schema.add(HTTPS_SCHEMAORG);
                                log.debug("JsonLdSubProcess.findSchemaDotOrg - after setting the schema" + schema);
                                return;
                            } else if (((String)key).startsWith(HTTP_SCHEMAORG)) {
                                schema.add(HTTP_SCHEMAORG);
                                log.debug("JsonLdSubProcess.findSchemaDotOrg - after setting theschema " + schema);
                                return;
                            }
                        }
                        //we can't find the schema on the key. So we need to look at the value if the value is a list or map
                        Object value = map.get(key);
                        if (value != null && ( value instanceof List || value instanceof Map)) {
                            findSchemaDotOrg(value, schema);
                        }
                    }
                }
            }
        } else if (expandedJsonld instanceof Map) {
            Map map = (Map) expandedJsonld;
            Set keys = map.keySet();
            for (Object key : keys) {
                log.debug("JsonLdSubProcess.findSchemaDotOrg - the key is " + key + " and value is " + map.get(key));
                if (key instanceof String) {
                    if (((String)key).startsWith(HTTPS_SCHEMAORG)) {
                        schema.add(HTTPS_SCHEMAORG);
                        log.debug("JsonLdSubProcess.findSchemaDotOrg - after setting the schema" + schema);
                        return;
                    } else if (((String)key).startsWith(HTTP_SCHEMAORG)) {
                        schema.add(HTTP_SCHEMAORG);
                        log.debug("JsonLdSubProcess.findSchemaDotOrg - after setting theschema " + schema);
                        return;
                    }
                }
                //we can't find the schema on the key. So we need to look at the value if the value is a list or map
                Object value = map.get(key);
                if (value != null && ( value instanceof List || value instanceof Map)) {
                    findSchemaDotOrg(value, schema);
                }
            }
        } 
        log.debug("JsonLdSubProcess.findSchemaDotOrg - end of findschema method , the schema is " + schema);
    }
    
    @Override
    public SolrDoc mergeWithIndexedDocument(SolrDoc indexDocument) throws IOException,
            EncoderException, XPathExpressionException {
        // just return the given document
        return indexDocument;
    }
}
