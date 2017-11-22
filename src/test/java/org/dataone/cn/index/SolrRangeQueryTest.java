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

package org.dataone.cn.index;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocumentList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.Resource;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class SolrRangeQueryTest extends DataONESolrJettyTestBase {

    private Resource peggym1304Sys;
    
    @Before
    public void setup() throws Exception
    {
        System.out.println("***** before delete *********************************");
        sendSolrDeleteAll();
        System.out.println("***** between **********************************");
        loadTestResource();
        System.out.println("***** after load *********************************");
    }

    @Test
    public void testSimpleRangeQuery() throws Exception {

        System.out.println("***** simpleRangeQuery ");
        Thread.sleep(2000L);
        SolrDocumentList sdl = findByField("westBoundCoord", "[\\-130 TO 0 ]");
        Assert.assertEquals(1, sdl.size());
    }

    @Test
    public void testTwoFieldRangeQuery() throws Exception {
        
        System.out.println("***** two field range query ");
        Thread.sleep(2000L);
        SolrDocumentList sdl = findByQueryString("westBoundCoord:[\\-130 TO 0] AND northBoundCoord:[0 TO 30]");
        Assert.assertEquals(1, sdl.size());
    }

    @Test
    public void testFourFieldRangeQuery() throws Exception {
        
        System.out.println("***** four field range query");
        Thread.sleep(2000L);
        SolrDocumentList sdl = findByQueryString("westBoundCoord:[\\-130 TO 0] " //
                + "AND southBoundCoord:[0 TO 30] " //
                + "AND eastBoundCoord:[\\-150 TO 20] " //
                + "AND northBoundCoord:[\\-10 TO 50]");
        Assert.assertEquals(1, sdl.size());
    }

    private void loadTestResource() throws Exception, SolrServerException {
        String pid = "peggym.130.4";
        peggym1304Sys = (Resource) context.getBean("peggym1304Sys");
        addEmlToSolrIndex(peggym1304Sys);
        assertPresentInSolrIndex(pid);
    }
}
