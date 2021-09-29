/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.parser;

import org.elasticsearch.xpack.eql.parser.EqlBaseParser.SingleStatementContext;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.UnresolvedAttribute;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;

import java.util.Set;

import static java.util.Collections.emptySet;

public class AstBuilder extends LogicalPlanBuilder {

    AstBuilder(ParserParams params) {
        super(params, emptySet(), emptySet());
    }

    AstBuilder(ParserParams params, Set<UnresolvedAttribute> allOptionals, Set<Expression> keyOptionals) {
        super(params, allOptionals, keyOptionals);
    }

    @Override
    public LogicalPlan visitSingleStatement(SingleStatementContext ctx) {
        return plan(ctx.statement());
    }
}
