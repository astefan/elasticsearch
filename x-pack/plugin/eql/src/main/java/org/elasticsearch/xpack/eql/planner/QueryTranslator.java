/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.eql.planner;

import org.elasticsearch.xpack.eql.expression.function.scalar.string.StartsWith;
import org.elasticsearch.xpack.ql.QlIllegalArgumentException;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.FieldAttribute;
import org.elasticsearch.xpack.ql.expression.function.scalar.ScalarFunction;
import org.elasticsearch.xpack.ql.expression.predicate.logical.And;
import org.elasticsearch.xpack.ql.expression.predicate.logical.Or;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslator;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.BinaryComparisons;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.InComparisons;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.Likes;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.Matches;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.MultiMatches;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.Nots;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.Ranges;
import org.elasticsearch.xpack.ql.planner.ExpressionTranslators.StringQueries;
import org.elasticsearch.xpack.ql.planner.TranslatorHandler;
import org.elasticsearch.xpack.ql.querydsl.query.PrefixQuery;
import org.elasticsearch.xpack.ql.querydsl.query.Query;
import org.elasticsearch.xpack.ql.querydsl.query.ScriptQuery;

import java.util.List;
import java.util.Locale;

import static org.elasticsearch.xpack.ql.planner.ExpressionTranslators.and;
import static org.elasticsearch.xpack.ql.planner.ExpressionTranslators.or;

final class QueryTranslator {
    public static final List<ExpressionTranslator<?>> QUERY_TRANSLATORS = List.of(
        new BinaryComparisons(),
        new Ranges(),
        new BinaryLogic(),
        new Nots(),
        new Likes(),
        new InComparisons(),
        new StringQueries(),
        new Matches(),
        new MultiMatches(),
        new Scalars()
        );

    public static Query toQuery(Expression e) {
        return toQuery(e, new EqlTranslatorHandler());
    }
    
    public static Query toQuery(Expression e, TranslatorHandler handler) {
        Query translation = null;
        for (ExpressionTranslator<?> translator : QUERY_TRANSLATORS) {
            translation = translator.translate(e, handler);
            if (translation != null) {
                return translation;
            }
        }
    
        throw new QlIllegalArgumentException("Don't know how to translate {} {}", e.nodeName(), e);
    }

    public static class BinaryLogic extends ExpressionTranslator<org.elasticsearch.xpack.ql.expression.predicate.logical.BinaryLogic> {

        @Override
        protected Query asQuery(org.elasticsearch.xpack.ql.expression.predicate.logical.BinaryLogic e, TranslatorHandler handler) {
            if (e instanceof And) {
                return and(e.source(), toQuery(e.left(), handler), toQuery(e.right(), handler));
            }
            if (e instanceof Or) {
                return or(e.source(), toQuery(e.left(), handler), toQuery(e.right(), handler));
            }

            return null;
        }
    }

    public static class Scalars extends ExpressionTranslator<ScalarFunction> {

        @Override
        protected Query asQuery(ScalarFunction f, TranslatorHandler handler) {
            return doTranslate(f, handler);
        }

        public static Query doTranslate(ScalarFunction f, TranslatorHandler handler) {
            if (f instanceof StartsWith) {
                StartsWith sw = (StartsWith) f;
                if (sw.field() instanceof FieldAttribute && sw.pattern().foldable()) {
                    String targetFieldName = handler.nameOf(((FieldAttribute) sw.field()).exactAttribute());
                    String pattern = (String) sw.pattern().fold();

                    return new PrefixQuery(f.source(), targetFieldName, pattern.toLowerCase(Locale.ROOT));
                }
            }

            return handler.wrapFunctionQuery(f, f, new ScriptQuery(f.source(), f.asScript()));
        }
    }
}
