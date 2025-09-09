package com.example.logstats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//Накопитель статистики для одной группы, считает полностью все
public class StatsAccumulator {
    private final GroupKey groupKey;

    private double sum = 0.0;
    private long count = 0;
    private Double maxValue = null;
    private final Set<Object> distinct = new HashSet<>();

    public StatsAccumulator(GroupKey groupKey) {
        this.groupKey = groupKey;
    }

    public void consume(String aggType, Object value) {
        if ("AVG".equalsIgnoreCase(aggType) || "MAX".equalsIgnoreCase(aggType)) {
            if (value instanceof Number) {
                double d = ((Number) value).doubleValue();
                if ("AVG".equalsIgnoreCase(aggType)) {
                    sum += d;
                    count++;
                } else {
                    if (maxValue == null || d > maxValue) {
                        maxValue = d;
                    }
                }
            } // иначе нечисловое значение - скип
        } else if ("DC".equalsIgnoreCase(aggType)) {
            if (value != null) {
                distinct.add(value);
            }
        }
    }

    // Собираем финальную мапу
    public Map<String, Object> toResultMap(String aggType, String aggField) {
        HashMap<String, Object> outResult = new HashMap<>(groupKey.getGroupMap());
        if ("AVG".equalsIgnoreCase(aggType)) {
            if (count == 0) {
                outResult.put("avg", null);
            } else {
                outResult.put("avg", sum / count);
            }
        } else if ("MAX".equalsIgnoreCase(aggType)) {
            outResult.put("max", maxValue);
        } else if ("DC".equalsIgnoreCase(aggType)) {
            outResult.put("dc", distinct.size());
        } else {
            outResult.put("value", null);
        }
        return outResult;
    }

    public GroupKey getGroupKey() {
        return groupKey;
    }
}
