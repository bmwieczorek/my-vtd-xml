package com.bawi.parser.impl;

import com.bawi.VtdXmlParser;
import com.ximpleware.AutoPilot;
import com.ximpleware.VTDNav;
import com.ximpleware.XPathParseException;

public class StringLengthParser implements VtdXmlParser.CustomFieldParser {

    @Override
    public Object parse(String field, String xpath, AutoPilot ap, VTDNav nav) {
        try {
            ap.selectXPath(xpath);
        } catch (XPathParseException e) {
            e.printStackTrace();
        }
        String s = ap.evalXPathToString();
        return s.length();
    }
}
