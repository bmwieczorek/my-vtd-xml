package com.bawi;

import com.ximpleware.extended.AutoPilotHuge;
import com.ximpleware.extended.VTDGenHuge;
import com.ximpleware.extended.VTDNavHuge;
import com.ximpleware.extended.XPathParseExceptionHuge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DeclarativeHugeParserTest {

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
        Object parse(String field, String xpath, AutoPilotHuge ap, VTDNavHuge nav);
    }

    static class StringLengthParser implements Parser {

        @Override
        public Object parse(String field, String xpath, AutoPilotHuge ap, VTDNavHuge nav) {
            try {
                ap.selectXPath(xpath);
            } catch (XPathParseExceptionHuge e) {
                e.printStackTrace();
            }
            String s = ap.evalXPathToString();
            return s.length();
        }
    }

    @Test
    public void shouldParseXml() throws IOException {
        // given
        List<MappingEntry> mapping = List.of(
                new MappingEntry("college_first_staff_dep_name", "staff[1]/@dep_name", String.class),
                new MappingEntry("college_id", "@id", Integer.class),
                new MappingEntry("college_first_staff_dep_name_length", "staff[1]/@dep_name", StringLengthParser.class)
        );



//        String xmlFilePath = "src/test/resources/college.xml";
        /*
        <college id="123">
            <staff id="101" dep_name="Admin">
         */

        // when
        Map<String, Object> result;
        Path path = new File("src/test/resources/college.xml").toPath();
        try (InputStream inputStream = Files.newInputStream(path)) {
            result = parse(inputStream, mapping);
        }

        // then
        Assertions.assertEquals(123, result.get("college_id"));
        Assertions.assertEquals("Admin", result.get("college_first_staff_dep_name"));
        Assertions.assertEquals(5, result.get("college_first_staff_dep_name_length"));
    }

    private Map<String, Object> parse(InputStream inputStream, List<MappingEntry> mapping) throws IOException {
        Map<Class<?>, Function<String, ?>> typeTransformation = Map.of(
                String.class, s -> s,
                Integer.class, Integer::parseInt
        );


        Path path2 = new File("target/college.xml.tmp").toPath();
        Files.copy(inputStream, path2, StandardCopyOption.REPLACE_EXISTING);

        VTDGenHuge vtdGen = new VTDGenHuge();
        vtdGen.parseFile(path2.toFile().getAbsolutePath(), false);
        VTDNavHuge nav = vtdGen.getNav();

        Map<String, Object> result = new HashMap<>();

        mapping.forEach(entry -> {
            AutoPilotHuge ap = new AutoPilotHuge(nav);
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
            } catch (XPathParseExceptionHuge e) {
                e.printStackTrace();
            }
        });

        return result;
    }
}
