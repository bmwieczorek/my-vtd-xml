package com.bawi;

import com.bawi.VtdXmlParser.Entry;
import com.bawi.parser.StringLengthParser;
import com.bawi.parser.SumValuesParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class DeclarativeParserTest {

    private final List<Entry> mapping = List.of(
            new Entry("college_id", "@id", Integer.class),
            new Entry("college_description", "description"),
            new Entry("staff", "staff[1]", null,
                    List.of(
                            new Entry("employee_names", "employee/@name"),
                            new Entry("employees", "employee",  List.of(
                                    new Entry("id", "@id"),
                                    new Entry("name", "@name"))),
                            new Entry("description", "description"),
                            new Entry("id", "@id", Integer.class),
                            new Entry("departament", "@dep_name"),
                            new Entry("basic_salary", "salary/basic", Integer.class),
                            new Entry("salary", "salary",
                                    List.of(new Entry("basic", "basic", Integer.class))),
                            new Entry("address", "address", null,
                                    List.of(new Entry("country_name", "country"),
                                            new Entry("country_code", "country/@code"))
            ))),
            new Entry("college_first_staff_dep_name", "staff[1]/@dep_name"),
            new Entry("college_first_staff_dep_name_length", "staff[1]/@dep_name", StringLengthParser.class),
            new Entry("staff_basic_salary_sum", "staff/salary/basic", SumValuesParser.class),
            new Entry("staff_id_attr_sum", "staff/@id", SumValuesParser.class)
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
    public void shouldParseXml() {
        // given
        String xml = "" +
                "<college id=\"123\">\n" +
                "    <description>US college</description>\n" +
                "    <staff id=\"101\" dep_name=\"Admin\">\n" +
                "        <description>Admin Admin</description>\n" +
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
