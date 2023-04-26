package com.bawi;

import com.ximpleware.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

public class VtdXmlParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(VtdXmlParser.class);

    public static class Entry implements Serializable {
        String field;
        String xpath;
        Class<?> clazz;
        List<Entry> children;

        public Entry(String field, String xpath, Class<?> clazz, List<Entry> children) {
            this.field = field;
            this.xpath = xpath;
            this.clazz = clazz;
            this.children = children;
        }

        public Entry(String field, String xpath, List<Entry> children) {
            this(field, xpath, null, children);
        }

        public Entry(String field, String xpath, Class<?> clazz) {
            this(field, xpath, clazz, Collections.emptyList());
        }

        public Entry(String field, String xpath) {
            this(field, xpath, String.class, Collections.emptyList());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry that = (Entry) o;
            return Objects.equals(field, that.field) && Objects.equals(xpath, that.xpath) && Objects.equals(clazz, that.clazz) && Objects.equals(children, that.children);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, xpath, clazz, children);
        }

        @Override
        public String toString() {
            return "E{f='" + field + '\'' + ",x='" + xpath + '\'' + ",cl=" + clazz + ",ch=" + children + '}';
        }
    }

    public interface CustomFieldParser {
        Object parse(String field, String xpath, AutoPilot ap, VTDNav nav) throws VTDException;
    }

    private final Map<Class<?>, Function<String, ?>> typeTransformations = Map.of(
            String.class, s -> s,
            Integer.class, Integer::parseInt
    );

    private final List<Entry> mappingEntries;

    public VtdXmlParser(List<Entry> mappingEntries) {
        this.mappingEntries = mappingEntries;
    }

    public Map<String, Object> parseFile(String xmlFilePath) {
        VTDGen vtdGen = new VTDGen();
        vtdGen.parseFile(xmlFilePath, false);
        VTDNav nav = vtdGen.getNav();
        return parseVTDGen(nav, mappingEntries);
    }

    public Map<String, Object> parseXml(String xmlFilePath) {
        VTDGen vtdGen = new VTDGen();
        vtdGen.setDoc(xmlFilePath.getBytes());
        try {
            vtdGen.parse(false);
            VTDNav nav = vtdGen.getNav();
            return parseVTDGen(nav, mappingEntries);
        } catch (ParseException e) {
            LOGGER.error("Failed to parse", e);
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> parseVTDGen(VTDNav nav, List<Entry> mappingEntries) {
        Map<String, Object> result = new HashMap<>();
        mappingEntries.forEach(entry -> {
            try {
                if (entry.children.size() > 0) {
                    Map<String, Object> recordAsMap = processRecord(nav, entry);
                    result.put(entry.field, recordAsMap);
                } else if (entry.clazz != null && CustomFieldParser.class.isAssignableFrom(entry.clazz)) {
                    Object value = parseField(nav, entry);
                    result.put(entry.field, value);
                } else {
                    String value = extractValue(nav, entry);
                    Function<String, ?> typeTransformationFunction = typeTransformations.get(entry.clazz);
                    Object transformed = typeTransformationFunction.apply(value);
                    result.put(entry.field, transformed);
                }
            } catch (XPathParseException e) {
                LOGGER.error("Failed to parse value for entry " + entry , e);
            }
        });
        return result;
    }

    private Map<String, Object> processRecord(VTDNav nav, Entry entry) throws XPathParseException {
        Map<String, Object> result = new HashMap<>();
        AutoPilot ap = new AutoPilot();
        ap.selectXPath(entry.xpath);
        ap.bind(nav);
        try {
            int cnt = 0;
            while (ap.evalXPath() > 0) { // requires a while loop, not if statement
                List<Entry> children = entry.children;
                result = parseVTDGen(nav, children);
                cnt++;
            }
            if (cnt > 1) LOGGER.error("More than one value for entry " + entry);
        } catch (VTDException e) {
            LOGGER.error("Failed to process record for entry " + entry , e);
        }
        finally {
            ap.resetXPath();
        }
        return result;
    }

    private static Object parseField(VTDNav nav, Entry entry) throws XPathParseException {
        AutoPilot ap = new AutoPilot(nav);
        ap.selectXPath(entry.xpath);
        ap.bind(nav);
        try {
            CustomFieldParser fieldParser = (CustomFieldParser) entry.clazz.getDeclaredConstructor((Class<?>[]) null).newInstance();
            return fieldParser.parse(entry.field, entry.xpath, ap, nav);
        } catch (Exception e) {
            LOGGER.error("Failed to parse value for entry " + entry , e);
        }
        finally {
            ap.resetXPath();
        }
        return null;
    }

    private static String extractValue(VTDNav nav, Entry entry) {
        String xpath = entry.xpath;
        String result = null;
        AutoPilot ap = new AutoPilot();
        try {
            ap.selectXPath(xpath);
            ap.bind(nav);

            if (xpath.startsWith("@") || xpath.contains("/@")) {
                int i, cnt = 0;
                while((i = ap.evalXPath()) > 0) { // requires a while loop, not if statement
                    String attrName = nav.toString(i);
                    int attrIdx = nav.getAttrVal(attrName);
                    if (attrIdx != -1) {
                        result = nav.toString(attrIdx);
                    }
                    cnt++;
                }
                if (cnt > 1) LOGGER.error("More than one value for entry " + entry);
            } else {
                int cnt = 0;
                while (ap.evalXPath() != -1) { // requires a while loop, not if statement
                    long attrPosition = nav.getContentFragment();
                    if (attrPosition != -1) {
                        int textTokenIdx = nav.getText();
                        result = nav.toString(textTokenIdx);
                    }
                    cnt++;
                }
                if (cnt > 1) LOGGER.error("More than one value for entry " + entry);
            }
        }
        catch (VTDException e) {
            LOGGER.error("Failed to extract value for entry " + entry , e);
        }
        finally {
            ap.resetXPath();
        }

        LOGGER.info("{} => {}", xpath, result);
        return result;
    }

}
