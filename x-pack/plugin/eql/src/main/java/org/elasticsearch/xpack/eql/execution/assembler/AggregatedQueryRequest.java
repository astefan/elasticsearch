/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.execution.assembler;

import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.xpack.eql.EqlIllegalArgumentException;
import org.elasticsearch.xpack.eql.execution.sample.SampleIterator;
import org.elasticsearch.xpack.eql.execution.search.Ordinal;
import org.elasticsearch.xpack.eql.execution.search.QueryRequest;
import org.elasticsearch.xpack.eql.execution.search.RuntimeUtils;
import org.elasticsearch.xpack.ql.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class AggregatedQueryRequest implements QueryRequest {

    private static NamedWriteableRegistry registry = new NamedWriteableRegistry(
        new SearchModule(Settings.EMPTY, List.of()).getNamedWriteables()
    );
    public static final String COMPOSITE_AGG_NAME = "keys";
    private SearchSourceBuilder searchSource;
    private final List<String> keys; // the name of the join keys
    private CompositeAggregationBuilder agg;
    private List<QueryBuilder> multipleKeyFilters;
    private List<QueryBuilder> singleKeyPairFilters;

    public AggregatedQueryRequest(QueryRequest original, List<String> keyNames) {
        this.searchSource = original.searchSource();
        this.keys = keyNames;
    }

    @Override
    public SearchSourceBuilder searchSource() {
        return searchSource;
    }

    @Override
    public void nextAfter(Ordinal ordinal) {}

    public void nextAfter(Map<String, Object> afterKeys) {
        agg.aggregateAfter(afterKeys);
        // Map<String, Object> stringAfterKey = new HashMap<>(mapSize(afterKeys.size()));
        // for (Entry<String, Object> entry : afterKeys.entrySet()) {
        // if a field is missing from an index, the after_key of the composite aggregation always expects a String
        // for example, setting after key as Integer on a field "X" and the field doesn't exist in the index targeted by the query,
        // then "composite" parsing will throw an exception
        // stringAfterKey.put(entry.getKey(), entry.getValue().toString());
        // }
        // agg.aggregateAfter(stringAfterKey);
    }

    public List<String> keys() {
        return keys;
    }

    /**
     * Sets keys / terms to filter on in an intermediate stage filtering.
     * Can be removed through null.
     */
    public void multipleKeyPairs(List<Map<String, Object>> values, List<String> previousCriterionKeys) {
        assert previousCriterionKeys != null && previousCriterionKeys.size() == keys.size();

        List<QueryBuilder> newFilters;
        if (values.isEmpty()) {
            // no keys have been specified and none have been set
            if (CollectionUtils.isEmpty(multipleKeyFilters)) {
                return;
            }
            newFilters = emptyList();
        } else {
            BoolQueryBuilder orKeys = boolQuery();
            newFilters = Collections.singletonList(orKeys);

            for (Map<String, Object> bucket : values) {
                BoolQueryBuilder joinKeyBoolQuery = null;
                // the list of keys order is important because a key on one position corresponds to another key on the same
                // position from another query. For example, [host, os] corresponds to [hostname, op_sys].
                for (int i = 0; i < keys.size(); i++) {
                    // build a bool must for the key of this criterion, but using the value of the previous criterion results
                    Object value = bucket.get(previousCriterionKeys.get(i));
                    if (value != null) {
                        if (joinKeyBoolQuery == null) {
                            joinKeyBoolQuery = boolQuery();
                        }
                        joinKeyBoolQuery.must(termQuery(keys.get(i), value));
                    } else {
                        /*
                         * Joining on null values can generate technically valid results, but difficult o understand by users. For example,
                         *
                         * sample by host [any where bool == true] by os [any where uptime > 0] by os [any where port > 100] by op_sys
                         *
                         * If we would allow "null" values as valid join keys, it is possible to get a match on documents that do not
                         * have a value for "op_sys" in some indices (but have a value on "os") and other documents that do not have a value
                         * for "os" (but have a value in "op_sys").
                         *
                         * Result for the above query:
                         * "join_keys": ["doom",null]
                         * "events": [{"_index":"test2","_id": "6","host": "doom","port": 65123,"bool": true,"op_sys": "redhat"}
                         *           {"_index": "test2","_id": "7","host": "doom","uptime": 15,"port": 1234,"bool": true,"op_sys": "redhat"}
                         *           {"_index": "test1","_id": "1","host": "doom","uptime": 0,"port": 1234,"os": "win10"}]
                         */
                        // joinKeyBoolQuery.mustNot(existsQuery(keys.get(i)));
                    }
                }

                if (joinKeyBoolQuery != null) {
                    orKeys.should(joinKeyBoolQuery);
                }
            }
        }

        RuntimeUtils.replaceFilter(multipleKeyFilters, newFilters, searchSource);
        multipleKeyFilters = newFilters;
    }

    /**
     * Sets keys / terms to filter on in the final stage filtering (where actual events are gathered).
     * Can be removed through null.
     */
    public void singleKeyPair(final List<Object> compositeKeyValues, int maxStages) {
        List<QueryBuilder> newFilters = new ArrayList<>();
        if (compositeKeyValues.isEmpty()) {
            // no keys have been specified and none have been set
            if (CollectionUtils.isEmpty(singleKeyPairFilters)) {
                return;
            }
            newFilters = emptyList();
        } else {
            Object value;
            for (int i = 0; i < keys.size(); i++) {
                value = compositeKeyValues.get(i);
                // if (value != null) {
                newFilters.add(termQuery(keys.get(i), value));
                // } else {
                // newFilters.add(boolQuery().mustNot(existsQuery(keys.get(i))));
                // }
            }
        }

        SearchSourceBuilder newSource = copySource(searchSource);
        RuntimeUtils.replaceFilter(singleKeyPairFilters, newFilters, newSource);
        newSource.size(maxStages) // ask for exactly number of filters/stages documents
            .terminateAfter(maxStages) // no need to ask for more from each shard since we don't need sorting or more docs
            .fetchSource(FetchSourceContext.DO_NOT_FETCH_SOURCE) // we'll get the source in a separate step
            .trackTotalHits(false)
            .trackScores(false);
        singleKeyPairFilters = newFilters;
        searchSource = newSource;
    }

    public void withCompositeAggregation() {
        if (this.agg != null) {
            return;
        }

        List<CompositeValuesSourceBuilder<?>> compositeAggSources = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            // compositeAggSources.add(new TermsValuesSourceBuilder(key).field(key).missingBucket(true));
            compositeAggSources.add(new TermsValuesSourceBuilder(key).field(key));
        }
        agg = new CompositeAggregationBuilder(COMPOSITE_AGG_NAME, compositeAggSources);
        agg.size(SampleIterator.MAX_PAGE_SIZE);
        searchSource.aggregation(agg);
    }

    /*
     * Not a great way of getting a copy of a SearchSourceBuilder
     */
    private static SearchSourceBuilder copySource(SearchSourceBuilder source) {
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            source.writeTo(output);
            try (StreamInput in = new NamedWriteableAwareStreamInput(output.bytes().streamInput(), registry)) {
                return new SearchSourceBuilder(in);
            }
        } catch (IOException e) {
            throw new EqlIllegalArgumentException("Error copying search source", e);
        }
    }
}
