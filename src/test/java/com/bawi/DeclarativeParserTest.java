package com.bawi;

import com.bawi.parser.impl.StringLengthParser;
import com.bawi.parser.impl.SumValuesParser;
import com.ximpleware.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class DeclarativeParserTest {

    private final List<VtdXmlParser.FieldXpathMappingEntry> mapping = List.of(
            new VtdXmlParser.FieldXpathMappingEntry("college_first_staff_dep_name", "staff[1]/@dep_name", String.class),
            new VtdXmlParser.FieldXpathMappingEntry("college_first_staff_dep_name_length", "staff[1]/@dep_name", StringLengthParser.class),
            new VtdXmlParser.FieldXpathMappingEntry("staff_basic_salary_sum", "staff/salary/basic", SumValuesParser.class),
            new VtdXmlParser.FieldXpathMappingEntry("staff_id_attr_sum", "staff/@id", SumValuesParser.class),
            new VtdXmlParser.FieldXpathMappingEntry("college_id", "@id", Integer.class)
    );
    private final VtdXmlParser vtdXmlParser = new VtdXmlParser(mapping);

    @Test
    public void shouldParseXmlFile() {
        // given
        String xmlFilePath = "src/test/resources/college.xml";

        // when
        Map<String, Object> result = vtdXmlParser.parseFile(xmlFilePath);

        // then
        Assertions.assertEquals(123, result.get("college_id"));
        Assertions.assertEquals("Admin", result.get("college_first_staff_dep_name"));
        Assertions.assertEquals(5, result.get("college_first_staff_dep_name_length"));
        Assertions.assertEquals(20000 + 25000 + 35000, result.get("staff_basic_salary_sum"));
        Assertions.assertEquals(101 + 102 + 103, result.get("staff_id_attr_sum"));
    }

    @Test
    public void shouldParseXml() throws ParseException {
        // given
        String xml = "<college id=\"123\">\n" +
                "    <staff id=\"101\" dep_name=\"Admin\">\n" +
                "        <employee id=\"101-01\" name=\"ashish\"/>\n" +
                "        <salary id=\"101-sal\">\n" +
                "            <basic>20000</basic>\n" +
                "        </salary>\n" +
                "    </staff>\n" +
                "</college>\n";

        // when
        Map<String, Object> result = vtdXmlParser.parseXml(xml);

        // then
        Assertions.assertEquals(123, result.get("college_id"));
        Assertions.assertEquals("Admin", result.get("college_first_staff_dep_name"));
        Assertions.assertEquals(5, result.get("college_first_staff_dep_name_length"));
        Assertions.assertEquals(20000, result.get("staff_basic_salary_sum"));
        Assertions.assertEquals(101, result.get("staff_id_attr_sum"));
    }

}
