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

import java.util.List;

import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.w3c.dom.Document;

public class FullTextSolrField extends SolrField {

    public FullTextSolrField(String name, String xpath) {
        super(name, xpath);
    }

    @Override
    public List<SolrElementField> getFields(Document doc, String identifier) throws Exception {
        List<SolrElementField> fields = super.getFields(doc, identifier);
        if (fields.size() > 0) {
            SolrElementField field = fields.get(0);
            if (field != null) {
                field.setValue(field.getValue().concat(" " + identifier));
            }
        } else {
            fields.add(new SolrElementField(name, identifier));
        }
        return fields;
    }
}
