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
import org.elasticsearch.xpack.sql.expression.function.scalar.string.BinaryStringStringProcessor.BinaryStringStringOperation;

import java.io.IOException;
import java.util.function.BiFunction;

public class BinaryStringStringProcessor extends BinaryStringProcessor<BinaryStringStringOperation, String, Number> {
    
    public BinaryStringStringProcessor(StreamInput in) throws IOException {
        super(in, i -> i.readEnum(BinaryStringStringOperation.class));
    }

    public BinaryStringStringProcessor(Processor left, Processor right, BinaryStringStringOperation operation) {
        super(left, right, operation);
    }

    public enum BinaryStringStringOperation implements BiFunction<String, String, Number> {
        DIFFERENCE((s,c) -> {
            return 0;
        }),
        POSITION((sub,str) -> {
            if (sub == null || str == null) return null;
            int pos = str.indexOf(sub);
            return pos < 0 ? 0 : pos+1;
        });

        BinaryStringStringOperation(BiFunction<String, String, Number> op) {
            this.op = op;
        }
        
        private final BiFunction<String, String, Number> op;
        
        @Override
        public Number apply(String stringExpLeft, String stringExpRight) {
            return op.apply(stringExpLeft, stringExpRight);
        }
    }

    @Override
    protected void doWrite(StreamOutput out) throws IOException {
        out.writeEnum(operation());
    }

    @Override
    protected Object doProcess(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (!(left instanceof String)) {
            throw new SqlIllegalArgumentException("A string is required; received [{}]", left);
        }
        if (!(right instanceof String)) {
            throw new SqlIllegalArgumentException("A string is required; received [{}]", right);
        }

        return operation().apply((String) left, (String) right);
    }

}
