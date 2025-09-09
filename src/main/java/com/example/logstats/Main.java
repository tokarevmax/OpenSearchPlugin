package com.example.logstats;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class Main implements Callable<Integer> {

    // Агрегационная функция
    @Option(names = {"-a", "--agg"}, required = true)
    private String agg;

    //Название поля для выполнения над ней агрегационной функции
    @Option(names = {"-f", "--field"}, required = true)
    private String field;

    // Указанные группы
    @Option(names = {"-g", "--group"}, split = ",")
    private List<String> groups;

    // Путь к файлу json
    @Option(names = {"-d", "--data"}, required = true)
    private Path dataPath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        List<Map<String, Object>> docs = JsonReader.readArray(dataPath.toFile());
        AggregationService svc = new AggregationService();
        List<Map<String, Object>> result = svc.aggregate(docs, agg, field, groups);
        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        return 0;
    }
}
