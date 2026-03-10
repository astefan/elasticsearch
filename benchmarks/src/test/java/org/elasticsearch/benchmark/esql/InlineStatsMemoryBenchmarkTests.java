/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.benchmark.esql;

import org.elasticsearch.benchmark.esql.InlineStatsMemoryBenchmark.ColumnDef;
import org.elasticsearch.test.ESTestCase;

public class InlineStatsMemoryBenchmarkTests extends ESTestCase {

    public void testMeasureMemoryReturnsPositiveValues() {
        ColumnDef[] columns = { ColumnDef.longCol("count"), ColumnDef.keywordCol("key", 10) };
        long mem100 = InlineStatsMemoryBenchmark.measureMemory(100, columns);
        long mem1000 = InlineStatsMemoryBenchmark.measureMemory(1_000, columns);

        assertTrue("memory for 100 groups must be positive", mem100 > 0);
        assertTrue("memory for 1000 groups must be positive", mem1000 > 0);
        assertTrue("memory must grow with group count", mem1000 > mem100);
    }

    public void testMoreColumnsUseMoreMemory() {
        ColumnDef[] oneAgg = { ColumnDef.longCol("count"), ColumnDef.keywordCol("key", 10) };
        ColumnDef[] threeAggs = {
            ColumnDef.longCol("min"),
            ColumnDef.longCol("max"),
            ColumnDef.doubleCol("avg"),
            ColumnDef.keywordCol("key", 10) };

        long memOne = InlineStatsMemoryBenchmark.measureMemory(10_000, oneAgg);
        long memThree = InlineStatsMemoryBenchmark.measureMemory(10_000, threeAggs);

        assertTrue("more aggregation columns should use more memory", memThree > memOne);
    }

    public void testPrintMemoryTable() {
        InlineStatsMemoryBenchmark.main(new String[0]);
    }

    public void testLongerKeywordsUseMoreMemory() {
        ColumnDef[] short10 = { ColumnDef.doubleCol("avg"), ColumnDef.keywordCol("key", 10) };
        ColumnDef[] long100 = { ColumnDef.doubleCol("avg"), ColumnDef.keywordCol("key", 100) };

        long memShort = InlineStatsMemoryBenchmark.measureMemory(10_000, short10);
        long memLong = InlineStatsMemoryBenchmark.measureMemory(10_000, long100);

        assertTrue("longer keyword values should use more memory", memLong > memShort);
    }
}
