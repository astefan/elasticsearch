/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.string;

import org.elasticsearch.xpack.sql.expression.Expression;
import org.elasticsearch.xpack.sql.expression.function.scalar.BinaryScalarFunction;
import org.elasticsearch.xpack.sql.expression.function.scalar.script.ScriptTemplate;
import org.elasticsearch.xpack.sql.tree.Location;
import org.elasticsearch.xpack.sql.type.DataType;

import java.util.Objects;
import java.util.function.BiFunction;

/*
 * A binary string function that abstracts away the second parameter of a binary string function and its result
 */
public abstract class BinaryStringFunction<T,R> extends BinaryScalarFunction {

    protected BinaryStringFunction(Location location, Expression left, Expression right) {
        super(location, left, right);
    }

    protected abstract BiFunction<String, T, R> operation();

    @Override
    public DataType dataType() {
        return DataType.KEYWORD;
    }

    @Override
    protected TypeResolution resolveType() {
        if (!childrenResolved()) {
            return new TypeResolution("Unresolved children");
        }

        if (!left().dataType().isString()) {
            return new TypeResolution("'%s' requires first parameter to be a string type, received %s", functionName(), left().dataType());
        }
                
        return resolveSecondParameterInputType(right().dataType());
    }

    protected abstract TypeResolution resolveSecondParameterInputType(DataType inputType);

    @Override
    public Object fold() {
        @SuppressWarnings("unchecked")
        T fold = (T) right().fold();
        return operation().apply((String) left().fold(), fold);
    }

    @Override
    protected ScriptTemplate asScriptFrom(ScriptTemplate leftScript, ScriptTemplate rightScript) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public int hashCode() {
        return Objects.hash(left(), right(), operation());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        BinaryStringFunction<?,?> other = (BinaryStringFunction<?,?>) obj;
        return Objects.equals(other.left(), left())
            && Objects.equals(other.right(), right())
            && Objects.equals(other.operation(), operation());
    }
}