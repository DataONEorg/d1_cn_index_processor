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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dataone.cn.indexer.parser.utility.DublinCoreSpatialBoxParsingUtility;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.w3c.dom.Document;

public class DublinCoreSpatialBoxBoundingCoordinatesSolrField extends SolrField implements
        ISolrField {

    private static Logger logger = Logger
            .getLogger(DublinCoreSpatialBoxBoundingCoordinatesSolrField.class.getName());

    private static final String INDEX_NORTH_PROPERTY = "northBoundCoord";
    private static final String INDEX_SOUTH_PROPERTY = "southBoundCoord";
    private static final String INDEX_EAST_PROPERTY = "eastBoundCoord";
    private static final String INDEX_WEST_PROPERTY = "westBoundCoord";

    private static DublinCoreSpatialBoxParsingUtility boxParsingUtility = new DublinCoreSpatialBoxParsingUtility();

    public DublinCoreSpatialBoxBoundingCoordinatesSolrField() {
    }

    public DublinCoreSpatialBoxBoundingCoordinatesSolrField(String xpath) {
        this.xpath = xpath;
    }

    public List<SolrElementField> getFields(Document doc, String identifier) throws Exception {
        return parseBox(doc);
    }

    private List<SolrElementField> parseBox(Document doc) {
        List<SolrElementField> fields = new ArrayList<SolrElementField>();

        String nodeValue = boxParsingUtility.extractNodeValue(doc, this.xPathExpression);

        setBoundingBoxCoordinate(nodeValue, fields,
                DublinCoreSpatialBoxParsingUtility.DC_BOX_NORTH_PROPERTY, INDEX_NORTH_PROPERTY);
        setBoundingBoxCoordinate(nodeValue, fields,
                DublinCoreSpatialBoxParsingUtility.DC_BOX_SOUTH_PROPERTY, INDEX_SOUTH_PROPERTY);
        setBoundingBoxCoordinate(nodeValue, fields,
                DublinCoreSpatialBoxParsingUtility.DC_BOX_EAST_PROPERTY, INDEX_EAST_PROPERTY);
        setBoundingBoxCoordinate(nodeValue, fields,
                DublinCoreSpatialBoxParsingUtility.DC_BOX_WEST_PROPERTY, INDEX_WEST_PROPERTY);

        return fields;
    }

    private void setBoundingBoxCoordinate(String nodeValue, List<SolrElementField> fields,
            String boxDirectionProperty, String indexDirectionProperty) {

        String directionValue = boxParsingUtility.extractDirectionalValue(nodeValue,
                boxDirectionProperty);
        if (directionValue != null && StringUtils.isNotEmpty(directionValue)) {
            if (this.converter != null) {
                directionValue = this.converter.convert(directionValue);
            }
            if (StringUtils.isNotEmpty(directionValue)) {
                fields.add(new SolrElementField(indexDirectionProperty, directionValue));
            }
        }
    }

}
