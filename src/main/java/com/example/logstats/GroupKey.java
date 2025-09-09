package com.example.logstats;

import java.util.*;

//Класс-ключ для группировки. Хранит список значений групповых полей
public class GroupKey {
    private final List<Object> values;
    private final Map<String, Object> groupMap;

    public GroupKey(Map<String, Object> doc, List<String> groupFields) {
        this.values = new ArrayList<>();
        for (String group : groupFields) {
            Object v = doc.get(group);
            values.add(v);
        }
        HashMap<String, Object> groupMap = new HashMap<>();
        for (int i = 0; i < groupFields.size(); i++) {
            groupMap.put(groupFields.get(i), values.get(i));
        }
        this.groupMap = groupMap;
    }

    public Map<String, Object> getGroupMap() {
        return this.groupMap;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GroupKey groupKey = (GroupKey) o;
        return Objects.equals(values, groupKey.values) && Objects.equals(groupMap, groupKey.groupMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, groupMap);
    }
}
