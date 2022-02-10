/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.planner;

import org.elasticsearch.xpack.eql.EqlIllegalArgumentException;
import org.elasticsearch.xpack.eql.plan.physical.EsQueryExec;
import org.elasticsearch.xpack.eql.plan.physical.FilterExec;
import org.elasticsearch.xpack.eql.plan.physical.LimitWithOffsetExec;
import org.elasticsearch.xpack.eql.plan.physical.OrderExec;
import org.elasticsearch.xpack.eql.plan.physical.PhysicalPlan;
import org.elasticsearch.xpack.eql.plan.physical.ProjectExec;
import org.elasticsearch.xpack.eql.plan.physical.SamplingExec;
import org.elasticsearch.xpack.eql.plan.physical.SequenceExec;
import org.elasticsearch.xpack.eql.plan.physical.UnaryExec;
import org.elasticsearch.xpack.eql.querydsl.container.QueryContainer;
import org.elasticsearch.xpack.ql.expression.Attribute;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Expressions;
import org.elasticsearch.xpack.ql.expression.FieldAttribute;
import org.elasticsearch.xpack.ql.expression.Order;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators;
import org.elasticsearch.xpack.ql.querydsl.container.AttributeSort;
import org.elasticsearch.xpack.ql.querydsl.container.Sort.Direction;
import org.elasticsearch.xpack.ql.querydsl.container.Sort.Missing;
import org.elasticsearch.xpack.ql.querydsl.query.Query;
import org.elasticsearch.xpack.ql.rule.Rule;
import org.elasticsearch.xpack.ql.rule.RuleExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class QueryFolder extends RuleExecutor<PhysicalPlan> {

    PhysicalPlan fold(PhysicalPlan plan) {
        return execute(plan);
    }

    @Override
    protected Iterable<RuleExecutor<PhysicalPlan>.Batch> batches() {
        Batch fold = new Batch("Fold queries", new FoldProject(), new FoldFilter(), new FoldOrderBy(), new FoldLimit());
        Batch finish = new Batch("Finish query", Limiter.ONCE, new PropagateCompositeKeys(), new PlanOutputToQueryRef());

        return Arrays.asList(fold, finish);
    }

    private static class FoldProject extends QueryFoldingRule<ProjectExec> {

        @Override
        protected PhysicalPlan rule(ProjectExec project, EsQueryExec exec) {
            return new EsQueryExec(exec.source(), project.output(), exec.queryContainer());
        }
    }

    private static class FoldFilter extends QueryFoldingRule<FilterExec> {

        @Override
        protected PhysicalPlan rule(FilterExec plan, EsQueryExec exec) {
            QueryContainer qContainer = exec.queryContainer();
            Query query = QueryTranslator.toQuery(plan.condition());

            if (qContainer.query() != null || query != null) {
                query = ExpressionTranslators.and(plan.source(), qContainer.query(), query);
            }

            qContainer = qContainer.with(query);
            return exec.with(qContainer);
        }
    }

    private static class FoldOrderBy extends QueryFoldingRule<OrderExec> {

        @Override
        protected PhysicalPlan rule(OrderExec plan, EsQueryExec query) {
            QueryContainer qContainer = query.queryContainer();

            for (Order order : plan.order()) {
                Direction direction = Direction.from(order.direction());
                Missing missing = Missing.from(order.nullsPosition());

                // check whether sorting is on an group (and thus nested agg) or field
                Expression orderExpression = order.child();

                String lookup = Expressions.id(orderExpression);

                // field
                if (orderExpression instanceof FieldAttribute fa) {
                    qContainer = qContainer.addSort(lookup, new AttributeSort(fa, direction, missing));
                }
                // unknown
                else {
                    throw new EqlIllegalArgumentException("unsupported sorting expression {}", orderExpression);
                }
            }

            return query.with(qContainer);
        }
    }

    private static class FoldLimit extends FoldingRule<LimitWithOffsetExec> {

        @Override
        protected PhysicalPlan rule(LimitWithOffsetExec limit) {
            PhysicalPlan plan = limit;
            PhysicalPlan child = limit.child();
            if (child instanceof EsQueryExec query) {
                plan = query.with(query.queryContainer().with(limit.limit()));
            }
            if (child instanceof SequenceExec exec) {
                plan = exec.with(limit.limit());
            }
            return plan;
        }
    }

    /*private static class PlanOutputToCompositeAggRef extends FoldingRule<CompositeAggExec> {
        @Override
        protected PhysicalPlan rule(CompositeAggExec comp) {
            PhysicalPlan plan = comp;
            PhysicalPlan child = comp.child();
            if (child instanceof EsQueryExec query) {
                QueryContainer qContainer = query.queryContainer();
                for (Attribute attr : comp.output()) {
                    qContainer = qContainer.addCompositeKey(attr);
                }
                query = query.with(qContainer);
            }
    
            // after all attributes have been resolved
            return plan;
        }
    }*/
    private static class PropagateCompositeKeys extends FoldingRule<SamplingExec> {
        @Override
        protected PhysicalPlan rule(SamplingExec plan) {
            List<PhysicalPlan> newChildren = new ArrayList<>();

            for (int i = 0; i < plan.children().size(); i++) {
                EsQueryExec query = (EsQueryExec) plan.children().get(i);
                QueryContainer qContainer = query.queryContainer();
                query = new EsQueryExec(query.source(), Collections.emptyList(), qContainer);

                List<Attribute> keys = plan.keys().get(i);
                for (Attribute a : keys) {
                    qContainer = qContainer.addCompositeKey(a);
                }
                newChildren.add(query.with(qContainer));
            }
            return plan.replaceChildren(newChildren);
        }
    }

    private static class PlanOutputToQueryRef extends FoldingRule<EsQueryExec> {
        @Override
        protected PhysicalPlan rule(EsQueryExec exec) {
            QueryContainer qContainer = exec.queryContainer();

            for (Attribute attr : exec.output()) {
                qContainer = qContainer.addColumn(attr);
            }

            // after all attributes have been resolved
            return exec.with(qContainer);
        }
    }

    abstract static class FoldingRule<SubPlan extends PhysicalPlan> extends Rule<SubPlan, PhysicalPlan> {

        @Override
        public final PhysicalPlan apply(PhysicalPlan plan) {
            return plan.transformUp(typeToken(), this::rule);
        }

        @Override
        protected abstract PhysicalPlan rule(SubPlan plan);
    }

    abstract static class QueryFoldingRule<SubPlan extends UnaryExec> extends FoldingRule<SubPlan> {

        @Override
        protected final PhysicalPlan rule(SubPlan plan) {
            PhysicalPlan p = plan;
            if (plan.child() instanceof EsQueryExec) {
                p = rule(plan, (EsQueryExec) plan.child());
            }
            return p;
        }

        protected abstract PhysicalPlan rule(SubPlan plan, EsQueryExec query);
    }
}
