/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.plan.logical;

import org.elasticsearch.xpack.ql.capabilities.Resolvables;
import org.elasticsearch.xpack.ql.expression.Attribute;
import org.elasticsearch.xpack.ql.expression.Order.OrderDirection;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.util.Check;
import org.elasticsearch.xpack.ql.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractJoin extends LogicalPlan {

    protected final List<KeyedFilter> queries;
    protected final OrderDirection direction;

    public AbstractJoin(Source source, OrderDirection direction, List<KeyedFilter> queries, KeyedFilter... query) {
        super(source, CollectionUtils.combine(queries, query));
        this.queries = queries;
        this.direction = direction;
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

    public OrderDirection direction() {
        return direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, queries);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        AbstractJoin other = (AbstractJoin) obj;
        return Objects.equals(direction, other.direction) && Objects.equals(queries, other.queries);
    }

}
