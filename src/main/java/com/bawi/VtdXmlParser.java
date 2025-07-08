package com.bawi;

import com.bawi.parser.CustomFieldParser;
import com.ximpleware.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            return "E{f=" + field + ",x=" + xpath + ",cl=" + (clazz != null ? clazz.getSimpleName() : null) + ",ch=" + children + '}';
        }
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
                    List<Map<String, Object>> recordAsMapList = processRecord(nav, entry);
                    Object value = recordAsMapList.size() == 0 ? null : recordAsMapList.size() == 1 ? recordAsMapList.get(0) : recordAsMapList;
                    result.put(entry.field, value);
                } else if (entry.clazz != null && CustomFieldParser.class.isAssignableFrom(entry.clazz)) {
                    Object value = parseField(nav, entry);
                    result.put(entry.field, value);
                } else {
                    Function<String, ?> typeTransformationFunction = typeTransformations.get(entry.clazz);
                    List<String> values = extractValue(nav, entry);
                    List<?> transformedValues = values.stream().map(typeTransformationFunction).collect(Collectors.toList());
                    Object transformed = transformedValues.size() == 0 ? null : transformedValues.size() == 1 ? transformedValues.get(0) : transformedValues;
                    result.put(entry.field, transformed);
                }
            } catch (XPathParseException e) {
                LOGGER.error("Failed to parse value for entry " + entry , e);
            }
        });
        return result;
    }

    private List<Map<String, Object>> processRecord(VTDNav nav, Entry entry) throws XPathParseException {
        String xpath = entry.xpath;
        List<Map<String, Object>> results = new ArrayList<>();
        AutoPilot ap = new AutoPilot();
        ap.selectXPath(xpath);
        ap.bind(nav);
        try {
            while (ap.evalXPath() > 0) { // requires a while loop, not if statement
                List<Entry> children = entry.children;
                Map<String, Object> recordAsMap = parseVTDGen(nav, children);
//                LOGGER.info("Record: {} => {}", xpath, recordAsMap);
                results.add(recordAsMap);
            }
        } catch (VTDException e) {
            LOGGER.error("Failed to process record for entry " + entry , e);
        }
        finally {
            ap.resetXPath();
        }
//        LOGGER.info("Records: {} => {}", xpath, results);
        return results;
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

    private static List<String> extractValue(VTDNav nav, Entry entry) {
        String xpath = entry.xpath;
        List<String> results = new ArrayList<>();
        AutoPilot ap = new AutoPilot();
        try {
            ap.selectXPath(xpath);
            ap.bind(nav);

            if (xpath.startsWith("@") || xpath.contains("/@")) {
                int i;
                while((i = ap.evalXPath()) > 0) { // requires a while loop, not if statement
                    String attrName = nav.toString(i);
                    int attrIdx = nav.getAttrVal(attrName);
                    if (attrIdx != -1) {
                        String attrValue = nav.toString(attrIdx);
//                        LOGGER.info("AttrValue: {} => {}", xpath, attrValue);
                        results.add(attrValue);
                    }
                }
            } else {
                while (ap.evalXPath() != -1) { // requires a while loop, not if statement
                    long attrPosition = nav.getContentFragment();
                    if (attrPosition != -1) {
                        int textTokenIdx = nav.getText();
                        String text = nav.toString(textTokenIdx);
//                        LOGGER.info("Text: {} => {}", xpath, text);
                        results.add(text);
                    }
                }
            }
        }
        catch (VTDException e) {
            LOGGER.error("Failed to extract value for entry " + entry , e);
        }
        finally {
            ap.resetXPath();
        }

//        LOGGER.info("Values: {} => {}", xpath, results);
        return results;
    }

}
