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
import java.io.InputStream;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class ResultSetTestCase extends JdbcIntegrationTestCase {
    
    private static final Set<String> fieldsNames = Stream.of("test_byte", "test_integer", "test_long", "test_short", "test_double",
            "test_float", "test_keyword")
            .collect(Collectors.toCollection(HashSet::new));
    
    // Byte values testing
    public void testGettingValidByteWithoutCasting() throws Exception {
        byte random1 = randomByte();
        byte random2 = randomValueOtherThan(random1, () -> randomByte());
        byte random3 = randomValueOtherThanMany(Arrays.asList(random1, random2)::contains, () -> randomByte());
        
        createTestDataForByteValueTests(random1, random2, random3);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_byte, test_null_byte, test_keyword FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
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
                    assertEquals(random3, results.getByte("test_keyword"));
                    
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidByteWithCasting() throws Exception {
        Map<String,Number> map = createTestDataForNumericValueTypes(() -> randomByte());
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    for(Entry<String, Number> entry : map.entrySet()) {
                        if (entry.getValue() instanceof Double) {
                            assertEquals(Math.round(entry.getValue().doubleValue()), results.getByte(entry.getKey()));
                        } else if (entry.getValue() instanceof Float) {
                            assertEquals(Math.round(entry.getValue().floatValue()), results.getByte(entry.getKey()));
                        } else {
                            assertEquals(entry.getValue().byteValue(), results.getByte(entry.getKey()));
                        }
                    }
                }
            }
        }
    }

    public void testGettingInvalidByte() throws Exception {
        createIndex("test");
        updateMappingForNumericValuesTests("test");
        updateMapping("test", builder -> {
            builder.startObject("test_keyword").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        int intNotByte = randomIntBetween(Byte.MAX_VALUE + 1, Integer.MAX_VALUE);
        long longNotByte = randomLongBetween(Byte.MAX_VALUE + 1, Long.MAX_VALUE);
        short shortNotByte = (short) randomIntBetween(Byte.MAX_VALUE + 1, Short.MAX_VALUE);
        double doubleNotByte = (double) randomDoubleBetween(Byte.MAX_VALUE + 1, Double.MAX_VALUE, true);
        float floatNotByte = (float) randomFloatBetween(Byte.MAX_VALUE + 1, Float.MAX_VALUE);
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);
                
        index("test", "1", builder -> {
            builder.field("test_integer", intNotByte);
            builder.field("test_long", longNotByte);
            builder.field("test_short", shortNotByte);
            builder.field("test_double", doubleNotByte);
            builder.field("test_float", floatNotByte);
            builder.field("test_keyword", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getByte("test_integer"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", intNotByte), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_short"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", shortNotByte), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_long"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Long.toString(longNotByte)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_double"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(doubleNotByte)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_float"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(floatNotByte)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_keyword"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to a Byte", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getByte("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Byte] not supported", sqle.getMessage());
                }
            }
        }
    }
    
    // Short values testing
    public void testGettingValidShortWithoutCasting() throws Exception {
        short random1 = randomShort();
        short random2 = randomValueOtherThan(random1, () -> randomShort());
        short random3 = randomValueOtherThanMany(Arrays.asList(random1, random2)::contains, () -> randomShort());
        
        createTestDataForShortValueTests(random1, random2, random3);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_short, test_null_short, test_keyword FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
                    assertEquals(Types.SMALLINT, resultSetMetaData.getColumnType(1));
                    assertEquals(Types.SMALLINT, resultSetMetaData.getColumnType(2));
                    assertEquals(random1, results.getShort(1));
                    assertEquals(random1, results.getShort("test_short"));
                    assertTrue(results.getObject(1) instanceof Short);
                    
                    assertEquals(0, results.getShort(2));
                    assertTrue(results.wasNull());
                    assertEquals(null, results.getObject("test_null_short"));
                    assertTrue(results.wasNull());
                    
                    assertTrue(results.next());
                    assertEquals(random2, results.getShort(1));
                    assertEquals(random2, results.getShort("test_short"));
                    assertTrue(results.getObject(1) instanceof Short);
                    assertEquals(random3, results.getShort("test_keyword"));
                    
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidShortWithCasting() throws Exception {
        Map<String,Number> map = createTestDataForNumericValueTypes(() -> randomShort());
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    for(Entry<String, Number> entry : map.entrySet()) {
                        if (entry.getValue() instanceof Double) {
                            assertEquals(Math.round(entry.getValue().doubleValue()), results.getShort(entry.getKey()));
                        } else if (entry.getValue() instanceof Float) {
                            assertEquals(Math.round(entry.getValue().floatValue()), results.getShort(entry.getKey()));
                        } else {
                            assertEquals(entry.getValue().shortValue(), results.getShort(entry.getKey()));
                        }
                    }
                }
            }
        }
    }

    public void testGettingInvalidShort() throws Exception {
        createIndex("test");
        updateMappingForNumericValuesTests("test");
        updateMapping("test", builder -> {
            builder.startObject("test_keyword").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        int intNotShort = randomIntBetween(Short.MAX_VALUE + 1, Integer.MAX_VALUE);
        long longNotShort = randomLongBetween(Short.MAX_VALUE + 1, Long.MAX_VALUE);
        double doubleNotShort = (double) randomDoubleBetween(Short.MAX_VALUE + 1, Double.MAX_VALUE, true);
        float floatNotShort = (float) randomFloatBetween(Short.MAX_VALUE + 1, Float.MAX_VALUE);
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);

        index("test", "1", builder -> {
            builder.field("test_integer", intNotShort);
            builder.field("test_long", longNotShort);
            builder.field("test_double", doubleNotShort);
            builder.field("test_float", floatNotShort);
            builder.field("test_keyword", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getShort("test_integer"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", intNotShort), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getShort("test_long"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Long.toString(longNotShort)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getShort("test_double"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(doubleNotShort)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getShort("test_float"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(floatNotShort)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getShort("test_keyword"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to a Short", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getShort("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Short] not supported", sqle.getMessage());
                }
            }
        }
    }
    
    // Integer values testing
    public void testGettingValidIntegerWithoutCasting() throws Exception {
        int random1 = randomInt();
        int random2 = randomValueOtherThan(random1, () -> randomInt());
        int random3 = randomValueOtherThanMany(Arrays.asList(random1, random2)::contains, () -> randomInt());
        
        createTestDataForIntegerValueTests(random1, random2, random3);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_integer, test_null_integer, test_keyword FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
                    assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(1));
                    assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(2));
                    assertEquals(random1, results.getInt(1));
                    assertEquals(random1, results.getInt("test_integer"));
                    assertTrue(results.getObject(1) instanceof Integer);
                    
                    assertEquals(0, results.getInt(2));
                    assertTrue(results.wasNull());
                    assertEquals(null, results.getObject("test_null_integer"));
                    assertTrue(results.wasNull());
                    
                    assertTrue(results.next());
                    assertEquals(random2, results.getInt(1));
                    assertEquals(random2, results.getInt("test_integer"));
                    assertTrue(results.getObject(1) instanceof Integer);
                    assertEquals(random3, results.getInt("test_keyword"));
                    
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidIntegerWithCasting() throws Exception {
        Map<String,Number> map = createTestDataForNumericValueTypes(() -> randomInt());
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    for(Entry<String, Number> entry : map.entrySet()) {
                        if (entry.getValue() instanceof Double) {
                            assertEquals(Math.round(entry.getValue().doubleValue()), results.getInt(entry.getKey()));
                        } else if (entry.getValue() instanceof Float) {
                            assertEquals(Math.round(entry.getValue().floatValue()), results.getInt(entry.getKey()));
                        } else {
                            assertEquals(entry.getValue().intValue(), results.getInt(entry.getKey()));
                        }
                    }
                }
            }
        }
    }

    public void testGettingInvalidInteger() throws Exception {
        createIndex("test");
        updateMappingForNumericValuesTests("test");
        updateMapping("test", builder -> {
            builder.startObject("test_keyword").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        long longNotInt = randomLongBetween(getMaxIntPlusOne(), Long.MAX_VALUE);
        double doubleNotInt = (double) randomDoubleBetween(getMaxIntPlusOne().doubleValue(), Double.MAX_VALUE, true);
        float floatNotInt = (float) randomFloatBetween(getMaxIntPlusOne().floatValue(), Float.MAX_VALUE);
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);

        index("test", "1", builder -> {
            builder.field("test_long", longNotInt);
            builder.field("test_double", doubleNotInt);
            builder.field("test_float", floatNotInt);
            builder.field("test_keyword", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getInt("test_long"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Long.toString(longNotInt)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getInt("test_double"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(doubleNotInt)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getInt("test_float"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(floatNotInt)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getInt("test_keyword"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to an Integer", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getInt("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Integer] not supported", sqle.getMessage());
                }
            }
        }
    }
    
    // Long values testing
    public void testGettingValidLongWithoutCasting() throws Exception {
        long random1 = randomLong();
        long random2 = randomValueOtherThan(random1, () -> randomLong());
        long random3 = randomValueOtherThanMany(Arrays.asList(random1, random2)::contains, () -> randomLong());
        
        createTestDataForLongValueTests(random1, random2, random3);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_long, test_null_long, test_keyword FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
                    assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(1));
                    assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(2));
                    assertEquals(random1, results.getLong(1));
                    assertEquals(random1, results.getLong("test_long"));
                    assertTrue(results.getObject(1) instanceof Long);
                    
                    assertEquals(0, results.getLong(2));
                    assertTrue(results.wasNull());
                    assertEquals(null, results.getObject("test_null_long"));
                    assertTrue(results.wasNull());
                    
                    assertTrue(results.next());
                    assertEquals(random2, results.getLong(1));
                    assertEquals(random2, results.getLong("test_long"));
                    assertTrue(results.getObject(1) instanceof Long);
                    assertEquals(random3, results.getLong("test_keyword"));
                    
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidLongWithCasting() throws Exception {
        Map<String,Number> map = createTestDataForNumericValueTypes(() -> randomLong());
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    for(Entry<String, Number> entry : map.entrySet()) {
                        if (entry.getValue() instanceof Double) {
                            assertEquals(Math.round(entry.getValue().doubleValue()), results.getLong(entry.getKey()));
                        } else if (entry.getValue() instanceof Float) {
                            /*if (entry.getValue().doubleValue() > Long.MAX_VALUE || entry.getValue().doubleValue() < Long.MIN_VALUE) {
                                assertEquals(Math.round(entry.getValue().doubleValue()), results.getLong(entry.getKey()));
                            }*/
                            assertEquals(Math.round(entry.getValue().doubleValue()), results.getLong(entry.getKey()));
                        } else {
                            assertEquals(entry.getValue().longValue(), results.getLong(entry.getKey()));
                        }
                    }
                }
            }
        }
    }

    public void testGettingInvalidLong() throws Exception {
        createIndex("test");
        updateMappingForNumericValuesTests("test");
        updateMapping("test", builder -> {
            builder.startObject("test_keyword").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        double doubleNotLong = (double) randomDoubleBetween(getMaxLongPlusOne().doubleValue(), Double.MAX_VALUE, true);
        float floatNotLong = (float) randomFloatBetween(getMaxLongPlusOne().floatValue(), Float.MAX_VALUE);
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);

        index("test", "1", builder -> {
            builder.field("test_double", doubleNotLong);
            builder.field("test_float", floatNotLong);
            builder.field("test_keyword", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getLong("test_double"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(doubleNotLong)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getLong("test_float"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(floatNotLong)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getLong("test_keyword"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to a Long", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getLong("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Long] not supported", sqle.getMessage());
                }
            }
        }
    }
    
    // Double values testing
    public void testGettingValidDoubleWithoutCasting() throws Exception {
        double random1 = randomDouble();
        double random2 = randomValueOtherThan(random1, () -> randomDouble());
        double random3 = randomValueOtherThanMany(Arrays.asList(random1, random2)::contains, () -> randomDouble());
        
        createTestDataForDoubleValueTests(random1, random2, random3);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_double, test_null_double, test_keyword FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
                    assertEquals(Types.DOUBLE, resultSetMetaData.getColumnType(1));
                    assertEquals(Types.DOUBLE, resultSetMetaData.getColumnType(2));
                    assertEquals(random1, results.getDouble(1), 0.0d);
                    assertEquals(random1, results.getDouble("test_double"), 0.0d);
                    assertTrue(results.getObject(1) instanceof Double);
                    
                    assertEquals(0, results.getDouble(2), 0.0d);
                    assertTrue(results.wasNull());
                    assertEquals(null, results.getObject("test_null_double"));
                    assertTrue(results.wasNull());
                    
                    assertTrue(results.next());
                    assertEquals(random2, results.getDouble(1), 0.0d);
                    assertEquals(random2, results.getDouble("test_double"), 0.0d);
                    assertTrue(results.getObject(1) instanceof Double);
                    assertEquals(random3, results.getDouble("test_keyword"), 0.0d);
                    
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidDoubleWithCasting() throws Exception {
        Map<String,Number> map = createTestDataForNumericValueTypes(() -> randomDouble());
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    for(Entry<String, Number> entry : map.entrySet()) {
                        assertEquals(entry.getValue().doubleValue(), results.getDouble(entry.getKey()), 0.0d);
                    }
                }
            }
        }
    }

    public void testGettingInvalidDouble() throws Exception {
        createIndex("test");
        updateMappingForNumericValuesTests("test");
        updateMapping("test", builder -> {
            builder.startObject("test_keyword").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);

        index("test", "1", builder -> {
            builder.field("test_keyword", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getDouble("test_keyword"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to a Double", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getDouble("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Double] not supported", sqle.getMessage());
                }
            }
        }
    }
    
    // Float values testing
    public void testGettingValidFloatWithoutCasting() throws Exception {
        float random1 = randomFloat();
        float random2 = randomValueOtherThan(random1, () -> randomFloat());
        float random3 = randomValueOtherThanMany(Arrays.asList(random1, random2)::contains, () -> randomFloat());
        
        createTestDataForFloatValueTests(random1, random2, random3);
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT test_float, test_null_float, test_keyword FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    ResultSetMetaData resultSetMetaData = results.getMetaData();

                    results.next();
                    assertEquals(3, resultSetMetaData.getColumnCount());
                    assertEquals(Types.REAL, resultSetMetaData.getColumnType(1));
                    assertEquals(Types.REAL, resultSetMetaData.getColumnType(2));
                    assertEquals(random1, results.getFloat(1), 0.0f);
                    assertEquals(random1, results.getFloat("test_float"), 0.0f);
                    assertTrue(results.getObject(1) instanceof Float);
                    
                    assertEquals(0, results.getFloat(2), 0.0d);
                    assertTrue(results.wasNull());
                    assertEquals(null, results.getObject("test_null_float"));
                    assertTrue(results.wasNull());
                    
                    assertTrue(results.next());
                    assertEquals(random2, results.getFloat(1), 0.0d);
                    assertEquals(random2, results.getFloat("test_float"), 0.0d);
                    assertTrue(results.getObject(1) instanceof Float);
                    assertEquals(random3, results.getFloat("test_keyword"), 0.0d);
                    
                    assertFalse(results.next());
                }
            }
        }
    }
    
    public void testGettingValidFloatWithCasting() throws Exception {
        Map<String,Number> map = createTestDataForNumericValueTypes(() -> randomFloat());
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    for(Entry<String, Number> entry : map.entrySet()) {
                        assertEquals(entry.getValue().floatValue(), results.getFloat(entry.getKey()), 0.0f);
                    }
                }
            }
        }
    }

    public void testGettingInvalidFloat() throws Exception {
        createIndex("test");
        updateMappingForNumericValuesTests("test");
        updateMapping("test", builder -> {
            builder.startObject("test_keyword").field("type", "keyword").endObject();
            builder.startObject("test_date").field("type", "date").endObject();
        });
        
        String randomString = randomUnicodeOfCodepointLengthBetween(128, 256);
        double doubleNotFloat = (double) randomDoubleBetween(Float.MAX_VALUE + 1, Double.MAX_VALUE, true);

        index("test", "1", builder -> {
            builder.field("test_double", doubleNotFloat);
            builder.field("test_keyword", randomString);
            builder.field("test_date", new Date(randomMillisSinceEpoch()));
        });
        
        try (Connection connection = esJdbc()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM test")) {
                try (ResultSet results = statement.executeQuery()) {
                    results.next();
                    
                    SQLException sqle = expectThrows(SQLException.class, () -> results.getLong("test_double"));
                    assertEquals(format(Locale.ROOT, "Numeric %s out of range", Double.toString(doubleNotFloat)), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getFloat("test_keyword"));
                    assertEquals(format(Locale.ROOT, "Unable to convert value [%.128s] to a Float", randomString), sqle.getMessage());
                    
                    sqle = expectThrows(SQLException.class, () -> results.getFloat("test_date"));
                    assertEquals("Conversion from type [TIMESTAMP] to [Float] not supported", sqle.getMessage());
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
    
    public void testUnsupportedGetMethods() throws IOException, SQLException {
        index("test", "1", builder -> {
            builder.field("test", "test");
        });
        Connection conn = esJdbc();
        PreparedStatement statement = conn.prepareStatement("SELECT * FROM test");
        ResultSet r = statement.executeQuery();
        
        r.next();
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getAsciiStream("test"), "AsciiStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getAsciiStream(1), "AsciiStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getArray("test"), "Array not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getArray(1), "Array not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBigDecimal("test"), "BigDecimal not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBigDecimal(1,1), "BigDecimal not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBigDecimal("test"), "BigDecimal not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBigDecimal(1,1), "BigDecimal not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBinaryStream("test"), "BinaryStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBinaryStream(1), "BinaryStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBlob("test"), "Blob not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getBlob(1), "Blob not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getCharacterStream("test"), "CharacterStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getCharacterStream(1), "CharacterStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getClob("test"), "Clob not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getClob(1), "Clob not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getNCharacterStream("test"), "NCharacterStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getNCharacterStream(1), "NCharacterStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getNClob("test"), "NClob not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getNClob(1), "NClob not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getNString("test"), "NString not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getNString(1), "NString not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getRef("test"), "Ref not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getRef(1), "Ref not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getRowId("test"), "RowId not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getRowId(1), "RowId not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getSQLXML("test"), "SQLXML not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getSQLXML(1), "SQLXML not supported");        
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getUnicodeStream("test"), "UnicodeStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getUnicodeStream(1), "UnicodeStream not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getURL("test"), "URL not supported");
        assertThrowsUnsupportedAndExpectErrorMessage(() -> r.getURL(1), "URL not supported");
    }
    
    private void assertThrowsUnsupportedAndExpectErrorMessage(ThrowingRunnable runnable, String message) {
        SQLException sqle = expectThrows(SQLFeatureNotSupportedException.class, runnable);
        assertEquals(message, sqle.getMessage());
    }
    
    public void testUnsupportedUpdateMethods() throws IOException, SQLException {
        index("test", "1", builder -> {
            builder.field("test", "test");
        });
        Connection conn = esJdbc();
        PreparedStatement statement = conn.prepareStatement("SELECT * FROM test");
        ResultSet r = statement.executeQuery();
        
        r.next();
        Blob b = null;
        InputStream i = null;
        Clob c = null;
        NClob nc = null;
        Reader rd = null;
        
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBytes(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBytes("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateArray(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateArray("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateAsciiStream(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateAsciiStream("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateAsciiStream(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateAsciiStream(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateAsciiStream("", null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateAsciiStream("", null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBigDecimal(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBigDecimal("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBinaryStream(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBinaryStream("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBinaryStream(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBinaryStream(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBinaryStream("", null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBinaryStream("", null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBlob(1, b));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBlob(1, i));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBlob("", b));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBlob("", i));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBlob(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBlob("", null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBoolean(1, false));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateBoolean("", false));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateByte(1, (byte) 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateByte("", (byte) 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateCharacterStream(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateCharacterStream("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateCharacterStream(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateCharacterStream(1, null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateCharacterStream("", null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateCharacterStream("", null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateClob(1, c));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateClob(1, rd));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateClob("", c));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateClob("", rd));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateClob(1, null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateClob("", null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateDate(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateDate("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateDouble(1, 0d));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateDouble("", 0d));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateFloat(1, 0f));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateFloat("", 0f));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateInt(1, 0));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateInt("", 0));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateLong(1, 0L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateLong("", 0L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNCharacterStream(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNCharacterStream("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNCharacterStream(1, null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNCharacterStream("", null, 1L));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNClob(1, nc));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNClob(1, rd));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNClob("", nc));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNClob("", rd));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNClob(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNClob("", null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNString(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNString("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNull(1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateNull(""));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateObject(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateObject("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateObject(1, null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateObject("", null, 1));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateRef(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateRef("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateRow());
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateRowId(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateRowId("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateSQLXML(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateSQLXML("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateShort(1, (short) 0));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateShort("", (short) 0));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateString(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateString("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateTime(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateTime("", null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateTimestamp(1, null));
        assertThrowsWritesUnsupportedForUpdate(() -> r.updateTimestamp("", null));
    }
    
    private void assertThrowsWritesUnsupportedForUpdate(ThrowingRunnable r) {
        assertThrowsUnsupportedAndExpectErrorMessage(r, "Writes not supported");
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
    
    private void createTestDataForByteValueTests(byte random1, byte random2, byte random3) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_byte").field("type", "byte").endObject();
            builder.startObject("test_null_byte").field("type", "byte").endObject();
            builder.startObject("test_keyword").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_byte", random1);
            builder.field("test_null_byte", (Byte) null);
        });
        index("test", "2", builder -> {
            builder.field("test_byte", random2);
            builder.field("test_keyword", random3);
        });
    }
    
    private void createTestDataForShortValueTests(short random1, short random2, short random3) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_short").field("type", "short").endObject();
            builder.startObject("test_null_short").field("type", "short").endObject();
            builder.startObject("test_keyword").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_short", random1);
            builder.field("test_null_short", (Short) null);
        });
        index("test", "2", builder -> {
            builder.field("test_short", random2);
            builder.field("test_keyword", random3);
        });
    }
    
    private void createTestDataForIntegerValueTests(int random1, int random2, int random3) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_integer").field("type", "integer").endObject();
            builder.startObject("test_null_integer").field("type", "integer").endObject();
            builder.startObject("test_keyword").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_integer", random1);
            builder.field("test_null_integer", (Integer) null);
        });
        index("test", "2", builder -> {
            builder.field("test_integer", random2);
            builder.field("test_keyword", random3);
        });
    }
    
    private void createTestDataForLongValueTests(long random1, long random2, long random3) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_long").field("type", "long").endObject();
            builder.startObject("test_null_long").field("type", "long").endObject();
            builder.startObject("test_keyword").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_long", random1);
            builder.field("test_null_long", (Long) null);
        });
        index("test", "2", builder -> {
            builder.field("test_long", random2);
            builder.field("test_keyword", random3);
        });
    }
    
    private void createTestDataForDoubleValueTests(double random1, double random2, double random3) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_double").field("type", "double").endObject();
            builder.startObject("test_null_double").field("type", "double").endObject();
            builder.startObject("test_keyword").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_double", random1);
            builder.field("test_null_double", (Double) null);
        });
        index("test", "2", builder -> {
            builder.field("test_double", random2);
            builder.field("test_keyword", random3);
        });
    }
    
    private void createTestDataForFloatValueTests(float random1, float random2, float random3) throws Exception, IOException {
        createIndex("test");
        updateMapping("test", builder -> {
            builder.startObject("test_float").field("type", "float").endObject();
            builder.startObject("test_null_float").field("type", "float").endObject();
            builder.startObject("test_keyword").field("type", "keyword").endObject();
        });
        
        index("test", "1", builder -> {
            builder.field("test_float", random1);
            builder.field("test_null_float", (Double) null);
        });
        index("test", "2", builder -> {
            builder.field("test_float", random2);
            builder.field("test_keyword", random3);
        });
    }

    /**
     * Creates test data for all numeric get* methods. All values random and different from the other numeric fields already generated.
     * It returns a map containing the field name and its randomly generated value to be later used in checking the returned values.
     */
    private Map<String,Number> createTestDataForNumericValueTypes(Supplier<Number> randomGenerator) throws Exception, IOException {
        Map<String,Number> map = new HashMap<String,Number>();
        createIndex("test");
        updateMappingForNumericValuesTests("test");

        index("test", "1", builder -> {
            // random Byte
            byte test_byte = randomValueOtherThanMany(map::containsValue, randomGenerator).byteValue();
            builder.field("test_byte", test_byte);
            map.put("test_byte", test_byte);
            
            // random Integer
            int test_integer = randomValueOtherThanMany(map::containsValue, randomGenerator).intValue();
            builder.field("test_integer", test_integer);
            map.put("test_integer", test_integer);

            // random Short
            int test_short = randomValueOtherThanMany(map::containsValue, randomGenerator).shortValue();
            builder.field("test_short", test_short);
            map.put("test_short", test_short);
            
            // random Long
            long test_long = randomValueOtherThanMany(map::containsValue, randomGenerator).longValue();
            builder.field("test_long", test_long);
            map.put("test_long", test_long);
            
            // random Double
            double test_double = randomValueOtherThanMany(map::containsValue, randomGenerator).doubleValue();
            builder.field("test_double", test_double);
            map.put("test_double", test_double);
            
            // random Float
            float test_float = randomValueOtherThanMany(map::containsValue, randomGenerator).floatValue();
            builder.field("test_float", test_float);
            map.put("test_float", test_float);
        });
        return map;
    }

    private void updateMappingForNumericValuesTests(String indexName) throws Exception {
        updateMapping(indexName, builder -> {
            for(String field : fieldsNames) {
                builder.startObject(field).field("type", field.substring(5)).endObject();
            }
        });
    }
    
    private long randomMillisSinceEpoch() {
        return randomLongBetween(0, System.currentTimeMillis());
    }
    
    private float randomFloatBetween(float start, float end) {
        float result = 0.0f;
        while (result < start || result > end || Float.isNaN(result)) {
            result = start + randomFloat() * (end - start);
        }
        
        return result;
    }
    
    private Long getMaxIntPlusOne() {
        return Long.valueOf(Integer.MAX_VALUE) + 1L;
    }
    
    private Double getMaxLongPlusOne() {
        return Double.valueOf(Long.MAX_VALUE) + 1d;
    }
}
