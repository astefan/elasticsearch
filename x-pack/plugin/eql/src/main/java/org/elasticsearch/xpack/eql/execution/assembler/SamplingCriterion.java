/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.execution.assembler;

import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.xpack.eql.execution.search.QueryRequest;
import org.elasticsearch.xpack.ql.execution.search.extractor.BucketExtractor;

import java.util.List;

public class SamplingCriterion<Q extends QueryRequest> {

    private final int stage;
    private final Q queryRequest;
    private final List<BucketExtractor> keys;
    
    private final int keySize;

    public SamplingCriterion(int stage, Q queryRequest, List<BucketExtractor> keys) {
        this.stage = stage;
        this.queryRequest = queryRequest;
        this.keys = keys;
        this.keySize = keys.size();
    }

    public int keySize() {
        return keySize;
    }

    public int stage() {
        return stage;
    }

    public Q queryRequest() {
        return queryRequest;
    }

    public Object[] key(Bucket bucket) {
        Object[] key = null;
        if (keySize > 0) {
            Object[] docKeys = new Object[keySize];
            for (int i = 0; i < keySize; i++) {
                docKeys[i] = keys.get(i).extract(bucket);
            }
            key = docKeys;
        }
        return key;
    }


    @Override
    public String toString() {
        return "[" + stage + "]";
    }
}
