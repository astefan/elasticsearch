/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.string;

import org.elasticsearch.xpack.sql.expression.Expression;
import org.elasticsearch.xpack.sql.expression.function.scalar.BinaryScalarFunction;
import org.elasticsearch.xpack.sql.expression.function.scalar.processor.definition.ProcessorDefinition;
import org.elasticsearch.xpack.sql.expression.function.scalar.processor.definition.ProcessorDefinitions;
import org.elasticsearch.xpack.sql.tree.Location;
import org.elasticsearch.xpack.sql.tree.NodeInfo;
import org.elasticsearch.xpack.sql.type.DataType;

import java.util.function.BiFunction;

/**
 * Returns a string that is the result of concatenating the two strings received as parameters.
 * The result of the function is null only if both parameters are null, otherwise the result is the non-null
 * parameter or the concatenation of the two strings if none of them is null.
 */
public class Concat extends BinaryStringFunction<String, String> {

    public Concat(Location location, Expression left, Expression right) {
        super(location, left, right);
    }

    @Override
    protected BiFunction<String, String, String> operation() {
        return ConcatOperation.CONCAT;
    }

    @Override
    protected TypeResolution resolveSecondParameterInputType(DataType inputType) {
        return inputType.isString() ? 
                TypeResolution.TYPE_RESOLVED : 
                new TypeResolution("'%s' requires second parameter to be a string type, received %s", functionName(), inputType);
    }

    @Override
    protected BinaryScalarFunction replaceChildren(Expression newLeft, Expression newRight) {
        return new Concat(location(), newLeft, newRight);
    }

    @Override
    protected ProcessorDefinition makeProcessorDefinition() {
        return new ConcatFunctionProcessorDefinition(location(), this,
                ProcessorDefinitions.toProcessorDefinition(left()),
                ProcessorDefinitions.toProcessorDefinition(right()),
                ConcatOperation.CONCAT);
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, Concat::new, left(), right());
    }
    
    public enum ConcatOperation implements BiFunction<String, String, String> {
        CONCAT((s,c) -> {
            if (s == null) return c;
            if (c == null) return s;
            return s.concat(c);
        });

        ConcatOperation(BiFunction<String, String, String> op) {
            this.op = op;
        }
        
        private final BiFunction<String, String, String> op;
        
        @Override
        public String apply(String stringExpLeft, String stringExpRight) {
            return op.apply(stringExpLeft, stringExpRight);
        }
    }
}
