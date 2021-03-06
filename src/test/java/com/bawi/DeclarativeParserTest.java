package com.bawi;

import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XPathParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DeclarativeParserTest {

    static class MappingEntry{
        String field;
        String xpath;
        Class<?> clazz;
        public MappingEntry(String field, String xpath, Class<?> clazz) {
            this.field = field;
            this.xpath = xpath;
            this.clazz = clazz;
        }
    }

    interface Parser {
        Object parse(String field, String xpath, AutoPilot ap, VTDNav nav);
    }

    static class StringLengthParser implements Parser {

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

    @Test
    public void shouldParseXml() {
        // given
        List<MappingEntry> mapping = List.of(
                new MappingEntry("college_first_staff_dep_name", "staff[1]/@dep_name", String.class),
                new MappingEntry("college_id", "@id", Integer.class),
                new MappingEntry("college_first_staff_dep_name_length", "staff[1]/@dep_name", StringLengthParser.class)
        );

        String xmlFilePath = "src/test/resources/college.xml";
        /*
        <college id="123">
            <staff id="101" dep_name="Admin">
         */

        // when
        Map<String, Object> result = parse(xmlFilePath, mapping);

        // then
        Assertions.assertEquals(123, result.get("college_id"));
        Assertions.assertEquals("Admin", result.get("college_first_staff_dep_name"));
        Assertions.assertEquals(5, result.get("college_first_staff_dep_name_length"));
    }

    private Map<String, Object> parse(String xmlFilePath, List<MappingEntry> mapping) {
        Map<Class<?>, Function<String, ?>> typeTransformation = Map.of(
                String.class, s -> s,
                Integer.class, Integer::parseInt
        );

        VTDGen vtdGen = new VTDGen();
        vtdGen.parseFile(xmlFilePath, false);
        VTDNav nav = vtdGen.getNav();

        Map<String, Object> result = new HashMap<>();

        mapping.forEach(entry -> {
            AutoPilot ap = new AutoPilot(nav);
            try {
                if (Parser.class.isAssignableFrom(entry.clazz)) {
                    try {
                        Parser parser = (Parser) entry.clazz.getDeclaredConstructor((Class<?>[]) null).newInstance();
                        Object v = parser.parse(entry.field, entry.xpath, ap, nav);
                        result.put(entry.field, v);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                } else {
                    ap.selectXPath(entry.xpath);
                    String value = ap.evalXPathToString();
                    Function<String, ?> typeTransformationFunction = typeTransformation.get(entry.clazz);
                    Object transformed = typeTransformationFunction.apply(value);
                    result.put(entry.field, transformed);
                }
            } catch (XPathParseException e) {
                e.printStackTrace();
            }
        });

        return result;
    }
}
