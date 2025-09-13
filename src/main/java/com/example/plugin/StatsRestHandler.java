package com.example.plugin;

import org.opensearch.action.admin.indices.mapping.get.*;
import org.opensearch.rest.*;
import org.opensearch.action.search.*;
import org.opensearch.search.aggregations.*;
import org.opensearch.search.aggregations.bucket.composite.*;
import org.opensearch.core.xcontent.*;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.core.action.ActionListener;
import org.opensearch.cluster.metadata.MappingMetadata;

import java.io.IOException;
import java.util.*;

/**
 * Запрос на /_plugins/opensearch-stats/_stats
 * Пример запроса:
 * {
 *   "index": "logs-*",
 *   "agg": "AVG" | "MAX" | "DC",
 *   "field": "number",
 *   "group": ["name","host"] // opt
 * }
 * Перед этим запросом надо послать на плагин данные с помощью @bulk, например
 * $bulk = @'
 * { "index": { "_index": "logs-test", "_id": "1" } }
 * { "name":"alpha","host":"h1","number":10 }
 * { "index": { "_index": "logs-test", "_id": "2" } }
 * { "name":"alpha","host":"h1","number":20 }
 * { "index": { "_index": "logs-test", "_id": "3" } }
 * { "name":"beta","host":"h2","number":5 }
 * { "index": { "_index": "logs-test", "_id": "4" } }
 * { "name":"beta","host":"h3","number":"not-a-number" }
 * { "index": { "_index": "logs-test", "_id": "5" } }
 * { "name":"gamma","host":"h2" }
 * '@
 * И посылал его на сервер с помощью команды pwsl:
 * Invoke-RestMethod -Method Post -Uri "http://localhost:9200/_bulk?refresh=true" `
 *     -ContentType 'application/x-ndjson' -Body $bulk
 */
public class StatsRestHandler extends BaseRestHandler {

    private static final String PATH = "/_plugins/opensearch-stats/_stats";

    @Override
    public String getName() {
        return "opensearch_stats_rest_handler";
    }

    @Override
    public List<Route> routes() {
        return Collections.singletonList(new Route(RestRequest.Method.POST, PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        try (XContentParser parser = request.contentParser()) {
            Map<String, Object> body = parser.map();
            // берем из парсера поля и валидируем их
            final String index = (String) body.getOrDefault("index", "_all");
            final String agg = (body.get("agg") == null) ? null : body.get("agg").toString().toUpperCase(Locale.ROOT);
            Object fieldObj = body.get("field");
            if (agg == null || fieldObj == null) {
                throw new IllegalArgumentException("Fields 'agg' and 'field' are required");
            }
            final String requestedField = fieldObj.toString();

            final List<String> requestedGroupFields; // если много групп
            Object g = body.get("group");
            if (g instanceof Collection) {
                List<String> groupFields = new ArrayList<>();
                for (Object o : (Collection<?>) g) {
                    groupFields.add(o == null ? null : o.toString());
                }
                requestedGroupFields = Collections.unmodifiableList(groupFields);
            } else {
                requestedGroupFields = Collections.emptyList(); // если не было групп
            }

            // Разрешение имен полей
            final String metricFieldResolved = resolveFieldName(client, index, requestedField);
            final List<String> groupFieldsResolved = new ArrayList<>();
            for (String rf : requestedGroupFields) {
                groupFieldsResolved.add(resolveFieldName(client, index, rf));
            }

            // Формируем метрики для агрегации
            final MetricType metricType = MetricType.fromString(agg);
            if (metricType == null) {
                throw new IllegalArgumentException("Unsupported aggregation-function: " + agg);
            }
            final AggregationBuilder metricAgg = metricType.createAggregation(metricFieldResolved);

            // Контейнер для тела search: query, aggregation, sort - отправляется в
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().size(0);

            // Если 0 - просто агрегация всех
            if (groupFieldsResolved.isEmpty()) {
                searchSourceBuilder.aggregation(metricAgg);
            } else if (groupFieldsResolved.size() == 1) { // если одна группа
                searchSourceBuilder.aggregation(AggregationBuilders
                        .terms("by_group") // спец билдер для серча
                        .field(groupFieldsResolved.get(0)) // поле для группировки
                        .size(10000) // ограничение количества термов
                        .subAggregation(metricAgg)); // добавляем подАгрегацию для каждого бакета
            } else { // Если много групп
                List<CompositeValuesSourceBuilder<?>> sources = new ArrayList<>(); // Собираем список для групп, используется "composite"
                for (String groupField : groupFieldsResolved) {
                    sources.add(new TermsValuesSourceBuilder(groupField).field(groupField)); // Создаём терм-подстроитель для "composite" по каждому полю
                }
                // Ну и создается сам агрегат как в группе с 1
                CompositeAggregationBuilder composite = new CompositeAggregationBuilder("composite", sources)
                        .size(10000)
                        .subAggregation(metricAgg);
                searchSourceBuilder.aggregation(composite);
            }

            // Формируем запрос по указанному индексу
            final SearchRequest searchRequest = new SearchRequest(index).source(searchSourceBuilder);

            // Штука, которую серч вызовет с RestChannel, также формируем ответ в виде json,
            return channel -> client.search(searchRequest, new ActionListener<>() { // асинхронный поиск
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    try {
                        XContentBuilder jsonBuilder = XContentFactory.jsonBuilder();
                        jsonBuilder.startObject();
                        jsonBuilder.field("value");
                        jsonBuilder.startArray();

                        // Формируем агрегации для ответа
                        Aggregations aggregations = searchResponse.getAggregations();
                        if (groupFieldsResolved.isEmpty()) { // Если одна группа
                            if (aggregations != null) {
                                jsonBuilder.startObject();
                                metricType.writeMetricTo(jsonBuilder, aggregations);
                                jsonBuilder.endObject();
                            }
                        } else if (groupFieldsResolved.size() == 1) { // если одна группа
                            Terms terms = aggregations.get("by_group");
                            if (terms != null) {
                                for (Terms.Bucket bucket : terms.getBuckets()) {
                                    jsonBuilder.startObject();
                                    jsonBuilder.field(requestedGroupFields.get(0), bucket.getKeyAsString());
                                    metricType.writeMetricTo(jsonBuilder, bucket.getAggregations());
                                    jsonBuilder.endObject();
                                }
                            }
                        } else { // Много групп
                            CompositeAggregation comp = aggregations.get("composite");
                            if (comp != null) {
                                for (CompositeAggregation.Bucket bucket : comp.getBuckets()) {
                                    jsonBuilder.startObject();
                                    Map<String, Object> keyMap = bucket.getKey();
                                    for (String k : keyMap.keySet()) {
                                        jsonBuilder.field(k, keyMap.get(k));
                                    }
                                    Aggregations subAggs = bucket.getAggregations();
                                    metricType.writeMetricTo(jsonBuilder, subAggs);
                                    jsonBuilder.endObject();
                                }
                            }
                        }

                        jsonBuilder.endArray();
                        jsonBuilder.field("Count", (groupFieldsResolved.isEmpty() ? 1 : (groupFieldsResolved.size() == 1 ? (aggregations != null && aggregations.get("by_group") != null ? ((Terms)aggregations.get("by_group")).getBuckets().size() : 0) : (aggregations != null && aggregations.get("composite") != null ? ((CompositeAggregation)aggregations.get("composite")).getBuckets().size() : 0))));
                        jsonBuilder.endObject();

                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, jsonBuilder));
                    } catch (IOException e) {
                        channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
                }
            });

        }
    }

    // Метод для добавления ".keyword" в текстовые поля
    private String resolveFieldName(NodeClient client, String index, String field) {
        try {
            if (field == null || field.endsWith(".keyword")) return field; // если уже есть ".keyword" или вообще нет поля

            GetMappingsRequest req = new GetMappingsRequest().indices(index); // запрос маппинга для указанных индексов
            GetMappingsResponse resp = client.admin().indices().getMappings(req).actionGet(); // синхронный вызов через админский клиент, actionGet() - блокирует и возвращает результат

            Map<String, MappingMetadata> mappings = resp.mappings(); // мапа метаданных
            if (mappings == null || mappings.isEmpty()) return field;
            MappingMetadata mm = mappings.get(index);
            // Берем первый маппинг в ответе, если ничего не нашли нужного
            if (mm == null) {
                Iterator<MappingMetadata> it = mappings.values().iterator();
                if (!it.hasNext()) return field;
                mm = it.next();
            }

            // Валидируем навигацию в самой мапе по структуре: mapping → properties → field → type
            Map<String, Object> source = mm.sourceAsMap();
            if (source == null) return field;
            Object propsObj = source.get("properties");
            if (!(propsObj instanceof Map)) return field;
            Map<?, ?> props = (Map<?, ?>) propsObj;
            Object fObj = props.get(field);
            if (!(fObj instanceof Map)) return field;
            Map<?, ?> fMap = (Map<?, ?>) fObj;
            Object typeObj = fMap.get("type");
            if (typeObj == null) return field;
            String type = typeObj.toString();
            // Возвращаем уже нужное поле
            if ("text".equals(type)) {
                Object fields = fMap.get("fields");
                if (fields instanceof Map && ((Map<?, ?>) fields).containsKey("keyword")) {
                    return field + ".keyword";
                }
            }
            return field;
        } catch (Exception e) {
            return field;
        }
    }
}