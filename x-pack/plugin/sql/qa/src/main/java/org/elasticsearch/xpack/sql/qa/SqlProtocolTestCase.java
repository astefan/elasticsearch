/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.sql.qa;

import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xpack.sql.proto.Mode;

import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.sql.qa.rest.RestSqlTestCase.runSql;

public abstract class SqlProtocolTestCase extends ESRestTestCase {

    public void testBooleanType() throws IOException {
        assertQuery("SELECT TRUE", "true", "boolean", true, Types.BOOLEAN, 1);
    }
    
    public void testNumericTypes() throws IOException {
        assertQuery("SELECT CAST(3 AS TINYINT)", "CAST(3 AS TINYINT)", "byte", 3, Types.TINYINT, 5);
        assertQuery("SELECT CAST(5 AS SMALLINT)", "CAST(5 AS SMALLINT)", "short", 5, Types.SMALLINT, 6);
        assertQuery("SELECT 123", "123", "integer", 123, Types.INTEGER, 11);
        assertQuery("SELECT 1234567890123", "1234567890123", "long", 1234567890123L, Types.BIGINT, 20);
        assertQuery("SELECT 1234567890123.34", "1.23456789012334E12", "double", 1234567890123.34, Types.DOUBLE, 25);
        assertQuery("SELECT CAST(1234.345 AS REAL)", "CAST(1234.345 AS REAL)", "float", 1234.345, Types.REAL, 15);
        assertQuery("SELECT CAST(1234567890123.34 AS FLOAT)", "CAST(1234567890123.34 AS FLOAT)", "half_float", 1234567890123.34,
                Types.FLOAT, 25);
    }
    
    public void testTextualType() throws IOException {
        assertQuery("SELECT abc123", "abc123", "keyword", "abc123", Types.VARCHAR, 0);
    }
    
    private void assertQuery(String sql, String columnName, String columnType, Object columnValue, int jdbcType, int displaySize) 
            throws IOException {
        assertQuery(sql, columnName, columnType, columnValue, jdbcType, displaySize, false);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void assertQuery(String sql, String columnName, String columnType, Object columnValue, int jdbcType, int displaySize,
            boolean debug) throws IOException {
        for (Mode mode : Mode.values()) {
            if (debug) {
                logger.info(mode);
            }
            Map<String, Object> response = runSql(mode.toString(), sql);
            List columns = (ArrayList) response.get("columns");
            assertEquals(1, columns.size());
            Map<String, Object> column = (HashMap<String, Object>) columns.get(0);
            assertEquals(mode != Mode.PLAIN ? 4 : 2, column.size());
            assertEquals(columnName, column.get("name"));
            assertEquals(columnType, column.get("type"));
            if (mode != Mode.PLAIN) {
                assertEquals(jdbcType, column.get("jdbc_type"));
                assertEquals(displaySize, column.get("display_size"));
            }
            
            List rows = (ArrayList) response.get("rows");
            assertEquals(1, rows.size());
            List row = (ArrayList) rows.get(0);
            assertEquals(1, row.size());
            assertEquals(columnValue, row.get(0));
        }
    }
}
