/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.execution.assembler;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xpack.eql.execution.sampling.SamplingIterator;
import org.elasticsearch.xpack.eql.execution.search.Ordinal;
import org.elasticsearch.xpack.eql.execution.search.QueryRequest;
import org.elasticsearch.xpack.eql.execution.search.RuntimeUtils;
import org.elasticsearch.xpack.ql.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.Collections.emptyList;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class AggregatedQueryRequest implements QueryRequest {

    public static final String COMPOSITE_AGG_NAME = "keys";
    private final SearchSourceBuilder searchSource;
    private final List<String> keys;
    private final CompositeAggregationBuilder agg;
    private List<QueryBuilder> keyFilters;

    public AggregatedQueryRequest(QueryRequest original, List<String> keyNames) {
        this.searchSource = original.searchSource();
        this.keys = keyNames;
        this.agg = compositeAggregation();
        this.searchSource.aggregation(agg);
    }

    @Override
    public SearchSourceBuilder searchSource() {
        return searchSource;
    }

    @Override
    public void nextAfter(Ordinal ordinal) {}

    public void nextAfter(List<Object> afterKeys) {
        Map<String, Object> after = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            after.put(keys.get(i), afterKeys.get(i));
        }
        agg.aggregateAfter(after);
    }

    public List<String> keys() {
        return keys;
    }

    /**
     * Sets keys / terms to filter on.
     * Accepts the unwrapped SequenceKey as a list of values matching an instance of a given
     * event.
     * Can be removed through null.
     */
    public void keys(List<Map<String, Object>> values, List<String> previousCriterionKeys) {
        assert previousCriterionKeys != null && previousCriterionKeys.size() == keys.size();

        List<QueryBuilder> newFilters;
        if (values.isEmpty()) {
            // no keys have been specified and none have been set
            if (CollectionUtils.isEmpty(keyFilters)) {
                return;
            }
            newFilters = emptyList();
        } else {
            int keysSize = keys.size();
            BoolQueryBuilder orKeys = boolQuery();
            newFilters = Collections.singletonList(orKeys);

            /*for (int i = 0; i < values.size(); i++) {
                List<Object> joinKeys = values.get(i);
                BoolQueryBuilder joinKeyBoolQuery = boolQuery();
            
                for (int keyIndex = 0; keyIndex < keysSize; keyIndex++) {
                    joinKeyBoolQuery.must(termQuery(keys.get(keyIndex), joinKeys.get(keyIndex)));
                }
            
                orKeys.should(joinKeyBoolQuery);
            }*/
            for (Map<String, Object> bucket : values) {
                BoolQueryBuilder joinKeyBoolQuery = boolQuery();
                // the list of keys order is important because a key on one position corresponds to another key on the same
                // position from another query. For example, [host, os] corresponds to [hostname, op_sys].
                for (int i = 0; i < keys.size(); i++) {
                    // build a bool must for a the key of this criterion, but using the value of the previous criterion results
                    joinKeyBoolQuery.must(termQuery(keys.get(i), bucket.get(previousCriterionKeys.get(i))));
                }

                orKeys.should(joinKeyBoolQuery);
            }
        }

        RuntimeUtils.replaceFilter(keyFilters, newFilters, searchSource);
        keyFilters = newFilters;
    }

    private CompositeAggregationBuilder compositeAggregation() {
        List<CompositeValuesSourceBuilder<?>> compositeAggSources = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            compositeAggSources.add(new TermsValuesSourceBuilder(key).field(key));
        }
        CompositeAggregationBuilder compositeAggregationBuilder = new CompositeAggregationBuilder(COMPOSITE_AGG_NAME, compositeAggSources);
        compositeAggregationBuilder.size(SamplingIterator.MAX_PAGE_SIZE);

        return compositeAggregationBuilder;
    }
}
