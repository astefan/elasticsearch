/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.plan.logical;

import org.elasticsearch.xpack.ql.capabilities.Resolvables;
import org.elasticsearch.xpack.ql.expression.Attribute;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.util.Check;
import org.elasticsearch.xpack.ql.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Sampling extends LogicalPlan {

    private final List<KeyedFilter> queries;

    public Sampling(Source source, List<KeyedFilter> queries, KeyedFilter... query) {
        super(source, CollectionUtils.combine(queries, query));
        this.queries = queries;
    }

    static List<KeyedFilter> asKeyed(List<LogicalPlan> list) {
        List<KeyedFilter> keyed = new ArrayList<>(list.size());

        for (LogicalPlan logicalPlan : list) {
            Check.isTrue(KeyedFilter.class.isInstance(logicalPlan), "Expected a KeyedFilter but received [{}]", logicalPlan);
            keyed.add((KeyedFilter) logicalPlan);
        }

        return keyed;
    }

    static KeyedFilter asKeyed(LogicalPlan plan) {
        Check.isTrue(KeyedFilter.class.isInstance(plan), "Expected a KeyedFilter but received [{}]", plan);
        return (KeyedFilter) plan;
    }

    @Override
    protected NodeInfo<? extends Sampling> info() {
        return NodeInfo.create(this, Sampling::new, queries);
    }

    @Override
    public Sampling replaceChildren(List<LogicalPlan> newChildren) {
        return new Sampling(source(), asKeyed(newChildren));
    }

    @Override
    public List<Attribute> output() {
        List<Attribute> out = new ArrayList<>();
        for (KeyedFilter query : queries) {
            out.addAll(query.output());
        }
        return out;
    }

    @Override
    public boolean expressionsResolved() {
        return Resolvables.resolved(queries);
    }

    public List<KeyedFilter> queries() {
        return queries;
    }

    public Sampling with(List<KeyedFilter> queries) {
        return new Sampling(source(), queries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queries);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Sampling other = (Sampling) obj;

        return Objects.equals(queries, other.queries);
    }
}
