package org.dataone.cn.indexer.solrhttp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * User: Porter Date: 7/26/11 Time: 11:37 AM
 * <p/>
 * HTTP Services based on Apache httpcomponents. This class to handles various
 * solr functions including adding documents to index.
 */

public class HTTPService {

    private static final String CHAR_ENCODING = "UTF-8";

    final static String PARAM_START = "start";
    final static String PARAM_ROWS = "rows";
    final static String PARAM_INDENT = "indent";
    final static String VALUE_INDENT_ON = "on";
    final static String VALUE_INDENT_OFF = "off";
    final static String PARAM_QUERY = "q";
    private Log log = LogFactory.getLog(HTTPService.class);
    private HttpComponentsClientHttpRequestFactory httpRequestFactory;

    private String SOLR_SCHEMA_PATH;
    private String solrIndexUri;
    private List<String> validSolrFieldNames = new ArrayList<String>();

    public HTTPService(HttpComponentsClientHttpRequestFactory requestFactory) {
        httpRequestFactory = requestFactory;
    }

    /**
     * Posts document data to Solr indexer.
     * 
     * @param uri
     *            Solr index url example:
     *            http://localhost:8080/solr/update?commit=true
     * @param data
     *            documents to index
     * @param encoding
     *            use "UTF-8"
     * @throws IOException
     */

    public void sendUpdate(String uri, SolrElementAdd data, String encoding) throws IOException {
        InputStream inputStreamResponse = null;
        HttpPost post = null;
        HttpResponse response = null;
        try {
            post = new HttpPost(uri);
            post.setEntity(new OutputStreamHttpEntity(data, encoding));
            response = getHttpClient().execute(post);
            HttpEntity responseEntity = response.getEntity();
            inputStreamResponse = responseEntity.getContent();
            if (response.getStatusLine().getStatusCode() != 200) {
                writeError(null, data, inputStreamResponse, uri);
            }
            post.abort();
        } catch (Exception ex) {
            writeError(ex, data, inputStreamResponse, uri);
        }
    }

    public void sendUpdate(String uri, SolrElementAdd data) throws IOException {
        sendUpdate(uri, data, CHAR_ENCODING);
    }

    private void sendPost(String uri, String data, String encoding) throws IOException {
        InputStream inputStreamResponse = null;
        HttpPost post = null;
        HttpResponse response = null;
        try {
            post = new HttpPost(uri);
            ByteArrayEntity entity = new ByteArrayEntity(data.getBytes());
            entity.setContentEncoding(encoding);
            post.setEntity(entity);
            response = getHttpClient().execute(post);
            HttpEntity responseEntity = response.getEntity();
            inputStreamResponse = responseEntity.getContent();
            if (response.getStatusLine().getStatusCode() != 200) {
                writeError(null, data, inputStreamResponse, uri);
            }
            post.abort();
        } catch (Exception ex) {
            writeError(ex, data, inputStreamResponse, uri);
        }
    }

    public void sendSolrDelete(String pid) {
        // generate request to solr server to remove index record for task.pid
        OutputStream outputStream = new ByteArrayOutputStream();
        try {
            IOUtils.write("<?xml version=\"1.1\" encoding=\"utf-8\"?>\n", outputStream,
                    CHAR_ENCODING);
            IOUtils.write("<delete><id>" + pid + "</id></delete>", outputStream, CHAR_ENCODING);
            sendPost(getSolrIndexUri(), outputStream.toString(), CHAR_ENCODING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Borrowed from
     * http://www.docjar.com/html/api/org/apache/solr/client/solrj/
     * util/ClientUtils.java.html
     * 
     * @param s
     * @return
     */
    public static String escapeQueryChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // These characters are part of the query syntax and must be escaped
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
                    || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}'
                    || c == '~' || c == '*' || c == '?' || c == '|' || c == '&' || c == ';'
                    || Character.isWhitespace(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void writeError(Exception ex, SolrElementAdd data, InputStream inputStreamResonse,
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
            ByteArrayOutputStream baosResponse = new ByteArrayOutputStream();
            org.apache.commons.io.IOUtils.copy(inputStreamResonse, baosResponse);
            log.error(new String(baosResponse.toByteArray()));
            inputStreamResonse.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeError(Exception ex, String data, InputStream inputStreamResonse, String uri)
            throws IOException {

        try {
            if (ex != null) {
                log.error("Unable to write to stream", ex);
            }

            log.error("URL: " + uri);
            log.error("Post: ");
            log.error(data);
            log.error("\n\n\nResponse: \n");
            ByteArrayOutputStream baosResponse = new ByteArrayOutputStream();
            org.apache.commons.io.IOUtils.copy(inputStreamResonse, baosResponse);
            log.error(new String(baosResponse.toByteArray()));
            inputStreamResonse.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ?q=id%3Ac6a8c20f-3503-4ded-b395-98fcb0fdd78c+OR+f5aaac58-dee1-4254-8cc4-95c5626ab037+OR+f3229cfb-2c53-4aa0-8437-057c2a52f502&version=2.2

    /**
     * Return the SOLR records for the specified PIDs
     * 
     * @param uir
     * @param ids
     * @return
     * @throws IOException
     * @throws XPathExpressionException
     * @throws EncoderException
     */
    public List<SolrDoc> getDocuments(String uir, List<String> ids) throws IOException,
            XPathExpressionException, EncoderException {
        if (ids == null || ids.size() <= 0) {
            return null;

        }

        loadSolrSchemaFields();

        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(" OR ");
            }
            sb.append(SolrElementField.FIELD_ID + ":").append(escapeQueryChars(id));
        }

        List<NameValuePair> params = new ArrayList<NameValuePair>();

        params.add(new BasicNameValuePair(PARAM_QUERY, sb.toString()));
        params.add(new BasicNameValuePair(PARAM_START, "0"));
        params.add(new BasicNameValuePair(PARAM_ROWS, Integer.toString(ids.size())));
        params.add(new BasicNameValuePair(PARAM_INDENT, VALUE_INDENT_ON));
        String paramString = URLEncodedUtils.format(params, "UTF-8");

        String requestURI = uir + "?" + paramString;
        log.info("REQUEST URI= " + requestURI);
        HttpGet commandGet = new HttpGet(requestURI);

        HttpResponse response = getHttpClient().execute(commandGet);

        HttpEntity entity = response.getEntity();
        InputStream content = entity.getContent();
        Document document = null;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(content);
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        commandGet.abort();
        List<SolrDoc> docs = parseResults(document);
        return docs;
    }

    public SolrDoc retrieveDocumentFromSolrServer(String id, String solrQueryUri)
            throws XPathExpressionException, IOException, EncoderException {
        List<String> ids = new ArrayList<String>();
        ids.add(id);
        List<SolrDoc> indexedDocuments = getDocuments(solrQueryUri, ids);
        return indexedDocuments.get(0);
    }

    private List<SolrDoc> parseResults(Document document) throws XPathExpressionException {

        NodeList nodeList = (NodeList) XPathFactory.newInstance().newXPath()
                .evaluate("/response/result/doc", document, XPathConstants.NODESET);
        List<SolrDoc> docList = new ArrayList<SolrDoc>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element docElement = (Element) nodeList.item(i);
            docList.add(parseDoc(docElement));

        }

        return docList;
    }

    private SolrDoc parseDoc(Element docElement) {
        SolrDoc doc = new SolrDoc();
        doc.LoadFromElement(docElement, validSolrFieldNames);
        return doc;
    }

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

    public void setSolrIndexUri(String uri) {
        this.solrIndexUri = uri;
    }

    public String getSolrIndexUri() {
        return this.solrIndexUri;
    }

    public HttpClient getHttpClient() {
        return httpRequestFactory.getHttpClient();
    }
}