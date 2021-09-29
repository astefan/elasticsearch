/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.eql.parser;

import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.eql.expression.OptionalUnresolvedAttribute;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.UnresolvedAttribute;
import org.elasticsearch.xpack.ql.tree.Location;
import org.elasticsearch.xpack.ql.tree.Source;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptySet;

public class OptionalFieldsParserTests extends ESTestCase {

    public void testOptionalFieldsInQueries() {
        Set<UnresolvedAttribute> allOptionals = new HashSet<>();
        allOptionals.add(optional(1, 12, "x"));
        allOptionals.add(optional(1, 17, "y"));

        assertOptionalFieldsForQuery("any where ?x == ?y", allOptionals, emptySet());
    }

    private void assertOptionalFieldsForQuery(String eql, Set<UnresolvedAttribute> allOptionals, Set<Expression> keyOptionals) {
        EqlParser parser = new EqlParser();
        parser.createStatement(eql);
        assertEquals(allOptionals.size(), parser.allOptionals().size());
        assertEquals(keyOptionals.size(), parser.keyOptionals().size());
        assertEquals(parser.allOptionals(), allOptionals);
    }

    private OptionalUnresolvedAttribute optional(int line, int col, String name) {
        return new OptionalUnresolvedAttribute(new Source(new Location(line, col), "?" + name), name);
    }
}
