package com.bawi;

import com.ximpleware.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VtdXmlXpathTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(VtdXmlXpathTest.class);

    @Test
    public void shouldParseXml() throws XPathParseException, NavException, XPathEvalException {
        VTDGen vtdGen = new VTDGen();
        vtdGen.parseFile("src/test/resources/college.xml", false);
        VTDNav nav = vtdGen.getNav();
        AutoPilot ap = new AutoPilot(nav);

        ap.selectXPath("/college/staff[2]/@dep_name");
        LOGGER.info("/college/staff[2]/@dep_name = " + ap.evalXPathToString());

        ap.selectXPath("/college/staff"); // selectXPath is heavy operation, reuse it with resetXPath
        while(ap.evalXPath() != -1) {
            int idx = nav.getAttrVal("id");
            String id = nav.toString(idx); // nav.toNormalizedString(idx);
            LOGGER.info("/college/staff/id = " + id);
        }

        // ap.selectXPath("/college/staff"); // selectXPath is heavy operation, reuse it with resetXPath instead calling select again
        ap.resetXPath(); // need to reset XPath otherwise it will not enter the evalXPath loop
        while(ap.evalXPath() != -1) {
            int idx = nav.getAttrVal("id");
            String id = nav.toString(idx);
            LOGGER.info("/college/staff/id = " + id);

            String dep_name = nav.toNormalizedString(nav.getAttrVal("dep_name"));
            LOGGER.info("/college/staff/dep_name = " + dep_name);
        }
    }

}