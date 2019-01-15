/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.sql.qa;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.cbor.CborXContent;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.common.xcontent.smile.SmileXContent;
import org.elasticsearch.common.xcontent.yaml.YamlXContent;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xpack.sql.proto.Mode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.elasticsearch.xpack.sql.qa.rest.RestSqlTestCase.mode;

public abstract class SqlProtocolTestCase extends ESRestTestCase {

    public void testBooleanType() throws IOException {
        assertQuery("SELECT TRUE", "TRUE", "boolean", true, 1);
    }
    
    public void testNumericTypes() throws IOException {
        assertQuery("SELECT CAST(3 AS TINYINT)", "CAST(3 AS TINYINT)", "byte", 3, 5);
        assertQuery("SELECT CAST(-123 AS TINYINT)", "CAST(-123 AS TINYINT)", "byte", -123, 5);
        assertQuery("SELECT CAST(5 AS SMALLINT)", "CAST(5 AS SMALLINT)", "short", 5, 6);
        assertQuery("SELECT CAST(-25 AS SMALLINT)", "CAST(-25 AS SMALLINT)", "short", -25, 6);
        assertQuery("SELECT 123", "123", "integer", 123, 11);
        assertQuery("SELECT -2123", "-2123", "integer", -2123, 11);
        assertQuery("SELECT 1234567890123", "1234567890123", "long", 1234567890123L, 20);
        assertQuery("SELECT -1234567890123", "-1234567890123", "long", -1234567890123L, 20);
        assertQuery("SELECT 1234567890123.34", "1234567890123.34", "double", 1234567890123.34, 25);
        assertQuery("SELECT -1234567890123.34", "-1234567890123.34", "double", -1234567890123.34, 25);
        assertQuery("SELECT CAST(1234.345 AS REAL)", "CAST(1234.345 AS REAL)", "float", 1234.345, 15);
        assertQuery("SELECT CAST(-1234.345 AS REAL)", "CAST(-1234.345 AS REAL)", "float", -1234.345, 15);
        assertQuery("SELECT CAST(1234567890123.34 AS FLOAT)", "CAST(1234567890123.34 AS FLOAT)", "double", 1234567890123.34, 25);
        assertQuery("SELECT CAST(-1234567890123.34 AS FLOAT)", "CAST(-1234567890123.34 AS FLOAT)", "double", -1234567890123.34, 25);
    }
    
    public void testTextualType() throws IOException {
        assertQuery("SELECT 'abc123'", "'abc123'", "keyword", "abc123", 0);
    }
    
    public void testDates() throws IOException {
        assertQuery("SELECT CAST('2019-01-14T12:29:25.000Z' AS DATE)", "CAST('2019-01-14T12:29:25.000Z' AS DATE)", "date",
                "2019-01-14T12:29:25.000Z", 24);
        assertQuery("SELECT CAST(-26853765751000 AS DATE)", "CAST(-26853765751000 AS DATE)", "date", "1119-01-15T12:37:29.000Z", 24);
        assertQuery("SELECT CAST(CAST('-26853765751000' AS BIGINT) AS DATE)", "CAST(CAST('-26853765751000' AS BIGINT) AS DATE)", "date",
                "1119-01-15T12:37:29.000Z", 24);
    }
    
    public void testIPs() throws IOException {
        assertQuery("SELECT CAST('12.13.14.15' AS IP)", "CAST('12.13.14.15' AS IP)", "ip", "12.13.14.15", 0);
        assertQuery("SELECT CAST('2001:0db8:0000:0000:0000:ff00:0042:8329' AS IP)", "CAST('2001:0db8:0000:0000:0000:ff00:0042:8329' AS IP)",
                "ip", "2001:0db8:0000:0000:0000:ff00:0042:8329", 0);
    }
    
    public void testDateTimeIntervals() throws IOException {
        assertQuery("SELECT INTERVAL '326' YEAR", "INTERVAL '326' YEAR", "interval_year", "P326Y", 7);
        assertQuery("SELECT INTERVAL '50' MONTH", "INTERVAL '50' MONTH", "interval_month", "P50M", 7);
        assertQuery("SELECT INTERVAL '520' DAY", "INTERVAL '520' DAY", "interval_day", "PT12480H", 23);
        assertQuery("SELECT INTERVAL '163' HOUR", "INTERVAL '163' HOUR", "interval_hour", "PT163H", 23);
        assertQuery("SELECT INTERVAL '163' MINUTE", "INTERVAL '163' MINUTE", "interval_minute", "PT2H43M", 23);
        assertQuery("SELECT INTERVAL '223.16' SECOND", "INTERVAL '223.16' SECOND", "interval_second", "PT3M43.016S", 23);
        assertQuery("SELECT INTERVAL '163-11' YEAR TO MONTH", "INTERVAL '163-11' YEAR TO MONTH", "interval_year_to_month", "P163Y11M", 7);
        assertQuery("SELECT INTERVAL '163 12' DAY TO HOUR", "INTERVAL '163 12' DAY TO HOUR", "interval_day_to_hour", "PT3924H", 23);
        assertQuery("SELECT INTERVAL '163 12:39' DAY TO MINUTE", "INTERVAL '163 12:39' DAY TO MINUTE", "interval_day_to_minute",
                "PT3924H39M", 23);
        assertQuery("SELECT INTERVAL '163 12:39:59.163' DAY TO SECOND", "INTERVAL '163 12:39:59.163' DAY TO SECOND",
                "interval_day_to_second", "PT3924H39M59.163S", 23);
        assertQuery("SELECT INTERVAL -'163 23:39:56.23' DAY TO SECOND", "INTERVAL -'163 23:39:56.23' DAY TO SECOND",
                "interval_day_to_second", "PT-3935H-39M-56.023S", 23);
        assertQuery("SELECT INTERVAL '163:39' HOUR TO MINUTE", "INTERVAL '163:39' HOUR TO MINUTE", "interval_hour_to_minute",
                "PT163H39M", 23);
        assertQuery("SELECT INTERVAL '163:39:59.163' HOUR TO SECOND", "INTERVAL '163:39:59.163' HOUR TO SECOND", "interval_hour_to_second",
                "PT163H39M59.163S", 23);
        assertQuery("SELECT INTERVAL '163:59.163' MINUTE TO SECOND", "INTERVAL '163:59.163' MINUTE TO SECOND", "interval_minute_to_second",
                "PT2H43M59.163S", 23);
    }
    
    private void assertQuery(String sql, String columnName, String columnType, Object columnValue, int displaySize) 
            throws IOException {
        assertQuery(sql, columnName, columnType, columnValue, displaySize, true);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void assertQuery(String sql, String columnName, String columnType, Object columnValue, int displaySize, boolean debug)
            throws IOException {
        for (Mode mode : Mode.values()) {
            if (debug) {
                logger.info(mode);
            }
            Map<String, Object> response = runSql(mode.toString(), sql);
            if (debug) {
                logger.info(response);
            }
            List columns = (ArrayList) response.get("columns");
            assertEquals(1, columns.size());
            Map<String, Object> column = (HashMap<String, Object>) columns.get(0);
            assertEquals(mode != Mode.PLAIN ? 3 : 2, column.size());
            assertEquals(columnName, column.get("name"));
            assertEquals(columnType, column.get("type"));
            if (mode != Mode.PLAIN) {
                assertEquals(displaySize, column.get("display_size"));
            }
            
            List rows = (ArrayList) response.get("rows");
            assertEquals(1, rows.size());
            List row = (ArrayList) rows.get(0);
            assertEquals(1, row.size());
            assertEquals(columnValue, row.get(0));
        }
    }
    
    private Map<String, Object> runSql(HttpEntity sql) throws IOException {
        Request request = new Request("POST", "/_sql");
        String format = randomFrom(XContentType.values()).name().toLowerCase(Locale.ROOT);
        
        if (randomBoolean()) {
            request.addParameter("error_trace", "true");
        }
        if (randomBoolean()) {
            request.addParameter("pretty", "true");
        }
        if (!"json".equals(format) || randomBoolean()) {
            // since we default to JSON if a format is not specified, randomize setting it
            // for any other format, just set it explicitly
            request.addParameter("format", format);
        }
        if (randomBoolean()) {
            // randomly use the Accept header for the response format
            RequestOptions.Builder options = request.getOptions().toBuilder();
            options.addHeader("Accept", randomFrom("*/*", "application/" + format));
            request.setOptions(options);
        }
        request.setEntity(sql);

        Response response = client().performRequest(request);
        try (InputStream content = response.getEntity().getContent()) {
            switch(format) {
                case "cbor": {
                    return XContentHelper.convertToMap(CborXContent.cborXContent, content, false);
                }
                case "yaml": {
                    return XContentHelper.convertToMap(YamlXContent.yamlXContent, content, false);
                }
                case "smile": {
                    return XContentHelper.convertToMap(SmileXContent.smileXContent, content, false);
                }
                default:
                   return XContentHelper.convertToMap(JsonXContent.jsonXContent, content, false); 
            }
        }
    }

    private Map<String, Object> runSql(String mode, String sql) throws IOException {
        return runSql(new StringEntity("{\"query\":\"" + sql + "\"" + mode(mode) + "}", ContentType.APPLICATION_JSON));
    }
}
