package com.example.logstats;

import java.util.*;

/**
 * Основной сервис агрегаций. Принимает список документов, тип агрегации,
 * имя поля и список полей группировки, а возвращает List<Map<String,Object>>
 * — массив объектов, готовых для сериализации в JSON.
 */
public class AggregationService {

    public List<Map<String, Object>> aggregate(List<Map<String, Object>> docs,
                                               String aggType,
                                               String field,
                                               List<String> groups) {

        // Чекаем, что аргумент правильный
        if (aggType == null) {
            throw new IllegalArgumentException("aggType is required");
        }
        String argument = aggType.trim().toUpperCase();
        if (!Arrays.asList("AVG", "MAX", "DC").contains(argument)) {
            throw new IllegalArgumentException("Unsupported aggregation: " + argument);
        }

        // Если не указана группа, то будем считать, что одна. Иначе добавляем все группы в список
        List<String> groupFields = groups == null ? Collections.emptyList() : new ArrayList<>();
        if (groups != null) {
            for (String g : groups) {
                if (g != null && !g.trim().isEmpty()) {
                    groupFields.add(g.trim());
                }
            }
        }

        Map<GroupKey, StatsAccumulator> map = new HashMap<>();

        // Одна группа
        if (groupFields.isEmpty()) {
            GroupKey groupKey = new GroupKey(Collections.emptyMap(), Collections.emptyList());
            StatsAccumulator accumulator = new StatsAccumulator(groupKey);
            for (Map<String, Object> doc : docs) {
                Object val = doc.get(field);
                accumulator.consume(argument, val);
            }
            return Collections.singletonList(accumulator.toResultMap(argument, field));
        } else { // Много групп
            for (Map<String, Object> doc : docs) {
                GroupKey groupKey = new GroupKey(doc, groupFields);
                StatsAccumulator accumulator = map.get(groupKey);
                if (accumulator == null) {
                    accumulator = new StatsAccumulator(groupKey);
                    map.put(groupKey, accumulator);
                }
                Object val = doc.get(field);
                accumulator.consume(argument, val);
            }
            List<Map<String, Object>> outResult = new ArrayList<>();
            for (StatsAccumulator acc : map.values()) {
                outResult.add(acc.toResultMap(argument, field));
            }
            return outResult;
        }
    }
}
