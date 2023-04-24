package com.bawi;

import com.ximpleware.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class VtdXmlParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(VtdXmlParser.class);

    public static class FieldXpathMappingEntry {
        String field;
        String xpath;
        Class<?> clazz;

        public FieldXpathMappingEntry(String field, String xpath, Class<?> clazz) {
            this.field = field;
            this.xpath = xpath;
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldXpathMappingEntry that = (FieldXpathMappingEntry) o;
            return Objects.equals(field, that.field) && Objects.equals(xpath, that.xpath) && Objects.equals(clazz, that.clazz);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, xpath, clazz);
        }

        @Override
        public String toString() {
            return "FieldXpathMappingEntry{" +
                    "field='" + field + '\'' +
                    ", xpath='" + xpath + '\'' +
                    ", clazz=" + clazz +
                    '}';
        }
    }

    public interface CustomFieldParser {
        Object parse(String field, String xpath, AutoPilot ap, VTDNav nav) throws VTDException;
    }

    private final Map<Class<?>, Function<String, ?>> typeTransformations = Map.of(
            String.class, s -> s,
            Integer.class, Integer::parseInt
    );

    private final List<FieldXpathMappingEntry> fieldXpathMappingEntries;

    public VtdXmlParser(List<FieldXpathMappingEntry> fieldXpathMappingEntries) {
        this.fieldXpathMappingEntries = fieldXpathMappingEntries;
    }

    public Map<String, Object> parseFile(String xmlFilePath) {
        VTDGen vtdGen = new VTDGen();
        vtdGen.parseFile(xmlFilePath, false);
        return parseVTDGen(vtdGen);
    }

    public Map<String, Object> parseXml(String xmlFilePath) {
        VTDGen vtdGen = new VTDGen();
        vtdGen.setDoc(xmlFilePath.getBytes());
        try {
            vtdGen.parse(false);
            return parseVTDGen(vtdGen);
        } catch (ParseException e) {
            LOGGER.error("Failed to parse", e);
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> parseVTDGen(VTDGen vtdGen) {
        VTDNav nav = vtdGen.getNav();

        Map<String, Object> result = new HashMap<>();

        fieldXpathMappingEntries.forEach(entry -> {
            AutoPilot ap = new AutoPilot(nav);
            try {
                if (CustomFieldParser.class.isAssignableFrom(entry.clazz)) {
                    ap.selectXPath(entry.xpath);
                    try {
                        CustomFieldParser fieldParser = (CustomFieldParser) entry.clazz.getDeclaredConstructor((Class<?>[]) null).newInstance();
                        Object v = fieldParser.parse(entry.field, entry.xpath, ap, nav);
                        result.put(entry.field, v);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                             NoSuchMethodException | VTDException e) {
                        e.printStackTrace();
                    }
                } else {
                    ap.selectXPath(entry.xpath);
                    String value = ap.evalXPathToString();
                    Function<String, ?> typeTransformationFunction = typeTransformations.get(entry.clazz);
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
