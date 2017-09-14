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

package org.dataone.cn.indexer.solrhttp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.dataone.cn.indexer.D1IndexerSolrClient;
import org.dataone.service.util.DateTimeMarshaller;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Adaptation of HttpService class to use the SolrJClient.
 * The SolrClient query responses return a list of SolrDocuments which
 * are adapted to our SolrDoc class.  (All fields in SolrDoc get represented
 * as Strings, instead of Objects.
 * 
 * @author rnahf
 */

public class SolrJClient implements D1IndexerSolrClient {

    private static final String CHAR_ENCODING = "UTF-8";
    private static final String XML_CONTENT_TYPE = "text/xml";

    final static String PARAM_START = "start";
    final static String PARAM_ROWS = "rows";
    final static String PARAM_INDENT = "indent";
    final static String VALUE_INDENT_ON = "on";
    final static String VALUE_INDENT_OFF = "off";
    final static String PARAM_QUERY = "q";

    private static final String MAX_ROWS = "5000";
    
    private static final String DYNAMIC_FIELD_SUFFIX = "_sm";

    private static Logger log = Logger.getLogger(SolrJClient.class.getName());
    private HttpComponentsClientHttpRequestFactory httpRequestFactory;
    private HttpClient httpClient;
    private SolrClient solrClient;


    private String SOLR_SCHEMA_PATH;
    private String solrIndexUri;
    private List<String> validSolrFieldNames = new ArrayList<String>();

    public SolrJClient(HttpComponentsClientHttpRequestFactory requestFactory, String zkHost) {
        httpRequestFactory = requestFactory;
        httpClient = httpRequestFactory.getHttpClient();
        solrClient = new HttpSolrClient(zkHost);
        solrClient = new CloudSolrClient(zkHost, true, httpClient);
    }
    
    /**
     * A constructor to be used for testing.  It only uses an HttpSolrClient, not
     * the CloudSolrClient.  (In the future, we should set the client by configuration...
     * 
     * @param uri
     */
    public SolrJClient( String uri) {
        solrClient = new HttpSolrClient(uri);

    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#sendUpdate(java.lang.String, org.dataone.cn.indexer.solrhttp.SolrElementAdd, java.lang.String)
     */

    @Override
    public void sendUpdate(String uri, SolrElementAdd data, String encoding) throws IOException {
        this.sendUpdate(uri, data, encoding, XML_CONTENT_TYPE);
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#sendUpdate(java.lang.String, org.dataone.cn.indexer.solrhttp.SolrElementAdd)
     */
    @Override
    public void sendUpdate(String uri, SolrElementAdd data) throws IOException {
        sendUpdate(uri, data, CHAR_ENCODING, XML_CONTENT_TYPE);
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#sendUpdate(java.lang.String, org.dataone.cn.indexer.solrhttp.SolrElementAdd, java.lang.String, java.lang.String)
     */
    @Override
    public void sendUpdate(String uri, SolrElementAdd data, String encoding, String contentType)
            throws IOException {

        
        // convert SolrElementAdd to SolrJ's SolrInputDocument
        List<SolrInputDocument> updateDocs = new ArrayList<SolrInputDocument>();
        for (SolrDoc doc : data.getDocList()) {
            SolrInputDocument childDoc = new SolrInputDocument();
            for (SolrElementField sef : doc.getFieldList()) {
                childDoc.addField(sef.getName(), sef.getValue());
            }
            updateDocs.add(childDoc);
        }
        
        try {
            getSolrClient().add(updateDocs);
        } catch (SolrServerException e) {
            logError(e,data,e.getMessage(),"Using zkHost");
            throw new IOException("Unable to update solr (see cause)",e);
        }
    }


    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#sendSolrDelete(java.lang.String)
     */
    @Override
    public void sendSolrDelete(String pid) {
          
        try {
            getSolrClient().deleteById(pid);
        } catch (SolrServerException | IOException e1) {
            logError(e1,pid, e1.getMessage(),"zkHost");
            e1.printStackTrace();
        }
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#sendSolrDeletes(java.util.List)
     */
    @Override
    public void sendSolrDeletes(List<String> pids) {
             
        try {
            getSolrClient().deleteById(pids);
        } catch (SolrServerException | IOException e1) {
            String pidString = StringUtils.join(pids, ",");
            logError(e1,pidString, e1.getMessage(),"zkHost");
            e1.printStackTrace();
        }
     }
    
    private void logError(Exception ex, SolrElementAdd data, String messageResponse,
            String uri) throws IOException {

        try {
            if (ex != null) {
                log.error("Unable to write to stream", ex);
            }

            log.error("URL: " + uri);
            log.error("Post: ");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            data.serialize(baos, "UTF-8");
            log.error(new String(baos.toByteArray(), "UTF-8"));
            log.error("\n\n\nResponse: \n");

            log.error(messageResponse);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logError(Exception ex, String data, String messageResponse, String uri)
    {

        try {
            if (ex != null) {
                log.error("Unable to write to stream", ex);
            }

            log.error("URL: " + uri);
            log.error("Post: ");
            log.error(data);
            log.error("\n\n\nResponse: \n");
            
            log.error(messageResponse);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ?q=id%3Ac6a8c20f-3503-4ded-b395-98fcb0fdd78c+OR+f5aaac58-dee1-4254-8cc4-95c5626ab037+OR+f3229cfb-2c53-4aa0-8437-057c2a52f502&version=2.2

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getDocumentsById(java.lang.String, java.util.List)
     */
    @Override
    public List<SolrDoc> getDocumentsByD1Identifier(String uir, List<String> ids) throws IOException
    {
        List<SolrDoc> docs = getDocumentsByField(uir, ids, SolrElementField.FIELD_SERIES_ID, false);
        docs.addAll(getDocumentsByField(uir, ids, SolrElementField.FIELD_ID, false));
        return docs;
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getDocumentById(java.lang.String, java.lang.String)
     */
    @Override
    public List<SolrDoc> getDocumentBySolrId(String uir, String id) throws IOException
    {
        return getDocumentsByField(uir, Collections.singletonList(id), SolrElementField.FIELD_ID,
                false);
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getDocumentsByResourceMap(java.lang.String, java.lang.String)
     */
    @Override
    public List<SolrDoc> getDocumentsByResourceMap(String uir, String resourceMapId) throws IOException 
    {
        return getDocumentsByField(uir, Collections.singletonList(resourceMapId),
                SolrElementField.FIELD_RESOURCEMAP, true);
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getDocumentsByField(java.lang.String, java.util.List, java.lang.String, boolean)
     */
    @Override
    public List<SolrDoc> getDocumentsByField(String uir, List<String> fieldValues,
            String queryField, boolean maxRows) throws IOException 
            {

        if (fieldValues == null || fieldValues.size() <= 0) {
            return null;
        }

        loadSolrSchemaFields();

        List<SolrDoc> docs = new ArrayList<SolrDoc>();

        int rows = 0;
        String rowString = "";
        StringBuilder sb = new StringBuilder();
        for (String id : fieldValues) {
            if (sb.length() > 0) {
                sb.append(" OR ");
            }
            sb.append(queryField + ":").append(ClientUtils.escapeQueryChars(id));
            rows++;
            if (sb.length() > 5000) {
                if (maxRows) {
                    rowString = MAX_ROWS;
                } else {
                    rowString = Integer.toString(rows);
                }
                docs.addAll(doRequest(uir, sb, rowString));
                rows = 0;
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            if (maxRows) {
                rowString = MAX_ROWS;
            } else {
                rowString = Integer.toString(rows);
            }
            docs.addAll(doRequest(uir, sb, rowString));
        }
        return docs;
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getDocumentsByResourceMapFieldAndDocumentsField(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public List<SolrDoc> getDocumentsByResourceMapFieldAndDocumentsField(String uir,
            String resourceMapId, String documentsId) throws IOException {
        
        return getDocumentsByTwoFields(uir, SolrElementField.FIELD_RESOURCEMAP, resourceMapId,
                SolrElementField.FIELD_DOCUMENTS, documentsId);
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getDocumentsByResourceMapFieldAndIsDocumentedByField(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public List<SolrDoc> getDocumentsByResourceMapFieldAndIsDocumentedByField(String uir,
            String resourceMapId, String isDocumentedById) throws IOException
    {

        return getDocumentsByTwoFields(uir, SolrElementField.FIELD_RESOURCEMAP, resourceMapId,
                SolrElementField.FIELD_ISDOCUMENTEDBY, isDocumentedById);
    }

    private List<SolrDoc> getDocumentsByTwoFields(String uir, String field1, String field1Value,
            String field2, String field2Value) throws IOException {

        loadSolrSchemaFields();
        List<SolrDoc> docs = new ArrayList<SolrDoc>();
        StringBuilder sb = new StringBuilder();
        sb.append(field1 + ":").append(ClientUtils.escapeQueryChars(field1Value));
        sb.append(" AND ");
        sb.append(field2 + ":").append(ClientUtils.escapeQueryChars(field2Value));
        docs.addAll(doRequest(uir, sb, MAX_ROWS));
        return docs;
    }

    
    /**
     * executes and parses the query response.
     * Does not escape the query 
     * @param uir
     * @param sb
     * @param rows
     * @return
     * @throws IOException
     */
    protected List<SolrDoc> doRequest(String uir, StringBuilder sb, String rows) throws IOException 
    {
        String solrQ = sb.toString();// ClientUtils.escapeQueryChars(sb.toString());
        log.info(solrQ);
        SolrQuery sq = new SolrQuery();

        sq.setQuery(solrQ);
        sq.setParam(PARAM_INDENT, VALUE_INDENT_ON);
        if (rows != null) {
            sq.setParam(PARAM_START, "0");
            sq.setParam(PARAM_ROWS, ClientUtils.escapeQueryChars(rows)); // probably not needed, but who knows what will be passed in?
        }

        try {
            QueryResponse qr = getSolrClient().query(sq);

            return parseResponse(qr.getResults());
            
        } catch (SolrServerException e1) {
            throw new IOException(e1);
        }
    }
    
    protected List<SolrDoc> parseResponse(SolrDocumentList sdl) {
        loadSolrSchemaFields();
        Iterator<SolrDocument> it = sdl.iterator();
        int countDoc = 0;
        List<SolrDoc> docs = new ArrayList<SolrDoc>();
        while (it.hasNext()) {
            countDoc++;
            SolrDoc solrDoc = new SolrDoc();
            SolrDocument sd = it.next();
            if (sd.hasChildDocuments()) {
                log.info("ChildDocCount = " + sd.getChildDocumentCount());
            }
            int fieldCount = 0;
            StringBuffer sb = new StringBuffer();
            for (String fieldName : sd.getFieldNames()) {
                fieldCount++;
                sb.append(fieldName + ": ");
                
                // ensure valid field, or a dynamic field matching the pattern
                // NOTE: there are many kinds of dynamic fields, only handling one for now.
                if (this.validSolrFieldNames.contains(fieldName) || fieldName.endsWith(DYNAMIC_FIELD_SUFFIX)) {
                    sb.append("valid: ");
                    Object v = sd.getFieldValue(fieldName);

                    if (v instanceof Collection) {
                        sb.append("multi-valued: ");
                        for (Object vv : sd.getFieldValues(fieldName)) {
                            SolrElementField sef = new SolrElementField();
                            sef.setName(fieldName);
                            sef.setValue(valueConverter(vv));
                            solrDoc.addField(sef);
                            sb.append(valueConverter(vv) + "'");
                        }
                    } else {
                        SolrElementField sef = new SolrElementField();
                        sef.setName(fieldName);
                        sef.setValue(valueConverter(v));
                        solrDoc.addField(sef);
                        sb.append(valueConverter(v));
                    }  
                } else {
                    sb.append("NOT valid");
                }
                if (log.isTraceEnabled()) {
                    log.trace(sb.toString());
                }
            }
            log.info("FieldCount = " + fieldCount);
            docs.add(solrDoc);
        }
        log.info("DocCount = " + countDoc);
        return docs;
    }
    
    private String valueConverter(Object o) {
        try {
            return (String) o;
        } catch (ClassCastException e) {
            if (o instanceof Long) {
                return Long.toString((Long) o);
            } else if (o instanceof Boolean) {
                return Boolean.toString((Boolean) o);
            } else if (o instanceof Date) {
                return DateTimeMarshaller.serializeDateToUTC((Date) o);
            } else if (o instanceof Float) {
                return Float.toString((Float) o);
            }
            throw e;
        }
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#retrieveDocumentFromSolrServer(java.lang.String, java.lang.String)
     */
    @Override
    public SolrDoc retrieveDocumentFromSolrServer(String id, String solrQueryUri) throws IOException
    {
        List<String> ids = new ArrayList<String>();
        ids.add(id);
        List<SolrDoc> indexedDocuments = getDocumentsByD1Identifier(solrQueryUri, ids);
        if (indexedDocuments.size() > 0) {
            return indexedDocuments.get(0);
        } else {
            return null;
        }
    }

//    private List<SolrDoc> parseResults(Document document) throws XPathExpressionException {
//
//        NodeList nodeList = (NodeList) XPathFactory.newInstance().newXPath()
//                .evaluate("/response/result/doc", document, XPathConstants.NODESET);
//        List<SolrDoc> docList = new ArrayList<SolrDoc>();
//        for (int i = 0; i < nodeList.getLength(); i++) {
//            Element docElement = (Element) nodeList.item(i);
//            docList.add(parseDoc(docElement));
//
//        }
//
//        return docList;
//    }
//
//    private SolrDoc parseDoc(Element docElement) {
//        SolrDoc doc = new SolrDoc();
//        doc.loadFromElement(docElement, validSolrFieldNames);
//        return doc;
//    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#setSolrSchemaPath(java.lang.String)
     */
    @Override
    public void setSolrSchemaPath(String path) {
        SOLR_SCHEMA_PATH = path;
    }

    private void loadSolrSchemaFields() {
        if (SOLR_SCHEMA_PATH != null && validSolrFieldNames.isEmpty()) {
            Document doc = loadSolrSchemaDocument();
            NodeList nList = doc.getElementsByTagName("copyField");
            List<String> copyDestinationFields = new ArrayList<String>();
            for (int i = 0; i < nList.getLength(); i++) {
                Node node = nList.item(i);
                String destinationField = node.getAttributes().getNamedItem("dest").getNodeValue();
                copyDestinationFields.add(destinationField);
            }
            nList = doc.getElementsByTagName("field");
            List<String> fields = new ArrayList<String>();
            for (int i = 0; i < nList.getLength(); i++) {
                Node node = nList.item(i);
                String fieldName = node.getAttributes().getNamedItem("name").getNodeValue();
                fields.add(fieldName);
            }
            fields.removeAll(copyDestinationFields);
            validSolrFieldNames = fields;
            fields.remove("_version_");
        }
    }

    private Document loadSolrSchemaDocument() {

        Document doc = null;
        File schemaFile = new File(SOLR_SCHEMA_PATH);
        if (schemaFile != null) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(schemaFile);
            } catch (FileNotFoundException e) {
                log.error(e.getMessage(), e);
            }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = null;
            try {
                dBuilder = dbFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                log.error(e.getMessage(), e);
            }
            try {
                doc = dBuilder.parse(fis);
            } catch (SAXException e) {
                log.error(e.getMessage(), e);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return doc;
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#setSolrIndexUri(java.lang.String)
     */
    @Override
    public void setSolrIndexUri(String uri) {
        this.solrIndexUri = uri;
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getSolrIndexUri()
     */
    @Override
    public String getSolrIndexUri() {
        return this.solrIndexUri;
    }

    /* (non-Javadoc)
     * @see org.dataone.cn.indexer.solrhttp.D1IndexerSolrClient#getHttpClient()
     */
    @Override
    public HttpClient getHttpClient() {
        return this.httpClient;     }
    
    protected SolrClient getSolrClient() {
        return solrClient;

    }
}
