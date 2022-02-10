/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.execution.sampling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.composite.InternalComposite;
import org.elasticsearch.xpack.eql.EqlIllegalArgumentException;
import org.elasticsearch.xpack.eql.execution.assembler.AggregatedQueryRequest;
import org.elasticsearch.xpack.eql.execution.assembler.Executable;
import org.elasticsearch.xpack.eql.execution.assembler.SamplingCriterion;
import org.elasticsearch.xpack.eql.execution.search.QueryClient;
import org.elasticsearch.xpack.eql.session.Payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static org.elasticsearch.action.ActionListener.wrap;
import static org.elasticsearch.xpack.eql.execution.assembler.AggregatedQueryRequest.COMPOSITE_AGG_NAME;

public class SamplingIterator implements Executable {

    public final static int MAX_PAGE_SIZE = 1000;
    private final Logger log = LogManager.getLogger(SamplingIterator.class);

    private final QueryClient client;
    private final List<SamplingCriterion<AggregatedQueryRequest>> criteria;
    private final Stack<Page> stack = new Stack<>();
    // shortcut
    private final int maxStages;

    private final boolean hasKeys;

    private long startTime;

    public SamplingIterator(
        QueryClient client,
        List<SamplingCriterion<AggregatedQueryRequest>> criteria
    ) {
        this.client = client;
        this.criteria = criteria;
        this.maxStages = criteria.size();

        SamplingCriterion<AggregatedQueryRequest> baseRequest = criteria.get(0);
        this.hasKeys = baseRequest.keySize() > 0;
    }

    @Override
    public void execute(ActionListener<Payload> listener) {
        startTime = System.currentTimeMillis();
        iterate(0, listener);
    }

    private void iterate(int currentStage, ActionListener<Payload> listener) {
        if (currentStage == maxStages + 1) { // the last queries we'll run to get the events matching the final set of keys
            finalStage(listener);
        } else if (currentStage == 0) {
            advance(listener);
        } else {
            advance(listener);
        }
    }

    private void finalStage(ActionListener<Payload> listener) {
        // query everything and build sequences
        // then remove the previous step from the stack, since we are done with it
        Page page = stack.pop();
        for (InternalComposite.InternalBucket hit : page.hits) {
            //page.keys()
        }
        if (page.size() == MAX_PAGE_SIZE) {
            advance(listener);
        } else {
            // we are done here with this page
            Page hits = stack.peek();
            
        }
    }

    private void advance(ActionListener<Payload> listener) {
        int currentStage = stack.size();
        if (currentStage < maxStages) {
            SamplingCriterion<AggregatedQueryRequest> criterion = criteria.get(currentStage);
            AggregatedQueryRequest request = criterion.queryRequest();

            // incorporate the previous stage composite keys in the current stage query
            if (currentStage > 0) {
                Page previousResults = stack.peek();
                List<Map<String, Object>> values = new ArrayList<>(previousResults.size);
                for (InternalComposite.InternalBucket bucket : previousResults.hits) {
                    values.add(bucket.getKey());
                }
                request.keys(values, previousResults.keys);
            }

            log.trace("Querying stage [{}] {}", criterion.stage(), request);
            client.query(request, wrap(r -> {
                InternalComposite composite;
                Aggregation a = r.getAggregations().get(COMPOSITE_AGG_NAME);
                if (a instanceof InternalComposite agg) {
                    composite = agg;
                } else {
                    throw new EqlIllegalArgumentException("Unexpected aggregation result type returned [{}]", a.getClass());
                }
                log.trace("Found [{}] hits", composite.getSize());
                Page nextPage = new Page(composite.getBuckets(), 0, composite.afterKey(), request.keys());
                stack.push(nextPage);
            }, listener::onFailure));
            advance(listener);
        } else if (currentStage > maxStages) {
            throw new EqlIllegalArgumentException("Unexpected stage [{}], max stages in this sampling [{}]", currentStage, maxStages);
        } else {
            finalStage(listener);
        }
        //iterate(currentStage, listener);
    }

    private void payload(ActionListener<Payload> listener) {
        /*List<Sequence> completed = matcher.completed();

        log.trace("Sending payload for [{}] sequences", completed.size());

        if (completed.isEmpty()) {
            listener.onResponse(new EmptyPayload(Type.SEQUENCE, timeTook()));
            close(listener);
            return;
        }

        // get results through search (to keep using PIT)
        client.fetchHits(hits(completed), ActionListeners.map(listener, listOfHits -> {
            if (criteria.get(0).descending()) {
                Collections.reverse(completed);
            }
            SequencePayload payload = new SequencePayload(completed, listOfHits, false, timeTook());
            close(listener);
            return payload;
        }));*/
    }

    private void close(ActionListener<Payload> listener) {
        //matcher.clear();
        client.close(listener.delegateFailure((l, r) -> {}));
    }

    private TimeValue timeTook() {
        return new TimeValue(System.currentTimeMillis() - startTime);
    }

    private record Page(List<InternalComposite.InternalBucket> hits, int size, Map<String, Object> afterKey, List<String> keys) {
        Page(List<InternalComposite.InternalBucket> hits, int size, Map<String, Object> afterKey, List<String> keys) {
            this.hits = hits;
            this.size = hits.size();
            this.afterKey = afterKey;
            this.keys = keys;
        }
    }
}
