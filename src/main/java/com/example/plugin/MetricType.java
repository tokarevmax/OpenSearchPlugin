package com.example.plugin;

import org.opensearch.search.aggregations.*;
import org.opensearch.search.aggregations.metrics.*;
import org.opensearch.core.xcontent.*;
import org.opensearch.search.aggregations.AggregationBuilders;

import java.io.IOException;
import java.util.Locale;

public enum MetricType {
    AVG {
        @Override
        AggregationBuilder createAggregation(String field) {
            return AggregationBuilders.avg("metric").field(field);
        }

        @Override
        void writeMetricTo(XContentBuilder jsonBuilder, Aggregations aggregations) throws IOException {
            Avg avg = aggregations == null ? null : aggregations.get("metric");
            Double value = (avg == null || Double.isNaN(avg.getValue())) ? null : avg.getValue();
            jsonBuilder.field("avg", value);
        }
    },
    MAX {
        @Override
        AggregationBuilder createAggregation(String field) {
            return AggregationBuilders.max("metric").field(field);
        }

        @Override
        void writeMetricTo(XContentBuilder jsonBuilder, Aggregations aggregations) throws IOException {
            Max max = aggregations == null ? null : aggregations.get("metric");
            Double value = (max == null || Double.isNaN(max.getValue()) || Double.isInfinite(max.getValue())) ? null : max.getValue();
            jsonBuilder.field("max", value);
        }
    },
    DC { // cardinality
        @Override
        AggregationBuilder createAggregation(String field) {
            return AggregationBuilders.cardinality("metric").field(field);
        }

        @Override
        void writeMetricTo(XContentBuilder jsonBuilder, Aggregations aggregations) throws IOException {
            Cardinality dc = aggregations == null ? null : aggregations.get("metric");
            long value = dc == null ? 0 : dc.getValue();
            jsonBuilder.field("dc", value);
        }
    };

    abstract AggregationBuilder createAggregation(String field);

    abstract void writeMetricTo(XContentBuilder jsonBuilder, Aggregations aggregations) throws IOException;

    static MetricType fromString(String s) {
        if (s == null) return null;
        try {
            return MetricType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}