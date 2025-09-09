package com.example.logstats;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AggregationServiceTest {

    private final AggregationService svc = new AggregationService();

    @Test
    public void testAvgNoGroup() {
        List<Map<String, Object>> docs = Arrays.asList(
                Map.of("number", 10),
                Map.of("number", 20),
                Map.of("number", "not-a-number")
        );
        List<Map<String, Object>> res = svc.aggregate(docs, "AVG", "number", null);
        assertEquals(1, res.size());
        Object avg = res.get(0).get("avg");
        assertNotNull(avg);
        assertEquals(15.0, ((Number) avg).doubleValue(), 1e-9);
    }

    @Test
    public void testMaxGroup() {
        List<Map<String, Object>> docs = Arrays.asList(
                Map.of("name", "a", "number", 5),
                Map.of("name", "b", "number", 7),
                Map.of("name", "a", "number", 3),
                Map.of("name", "b", "number", 10)
        );
        List<Map<String, Object>> res = svc.aggregate(docs, "MAX", "number", List.of("name"));
        Map<String, Double> resultMap = new HashMap<>();
        for (Map<String, Object> r : res) {
            resultMap.put(String.valueOf(r.get("name")), r.get("max") == null ? null : ((Number) r.get("max")).doubleValue());
        }
        assertEquals(2, resultMap.size());
        assertEquals(5.0, resultMap.get("a"));
        assertEquals(10.0, resultMap.get("b"));
    }

    @Test
    public void testDcGroup() {
        List<Map<String, Object>> docs = Arrays.asList(
                Map.of("host", "h1", "name", "x"),
                Map.of("host", "h1", "name", "y"),
                Map.of("host", "h2", "name", "x"),
                Map.of("host", "h1", "name", "x")
        );
        List<Map<String, Object>> res = svc.aggregate(docs, "DC", "name", List.of("host"));
        Map<String, Integer> resultMap = new HashMap<>();
        for (Map<String, Object> r : res) {
            resultMap.put(String.valueOf(r.get("host")), (Integer) r.get("dc"));
        }
        assertEquals(2, resultMap.get("h1"));
        assertEquals(1, resultMap.get("h2"));
    }

    @Test
    public void testIgnoreNonNumericForAvg() {
        Map<String, Object> m1 = new HashMap<>(), m2 = new HashMap<>();
        m1.put("number", "a");
        m2.put("number", null);
        List<Map<String, Object>> docs = Arrays.asList(m1, m2);
        List<Map<String, Object>> res = svc.aggregate(docs, "AVG", "number", null);
        assertEquals(1, res.size());
        assertNull(res.get(0).get("avg"));
    }

    @Test
    public void testMultipleGroupFields() {
        List<Map<String, Object>> docs = Arrays.asList(
                Map.of("name", "n1", "host", "h1", "number", 1),
                Map.of("name", "n1", "host", "h2", "number", 2),
                Map.of("name", "n1", "host", "h1", "number", 3)
        );
        List<Map<String, Object>> res = svc.aggregate(docs, "AVG", "number", List.of("name", "host"));
        // Ожидаем две группы: (n1,h1) avg=(1+3)/2=2, (n1,h2) avg=2
        Map<String, Double> map = new HashMap<>();
        for (Map<String, Object> r : res) {
            String key = r.get("name") + "|" + r.get("host");
            map.put(key, ((Number) r.get("avg")).doubleValue());
        }
        assertEquals(2, map.size());
        assertEquals(2.0, map.get("n1|h1"));
        assertEquals(2.0, map.get("n1|h2"));
    }
}
