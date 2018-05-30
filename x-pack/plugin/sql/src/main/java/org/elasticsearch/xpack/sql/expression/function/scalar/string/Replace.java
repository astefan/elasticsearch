/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.string;

import org.elasticsearch.xpack.sql.expression.Expression;
import org.elasticsearch.xpack.sql.expression.function.scalar.ScalarFunction;
import org.elasticsearch.xpack.sql.expression.function.scalar.processor.definition.ProcessorDefinition;
import org.elasticsearch.xpack.sql.expression.function.scalar.processor.definition.ProcessorDefinitions;
import org.elasticsearch.xpack.sql.expression.function.scalar.script.ScriptTemplate;
import org.elasticsearch.xpack.sql.tree.Location;
import org.elasticsearch.xpack.sql.tree.NodeInfo;
import org.elasticsearch.xpack.sql.type.DataType;

import java.util.Arrays;
import java.util.List;

/*
 * Search the source string for occurrences of the pattern, and replace with the replacement string.
 */
public class Replace extends ScalarFunction {

    private final Expression source, pattern, replacement;
    
    public Replace(Location location, Expression source, Expression pattern, Expression replacement) {
        super(location, Arrays.asList(source, pattern, replacement));
        this.source = source;
        this.pattern = pattern;
        this.replacement = replacement;
    }
    
    protected TypeResolution resolveType() {
        if (!childrenResolved()) {
            return new TypeResolution("Unresolved children");
        }

        TypeResolution sourceResolution = StringFunctionUtils.resolveStringInputType(source.dataType(), functionName());
        if (sourceResolution != TypeResolution.TYPE_RESOLVED) {
            return sourceResolution;
        }
        
        TypeResolution patternResolution = StringFunctionUtils.resolveStringInputType(pattern.dataType(), functionName());
        if (patternResolution != TypeResolution.TYPE_RESOLVED) {
            return patternResolution;
        }
        
        return StringFunctionUtils.resolveStringInputType(replacement.dataType(), functionName());
    }

    @Override
    protected ProcessorDefinition makeProcessorDefinition() {
        return new ReplaceFunctionProcessorDefinition(location(), this,
                ProcessorDefinitions.toProcessorDefinition(source),
                ProcessorDefinitions.toProcessorDefinition(pattern),
                ProcessorDefinitions.toProcessorDefinition(replacement));
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, Replace::new, source, pattern, replacement);
    }

    @Override
    public ScriptTemplate asScript() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DataType dataType() {
        return DataType.KEYWORD;
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        if (newChildren.size() != 3) {
            throw new IllegalArgumentException("expected [3] children but received [" + newChildren.size() + "]");
        }

        return new Replace(location(), newChildren.get(0), newChildren.get(1), newChildren.get(2));
    }
}
