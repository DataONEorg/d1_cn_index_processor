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
package org.dataone.cn.indexer.convert;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "../../index/test-context.xml" })
public class MemberNodeServiceRegistrationTypeConverterTest {

    @Autowired
    private MemberNodeServiceRegistrationTypeConverter serviceTypesConverter;

    @Test
    public void testConvertWMS() {
        assertTrue("\"WMS\" should be converted to service type WMS.",
                "WMS".equals(serviceTypesConverter.convert("WMS")));
        assertTrue("\"Wms\" should be converted to service type WMS.",
                "WMS".equals(serviceTypesConverter.convert("Wms")));
        assertTrue("\"wms\" should be converted to service type WMS.",
                "WMS".equals(serviceTypesConverter.convert("wms")));

        assertFalse("\"W-M-S\" should not be converted to service type WMS.",
                "WMS".equals(serviceTypesConverter.convert("W-M-S")));
        assertFalse("\"OPeNDAP\" should not be converted to service type WMS.",
                "WMS".equals(serviceTypesConverter.convert("OPeNDAP")));
    }

    @Test
    public void testConvertOPeNDAP() {
        assertTrue("\"OPeNDAP\" should be converted to service type OPeNDAP.",
                "OPeNDAP".equals(serviceTypesConverter.convert("OPeNDAP")));
        assertTrue("\"OPENDAP\" should be converted to service type OPeNDAP.",
                "OPeNDAP".equals(serviceTypesConverter.convert("OPENDAP")));
        assertTrue("\"opendap\" should be converted to service type OPeNDAP.",
                "OPeNDAP".equals(serviceTypesConverter.convert("opendap")));

        assertTrue("\"ERDDAP OPeNDAP\" should be converted to service type OPeNDAP.",
                "OPeNDAP".equals(serviceTypesConverter.convert("ERDDAP OPeNDAP")));

        assertFalse("\"WMS\" should not be converted to service type OPeNDAP.",
                "OPeNDAP".equals(serviceTypesConverter.convert("WMS")));
        assertFalse("\"ERRDAP\" should not be converted to service type OPeNDAP.",
                "OPeNDAP".equals(serviceTypesConverter.convert("ERRDAP")));
    }

}
