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
 * Returns the starting position of the first occurrence of the pattern within the source string.
 * The search for the first occurrence of the pattern begins with the first character position in the source string
 * unless the optional argument, start, is specified. If start is specified, the search begins with the character 
 * position indicated by the value of start. The first character position in the source string is indicated by the value 1.
 * If the pattern is not found within the source string, the value 0 is returned.
 */
public class Locate extends ScalarFunction {

    private final Expression pattern, source, start;
    
    public Locate(Location location, Expression pattern, Expression source, Expression start) {
        super(location, start != null ? Arrays.asList(pattern, source, start) : Arrays.asList(pattern, source));
        this.pattern = pattern;
        this.source = source;
        this.start = start;
    }
    
    protected TypeResolution resolveType() {
        if (!childrenResolved()) {
            return new TypeResolution("Unresolved children");
        }

        TypeResolution patternResolution = StringFunctionUtils.resolveStringInputType(pattern.dataType(), functionName());
        if (patternResolution != TypeResolution.TYPE_RESOLVED) {
            return patternResolution;
        }
        
        TypeResolution sourceResolution = StringFunctionUtils.resolveStringInputType(source.dataType(), functionName());
        if (sourceResolution != TypeResolution.TYPE_RESOLVED) {
            return sourceResolution;
        }
        
        return start == null ? TypeResolution.TYPE_RESOLVED : StringFunctionUtils.resolveNumericInputType(start.dataType(), functionName());
    }

    @Override
    protected ProcessorDefinition makeProcessorDefinition() {
        LocateFunctionProcessorDefinition processorDefinition;
        if (start == null) {
            processorDefinition = new LocateFunctionProcessorDefinition(location(), this,
                    ProcessorDefinitions.toProcessorDefinition(pattern),
                    ProcessorDefinitions.toProcessorDefinition(source));
        }
        else {
            processorDefinition = new LocateFunctionProcessorDefinition(location(), this,
                    ProcessorDefinitions.toProcessorDefinition(pattern),
                    ProcessorDefinitions.toProcessorDefinition(source),
                    ProcessorDefinitions.toProcessorDefinition(start));
        }
        
        return processorDefinition;
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, Locate::new, pattern, source, start);
    }

    @Override
    public ScriptTemplate asScript() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DataType dataType() {
        return DataType.INTEGER;
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        if (newChildren.size() != 3) {
            throw new IllegalArgumentException("expected [3] children but received [" + newChildren.size() + "]");
        }

        return new Locate(location(), newChildren.get(0), newChildren.get(1), newChildren.get(2));
    }
}
