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

        AutoPilot ap = new AutoPilot();
        ap.selectXPath("staff[1]"); // selectXPath is heavy operation, reuse it with resetXPath
        ap.bind(nav);
        while (ap.evalXPath() > 0) {
            extractValueForXpath(nav, "@id");
            extract("employee", nav, "@name");
            extract("salary", nav, "basic");
            extract("address", nav, "country");
            extract("address", nav, "country/@code");
            extractValueForXpath(nav, "description");
        }
        ap.resetXPath();
    }

    private static void extract(String elementXpath, VTDNav nav, String attributeXpath) throws XPathParseException, XPathEvalException, NavException {
        AutoPilot ap = new AutoPilot();
        ap.selectXPath(elementXpath);
        ap.bind(nav);
        while (ap.evalXPath() > 0) {
            extractValueForXpath(nav, attributeXpath);
        }
        ap.resetXPath();
    }

    private static void extractValueForXpath(VTDNav nav, String xpath) {
        String result = null;
        AutoPilot ap = new AutoPilot();
        try {
            ap.selectXPath(xpath);
            ap.bind(nav);

            if (xpath.startsWith("@") || xpath.contains("/@")) {
                int i;
                while ((i = ap.evalXPath()) > 0) {
                    String attrName = nav.toString(i);
                    int attrIdx = nav.getAttrVal(attrName);
                    if (attrIdx != -1) {
                        result = nav.toString(attrIdx);
                    }
                }
            } else {
                while (ap.evalXPath() != -1) {
                    long attrPosition = nav.getContentFragment();
                    if (attrPosition != -1) {
                        int textTokenIdx = nav.getText();
                        result = nav.toString(textTokenIdx);
                    }
                }
            }
        } catch (VTDException e) {
            LOGGER.error("Failed to extract value for xpath " + xpath, e);
        } finally {
            ap.resetXPath();
        }

        LOGGER.info("{} => {}", xpath, result);
    }
}