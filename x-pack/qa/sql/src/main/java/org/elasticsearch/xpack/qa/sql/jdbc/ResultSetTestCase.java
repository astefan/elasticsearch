/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.qa.sql.jdbc;

import org.elasticsearch.client.Request;
import org.elasticsearch.common.CheckedConsumer;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class ResultSetTestCase extends JdbcIntegrationTestCase {
    
    private static final Set<String> fieldsNames = Stream.of("test_byte", "test_integer", "test_long", "test_short", "test_double",
            "test_float", "test_half_float", "test_scaled_float", "test_keyword")
            .collect(Collectors.toCollection(HashSet::new));
    
    public void testGettingValidByteWithoutCasting() throws Exception {
        byte random1 = randomByte();
        byte random2 = randomValueOtherThan(random1, () -> randomByte());
        createTestDataForByteValueTests(random1, random2);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_byte, test_null_byte FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(2, resultSetMetaData.getColumnCount());
                    assertEquals(Types.TINYINT, resultSetMetaData.getColumnType(1));
                    assertEquals(Types.TINYINT, resultSetMetaData.getColumnType(2));
                    assertEquals(random1, results.getByte(1));
                    assertEquals(random1, results.getByte("test_byte"));
                    assertTrue(results.getObject(1) instanceof Byte);
                    
                    assertEquals(0, results.getByte(2));
                    assertTrue(results.wasNull());
                    assertEquals(null, results.getObject("test_null_byte"));
                    assertTrue(results.wasNull());
                    
                    assertTrue(results.next());
                    assertEquals(random2, results.getByte(1));
                    assertEquals(random2, results.getByte("test_byte"));
                    assertTrue(results.getObject(1) instanceof Byte);
                    assertEquals((byte) 123, results.getByte(2));
                    
                    assertTrue(results.next());
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidByteWithCasting() throws Exception {
        byte random1 = randomByte();
        byte random2 = randomValueOtherThan(random1, () -> randomByte());
        createTestDataForByteValueTests(random1, random2);
        //Map<String,Byte> map = createTestDataForValueTypesExcept(() -> randomByte(), "byte");
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    assertEquals(random1, results.getInt("test_byte"));
                    assertEquals(random1, results.getLong("test_byte"));
                    assertEquals(random1, results.getShort("test_byte"));
                    assertEquals(Byte.toString(random1), results.getString("test_byte"));
                    assertTrue(results.getObject("test_byte") instanceof Byte);
                    
                    assertTrue(results.next());
                    assertTrue(results.next());
                    assertEquals(true, results.getBoolean("test_byte"));
                    assertEquals(false, results.getBoolean("test_null_byte"));
                    assertEquals(Byte.MIN_VALUE, results.getByte("test_integer"));
                    assertEquals(Byte.MAX_VALUE, results.getByte("test_long"));
                    assertEquals(Byte.MIN_VALUE + 1, results.getByte("test_short"));
                    assertEquals(Byte.MAX_VALUE - 1, results.getByte("test_double"));
                    assertEquals(Byte.MIN_VALUE + 2, results.getByte("test_float"));
                    assertEquals(Byte.MAX_VALUE - 2, results.getByte("test_half_float"));
                    assertEquals(Byte.MIN_VALUE + 3, results.getByte("test_scaled_float"));
                    assertEquals(Byte.MIN_VALUE + 3, results.getByte("test_scaled_float"));
                    assertEquals(123, results.getByte("test_string"));
                    /*map.entrySet().stream().forEach(entry -> {
                        System.out.println(entry.getKey());
                        try {
                            assertEquals((Byte) entry.getValue(), (Byte) results.getByte(entry.getKey()));
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });*/

                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingInvalidByte() throws Exception {
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_integer").field("type", "integer").endObject();
            builder.startObject("test_long").field("type", "long").endObject();
            builder.startObject("test_short").field("type", "short").endObject();
            builder.startObject("test_double").field("type", "double").endObject();
            builder.startObject("test_float").field("type", "float").endObject();
            builder.startObject("test_half_float").field("type", "half_float").endObject();
            builder.startObject("test_scaled_float").field("type", "scaled_float").field("scaling_factor", 1).endObject();
            builder.startObject("test_string").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_integer", Integer.MAX_VALUE);
            builder.field("test_long", Long.MIN_VALUE);
            builder.field("test_short", Short.MAX_VALUE);
            builder.field("test_double", Double.MAX_VALUE);
            builder.field("test_float", Float.MAX_VALUE);
            builder.field("test_half_float", 345.67f);
            builder.field("test_scaled_float", Float.MAX_VALUE);
            builder.field("test_string", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getByte("test_integer"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Long.toString(Integer.MAX_VALUE)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_long"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Long.toString(Long.MIN_VALUE)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_double"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(Double.MAX_VALUE)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_float"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(Float.MAX_VALUE)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_half_float"));
                    assertEquals("Numeric 346 out of range", sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_scaled_float"));
                    assertEquals(format(Locale.ROOT, "Numeric 9223372036854775807 out of range"), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_string"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to a byte", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Byte] not supported", sqle.getMessage());
                }
            }
        }
    }
    
    public void testGettingTimestamp() throws Exception {
        long randomMillis = randomLongBetween(0, System.currentTimeMillis());

        index("library", "1", builder -> {
            builder.field("name", "Don Quixote");
            builder.field("page_count", 1072);
            builder.timeField("release_date", new Date(randomMillis));
            builder.timeField("republish_date", null);
        });
        index("library", "2", builder -> {
            builder.field("name", "1984");
            builder.field("page_count", 328);
            builder.timeField("release_date", new Date(-649036800000L));
            builder.timeField("republish_date", new Date(599616000000L));
        });

        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT name, release_date, republish_date FROM library")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
                    assertEquals(randomMillis, results.getTimestamp("release_date").getTime());
                    assertEquals(randomMillis, results.getTimestamp(2).getTime());
                    assertTrue(results.getObject(2) instanceof Timestamp);
                    assertEquals(randomMillis, ((Timestamp) results.getObject("release_date")).getTime());
                    
                    assertNull(results.getTimestamp(3));
                    assertNull(results.getObject("republish_date"));

                    assertTrue(results.next());
                    assertEquals(599616000000L, results.getTimestamp("republish_date").getTime());
                    assertEquals(-649036800000L, ((Timestamp) results.getObject(2)).getTime());

                    assertFalse(results.next());
                }
            }
        }
    }

    /*
     * Checks StackOverflowError fix for https://github.com/elastic/elasticsearch/pull/31735
     */
    public void testNoInfiniteRecursiveGetObjectCalls() throws SQLException, IOException {
        index("library", "1", builder -> {
            builder.field("name", "Don Quixote");
            builder.field("page_count", 1072);
        });
        Connection conn = esJdbc();
        PreparedStatement statement = conn.prepareStatement("SELECT * FROM library");
        ResultSet results = statement.executeQuery();

        try {
            results.next();
            results.getObject("name");
            results.getObject("page_count");
            results.getObject(1);
            results.getObject(1, String.class);
            results.getObject("page_count", Integer.class);
        } catch (StackOverflowError soe) {
            fail("Infinite recursive call on getObject() method");
        }
    }
    
    private void createIndex(String index) throws Exception {
        Request request = new Request("PUT", "/" + index);
        XContentBuilder createIndex = JsonXContent.contentBuilder().startObject();
        createIndex.startObject("settings");
        {
            createIndex.field("number_of_shards", 1);
            createIndex.field("number_of_replicas", 1);
        }
        createIndex.endObject();
        createIndex.startObject("mappings");
        {
            createIndex.startObject("doc");
            {
                createIndex.startObject("properties");
                {}
                createIndex.endObject();
            }
            createIndex.endObject();
        }
        createIndex.endObject().endObject();
        request.setJsonEntity(Strings.toString(createIndex));
        client().performRequest(request);
    }
    
    private void updateMapping(String index, CheckedConsumer<XContentBuilder, IOException> body) throws Exception {
        Request request = new Request("PUT", "/" + index + "/_mapping/doc");
        XContentBuilder updateMapping = JsonXContent.contentBuilder().startObject();
        updateMapping.startObject("properties");
        {
            body.accept(updateMapping);
        }
        updateMapping.endObject().endObject();
        
        request.setJsonEntity(Strings.toString(updateMapping));
        client().performRequest(request);
    }
    
    private void createTestDataForByteValueTests(byte random1, byte random2) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_byte").field("type", "byte").endObject();
            builder.startObject("test_null_byte").field("type", "byte").endObject();
            builder.startObject("test_integer").field("type", "integer").endObject();
            builder.startObject("test_long").field("type", "long").endObject();
            builder.startObject("test_short").field("type", "short").endObject();
            builder.startObject("test_double").field("type", "double").endObject();
            builder.startObject("test_float").field("type", "float").endObject();
            builder.startObject("test_half_float").field("type", "half_float").endObject();
            builder.startObject("test_scaled_float").field("type", "scaled_float").field("scaling_factor", 1).endObject();
            builder.startObject("test_string").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_byte", random1);
            builder.field("test_null_byte", (Byte) null);
        });
        index("test", "2", builder -> {
            builder.field("test_byte", random2);
            builder.field("test_null_byte", (byte) 123);
        });
        index("test", "3", builder -> {
            builder.field("test_byte", (byte) 1);
            builder.field("test_null_byte", (byte) 0);
            builder.field("test_integer", Byte.MIN_VALUE);
            builder.field("test_long", Byte.MAX_VALUE);
            builder.field("test_short", Byte.MIN_VALUE + 1);
            builder.field("test_double", Byte.MAX_VALUE - 1);
            builder.field("test_float", Byte.MIN_VALUE + 2);
            builder.field("test_half_float", Byte.MAX_VALUE - 2);
            builder.field("test_scaled_float", Byte.MIN_VALUE + 3);
            builder.field("test_string", "123");
        });
    }

    private <T> Map<String,T> createTestDataForValueTypesExcept(Supplier<T> randomGenerator, String excepted) throws Exception, IOException {
        Map<String,T> map = new HashMap<String,T>();
        createIndex("test");
        updateMapping("test", builder -> {
            this.fieldsNames.stream().filter(field -> field != "test_scaled_float").forEach((field) -> {
                try {
                    builder.startObject(field).field("type", field.substring(5)).endObject();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            // scaled_float requires another attribute, so we treat it differently
            builder.startObject("test_scaled_float").field("type", "scaled_float").field("scaling_factor", 1).endObject();
        });
        
        T random1 = randomGenerator.get();
        
        index("test", "1", builder -> {
            map.put("test_" + excepted, random1);
            
            builder.field("test_" + excepted, randomGenerator.get());
            builder.field("test_null_" + excepted, (T) null);
        });
        index("test", "2", builder -> {
            this.fieldsNames.stream().forEach((field) -> {
                T random = randomValueOtherThanMany(map::containsValue, randomGenerator);
                try {
                    builder.field(field, randomGenerator.get());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                map.put(field, random);
            });
        });
        return map;
    }
    
    private long randomMillisSinceEpoch() {
        return randomLongBetween(0, System.currentTimeMillis());
    }
}
