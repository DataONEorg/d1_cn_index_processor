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

import java.util.HashMap;

import org.dataone.cn.indexer.convert.SolrDateConverter;
import org.dataone.cn.indexer.parser.ScienceMetadataDocumentSubprocessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "test-context.xml" })
public class SolrFieldXPathEmlTest extends BaseSolrFieldXPathTest {

    @Autowired
    private Resource peggym1304Sys;
    @Autowired
    private Resource peggym1304Sci;

    @Autowired
    private ScienceMetadataDocumentSubprocessor eml210Subprocessor;

    private SolrDateConverter dateConverter = new SolrDateConverter();

    // solr/rule field name (from spring context fields) mapped to actual value
    // from xml documents (system and science metadata docs)
    private HashMap<String, String> eml210Expected = new HashMap<String, String>();

    @Before
    public void setUp() throws Exception {
        // science metadata
        eml210Expected
                .put("abstract",
                        "This metadata record fred, describes a 12-34 TT-12 long-term data document can't frank.  This is a test.  If this was not a lower, an abstract \"double\" or 'single' would be present in UPPER (parenthized) this location.");
        eml210Expected
                .put("keywords",
                        "SANParks, South Africa##Augrabies Falls National Park,South Africa##Census data#EARTH SCIENCE : Oceans : Ocean Temperature : Water Temperature");
        eml210Expected.put("title", "Augrabies falls National Park census data.");
        eml210Expected.put("project", "");
        eml210Expected.put("southBoundCoord", "26.0");
        eml210Expected.put("northBoundCoord", "26.0");
        eml210Expected.put("westBoundCoord", "-120.31121");
        eml210Expected.put("eastBoundCoord", "-120.31121");
        eml210Expected.put("site", "Agulhas falls national Park");
        eml210Expected.put("beginDate", dateConverter.convert("1998"));
        eml210Expected.put("endDate", dateConverter.convert("2004-02-13"));
        eml210Expected.put("pubDate", "");
        eml210Expected.put("author", "SANParks");
        eml210Expected.put("authorGivenName", "");
        eml210Expected.put("authorSurName", "SANParks");
        eml210Expected.put("authorLastName", "SANParks#Garcia#Freeman");
        eml210Expected.put("investigator", "SANParks#Garcia#Freeman");
        eml210Expected.put("contactOrganization", "SANParks#The Awesome Store");
        eml210Expected
                .put("genus",
                        "Antidorcas#Cercopithecus#Diceros#Equus#Giraffa#Oreotragus#Oryz#Papio#Taurotragus#Tragelaphus");
        eml210Expected
                .put("species",
                        "marsupialis#aethiops#bicornis#hartmannae#camelopardalis#oreotragus#gazella#hamadryas#oryx#strepsiceros");
        eml210Expected.put("kingdom", "");
        eml210Expected.put("order", "");
        eml210Expected.put("phylum", "");
        eml210Expected.put("family", "");
        eml210Expected.put("class", "");
        eml210Expected
                .put("scientificName",
                        "Antidorcas marsupialis#Cercopithecus aethiops#Diceros bicornis#Equus hartmannae#Giraffa camelopardalis#Oreotragus oreotragus#Oryz gazella#Papio hamadryas#Taurotragus oryx#Tragelaphus strepsiceros");
        eml210Expected.put("origin", "SANParks Freddy Garcia#Gordon Freeman#The Awesome Store");
        eml210Expected
                .put("attributeName",
                        "ID#Lat S#Long E#Date#Stratum#Transect#Species#LatS#LongE#Total#Juvenile#L/R#Species#Stratum#Date#SumOfTotal#SumOfJuvenile#Species#Date#SumOfTotal#SumOfJuvenile");
        eml210Expected.put("attributeLabel", "");
        eml210Expected
                .put("attributeDescription",
                        "The ID#Lat S#Long E#The date#Stratum#Transect#The name of species#LatS#LongE#The total#Juvenile#L/R#The name of species#Stratum#The date#Sum of the total#Sum of juvenile#The name of species#The date#The sum of total#Sum of juvenile");
        eml210Expected
                .put("attributeUnit",
                        "dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless#dimensionless");
        eml210Expected
                .put("attribute",
                        "ID  The ID dimensionless#Lat S  Lat S dimensionless#Long E  Long E dimensionless#Date  The date#Stratum  Stratum dimensionless#Transect  Transect dimensionless#Species  The name of species#LatS  LatS dimensionless#LongE  LongE dimensionless#Total  The total dimensionless#Juvenile  Juvenile dimensionless#L/R  L/R dimensionless#Species  The name of species#Stratum  Stratum dimensionless#Date  The date#SumOfTotal  Sum of the total dimensionless#SumOfJuvenile  Sum of juvenile dimensionless#Species  The name of species#Date  The date#SumOfTotal  The sum of total dimensionless#SumOfJuvenile  Sum of juvenile dimensionless");

        eml210Expected.put("fileID", "https://" + hostname + "/cn/v1/resolve/peggym.130.4");

        // system metadata
        eml210Expected.put("id", "peggym.130.4");
        eml210Expected.put("formatId", "eml://ecoinformatics.org/eml-2.1.0");
        eml210Expected.put("formatType", "METADATA");
        eml210Expected.put("size", "36281");
        eml210Expected.put("checksum", "24426711d5385a9ffa583a13d07af2502884932f");
        eml210Expected.put("checksumAlgorithm", "SHA-1");
        eml210Expected.put("submitter", "dataone_integration_test_user");
        eml210Expected.put("rightsHolder", "dataone_integration_test_user");
        eml210Expected.put("replicationAllowed", "true");
        eml210Expected.put("numberReplicas", "");
        eml210Expected.put("preferredReplicationMN", "");
        eml210Expected.put("blockedReplicationMN", "");
        eml210Expected.put("obsoletes", "peggym.130.3");
        eml210Expected.put("obsoletedBy", "peggym.130.5");
        eml210Expected.put("dateUploaded", dateConverter.convert("2011-08-31T15:59:50.071163"));
        eml210Expected.put("dateModified", dateConverter.convert("2011-08-31T15:59:50.072921"));
        eml210Expected.put("datasource", "test_documents");
        eml210Expected.put("authoritativeMN", "test_documents");
        eml210Expected.put("replicaMN", "");
        eml210Expected.put("replicaVerifiedDate", "");
        eml210Expected.put("readPermission", "public#dataone_test_user##dataone_public_user");
        eml210Expected.put("writePermission", "dataone_integration_test_user");
        eml210Expected.put("changePermission", "");
        eml210Expected.put("isPublic", "true");
        eml210Expected.put("dataUrl", "https://" + hostname + "/cn/v1/resolve/peggym.130.4");

        eml210Expected
                .put("text",
                        "Augrabies falls National Park census data.   SANParks    Garcia  Freddy   SANParks  Regional Ecologists   Private Bag x402 Skukuza, 1350 South Africa     Freeman  Gordon   SANParks  Regional Ecologists   Private Bag x402 Skukuza, 1350 South Africa    The Awesome Store  Regional Ecologists   Private Bag x402 Skukuza, 1350 South Africa    This metadata record fred, describes a 12-34 TT-12 long-term data document can't frank.  This is a test.  If this was not a lower, an abstract \"double\" or 'single' would be present in UPPER (parenthized) this location.   SANParks, South Africa  Augrabies Falls National Park,South Africa  Census data  EARTH SCIENCE : Oceans : Ocean Temperature : Water Temperature    Agulhas falls national Park   -120.311210  -120.311210  26.0  26.0       1998    2004-02-13       Genus  Antidorcas   Species  marsupialis  Hartmans Zebra     Genus  Cercopithecus   Species  aethiops  Vervet monkey     Genus  Diceros   Species  bicornis  Baboon     Genus  Equus   Species  hartmannae  Giraffe     Genus  Giraffa   Species  camelopardalis  Kudu     Genus  Oreotragus   Species  oreotragus  Gemsbok     Genus  Oryz   Species  gazella  Eland     Genus  Papio   Species  hamadryas     Genus  Taurotragus   Species  oryx  Black rhino     Genus  Tragelaphus   Species  strepsiceros  Klipspringer      1251095992100 peggym.130.4 ID Lat S Long E Date Stratum Transect Species LatS LongE Total Juvenile L/R SumOfTotal SumOfJuvenile The ID Lat S Long E The date Stratum Transect The name of species LatS LongE The total Juvenile L/R Sum of the total Sum of juvenile The sum of total dimensionless");
    }

    /**
     * Testing that the Xpath expressions used by XPathParser and associates are
     * 'mining' the expected data from the science and system metadata
     * documents.
     * 
     * @throws Exception
     */
    @Test
    public void testEml210ScienceMetadataFields() throws Exception {
        testXPathParsing(eml210Subprocessor, peggym1304Sys, peggym1304Sci, eml210Expected,
                "peggym.130.4");
    }
}
