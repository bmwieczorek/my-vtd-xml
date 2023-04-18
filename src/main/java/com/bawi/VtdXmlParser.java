package com.bawi;

import com.ximpleware.*;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class VtdXmlParser {

    public static class FieldXpathMappingEntry {
        String field;
        String xpath;
        Class<?> clazz;

        public FieldXpathMappingEntry(String field, String xpath, Class<?> clazz) {
            this.field = field;
            this.xpath = xpath;
            this.clazz = clazz;
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

    public Map<String, Object> parseXml(String xmlFilePath) throws ParseException {
        VTDGen vtdGen = new VTDGen();
        vtdGen.setDoc(xmlFilePath.getBytes());
        vtdGen.parse(false);
        return parseVTDGen(vtdGen);
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
