/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.string;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.sql.SqlIllegalArgumentException;
import org.elasticsearch.xpack.sql.expression.function.scalar.processor.runtime.Processor;
import org.elasticsearch.xpack.sql.expression.function.scalar.string.Concat.ConcatOperation;

import java.io.IOException;

public class ConcatFunctionProcessor extends BinaryStringProcessor<ConcatOperation, String, String> {

    public static final String NAME = "cb";
    
    public ConcatFunctionProcessor(StreamInput in) throws IOException {
        super(in, i -> i.readEnum(ConcatOperation.class));
    }

    public ConcatFunctionProcessor(Processor left, Processor right, ConcatOperation operation) {
        super(left, right, operation);
    }

    @Override
    protected void doWrite(StreamOutput out) throws IOException {
        out.writeEnum(operation());
    }

    @Override
    protected Object doProcess(Object left, Object right) {
        if (left == null && right == null) {
            return null;
        }
        if (left != null && !(left instanceof String || left instanceof Character)) {
            throw new SqlIllegalArgumentException("A string/char is required; received [{}]", left);
        }
        if (right != null && !(right instanceof String || right instanceof Character)) {
            throw new SqlIllegalArgumentException("A string/char is required; received [{}]", right);
        }

        return operation().apply(left instanceof Character ? left.toString() : (String) left, 
                right instanceof Character ? right.toString() : (String) right);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }
}
