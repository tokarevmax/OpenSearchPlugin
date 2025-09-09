package com.example.logstats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.Map;

public class JsonReader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<Map<String, Object>> readArray(File file) throws Exception {
        return MAPPER.readValue(file, new TypeReference<>(){});
    }
}
