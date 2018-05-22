/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.string;

abstract class StringFunctionUtils {
    
    static String trimTrailingWhitespaces(String s) {
        if (!hasLength(s)) {
            return s;
        }
        
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
    
    static String trimLeadingWhitespaces(String s) {
        if (!hasLength(s)) {
            return s;
        }
        
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(0))) {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    private static boolean hasLength(String s) {
        return (s != null && s.length() > 0);
    }
}
