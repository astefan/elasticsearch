/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.esql.plan.logical;

import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xpack.esql.core.capabilities.Resolvables;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.NamedExpression;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.esql.expression.NamedExpressions.mergeOutputAttributes;

public class Insist extends UnaryPlan {
    private final List<NamedExpression> fields;

    public Insist(Source source, LogicalPlan child, List<NamedExpression> fields) {
        super(source, child);
        this.fields = fields;
    }

    private @Nullable List<Attribute> lazyOutput = null;

    @Override
    public List<Attribute> output() {
        if (lazyOutput == null) {
            lazyOutput = computeOutput();
        }
        return lazyOutput;
    }

    private List<Attribute> computeOutput() {
        if (lazyOutput == null) {
            lazyOutput = mergeOutputAttributes(fields, child().output());
        }

        return lazyOutput;
    }

    public List<NamedExpression> fields() {
        return fields;
    }

    @Override
    public Insist replaceChild(LogicalPlan newChild) {
        return new Insist(source(), newChild, fields);
    }

    @Override
    public boolean expressionsResolved() {
        return Resolvables.resolved(fields);
    }

    @Override
    protected NodeInfo<? extends LogicalPlan> info() {
        return NodeInfo.create(this, Insist::new, child(), fields());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        throw new UnsupportedOperationException("doesn't escape the node");
    }

    @Override
    public String getWriteableName() {
        throw new UnsupportedOperationException("doesn't escape the node");
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), Objects.hashCode(fields));
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && ((Insist) obj).fields.equals(fields);
    }
}
