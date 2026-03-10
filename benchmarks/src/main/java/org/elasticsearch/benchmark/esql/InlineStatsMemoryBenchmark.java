/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.benchmark.esql;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.breaker.NoopCircuitBreaker;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BlockFactory;
import org.elasticsearch.compute.data.BytesRefBlock;
import org.elasticsearch.compute.data.DoubleBlock;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.Page;

import java.util.Locale;
import java.util.Random;

/**
 * Measures the memory footprint of intermediate LocalRelation results produced by INLINE STATS.
 *
 * INLINE STATS executes an aggregation sub-plan and materializes the results as a LocalRelation
 * held in memory on the coordinating node. The size of this relation is bounded by
 * {@code esql.intermediate_local_relation_max_size} (default 0.1% of heap).
 *
 * This utility builds blocks that simulate typical aggregation outputs and reports their memory
 * consumption against several reference heap sizes.
 */
public class InlineStatsMemoryBenchmark {

    private static final BlockFactory BLOCK_FACTORY = BlockFactory.builder(BigArrays.NON_RECYCLING_INSTANCE)
        .breaker(new NoopCircuitBreaker("bench"))
        .build();

    private static final long[] REFERENCE_HEAPS_BYTES = { gb(4), gb(8), gb(16), gb(32), gb(64) };

    private static long gb(long n) {
        return n * 1024 * 1024 * 1024;
    }

    enum ColType {
        LONG,
        DOUBLE,
        KEYWORD
    }

    record ColumnDef(String name, ColType type, int keywordLength) {
        static ColumnDef longCol(String name) {
            return new ColumnDef(name, ColType.LONG, 0);
        }

        static ColumnDef doubleCol(String name) {
            return new ColumnDef(name, ColType.DOUBLE, 0);
        }

        static ColumnDef keywordCol(String name, int avgLength) {
            return new ColumnDef(name, ColType.KEYWORD, avgLength);
        }
    }

    record Scenario(String description, String sampleQuery, ColumnDef[] columns) {}

    private static final Scenario[] SCENARIOS = {
        new Scenario(
            "COUNT(*) BY keyword(10)",
            "FROM logs | INLINE STATS count = COUNT(*) BY status",
            new ColumnDef[] { ColumnDef.longCol("count"), ColumnDef.keywordCol("key", 10) }
        ),
        new Scenario(
            "AVG(x) BY keyword(10)",
            "FROM employees | INLINE STATS avg_salary = AVG(salary) BY department",
            new ColumnDef[] { ColumnDef.doubleCol("avg"), ColumnDef.keywordCol("key", 10) }
        ),
        new Scenario(
            "MIN+MAX+AVG BY keyword(10)",
            "FROM orders | INLINE STATS min_price = MIN(price), max_price = MAX(price), avg_price = AVG(price) BY category",
            new ColumnDef[] {
                ColumnDef.longCol("min"),
                ColumnDef.longCol("max"),
                ColumnDef.doubleCol("avg"),
                ColumnDef.keywordCol("key", 10) }
        ),
        new Scenario(
            "COUNT(*) BY keyword(10), keyword(10)",
            "FROM logs | INLINE STATS count = COUNT(*) BY region, status",
            new ColumnDef[] { ColumnDef.longCol("count"), ColumnDef.keywordCol("key1", 10), ColumnDef.keywordCol("key2", 10) }
        ),
        new Scenario(
            "COUNT(*) BY long",
            "FROM events | INLINE STATS count = COUNT(*) BY year",
            new ColumnDef[] { ColumnDef.longCol("count"), ColumnDef.longCol("key") }
        ),
        new Scenario(
            "AVG(x) BY keyword(50)",
            "FROM products | INLINE STATS avg_rating = AVG(rating) BY product_name",
            new ColumnDef[] { ColumnDef.doubleCol("avg"), ColumnDef.keywordCol("key", 50) }
        ),
        new Scenario(
            "AVG(x) BY keyword(100)",
            "FROM documents | INLINE STATS avg_score = AVG(score) BY url",
            new ColumnDef[] { ColumnDef.doubleCol("avg"), ColumnDef.keywordCol("key", 100) }
        ),
        new Scenario(
            "COUNT+AVG BY 3 keywords(10)",
            "FROM logs | INLINE STATS count = COUNT(*), avg_duration = AVG(duration) BY host, region, service",
            new ColumnDef[] {
                ColumnDef.longCol("count"),
                ColumnDef.doubleCol("avg"),
                ColumnDef.keywordCol("key1", 10),
                ColumnDef.keywordCol("key2", 10),
                ColumnDef.keywordCol("key3", 10) }
        ),
        new Scenario(
            "COUNT(*) BY 3 keywords(20)",
            "FROM logs | INLINE STATS count = COUNT(*) BY hostname, datacenter, environment",
            new ColumnDef[] {
                ColumnDef.longCol("count"),
                ColumnDef.keywordCol("key1", 20),
                ColumnDef.keywordCol("key2", 20),
                ColumnDef.keywordCol("key3", 20) }
        ), };

    private static final int[] GROUP_COUNTS = { 100, 1_000, 10_000, 30_000, 100_000, 300_000, 1_000_000 };

    public static void main(String[] args) {
        printLimitTable();
        System.out.println();

        for (Scenario scenario : SCENARIOS) {
            printScenarioTable(scenario);
            System.out.println();
        }
    }

    private static void printLimitTable() {
        System.out.println("=== INLINE STATS memory limit (0.1% of heap) for reference heap sizes ===");
        System.out.println();
        System.out.printf("  %-10s | %s%n", "Heap", "0.1% Limit");
        System.out.printf("  %-10s-+-%s%n", "-".repeat(10), "-".repeat(12));
        for (long heap : REFERENCE_HEAPS_BYTES) {
            System.out.printf("  %-10s | %s%n", ByteSizeValue.ofBytes(heap), ByteSizeValue.ofBytes(heap / 1000));
        }
    }

    private static void printScenarioTable(Scenario scenario) {
        System.out.printf("=== %s ===%n", scenario.description);
        System.out.printf("  e.g. %s%n", scenario.sampleQuery);
        System.out.println();

        StringBuilder header = new StringBuilder();
        header.append(String.format("  %18s | %12s", "Groups cardinality", "Memory used"));
        for (long heap : REFERENCE_HEAPS_BYTES) {
            header.append(String.format(" | %s", padCenter("Heap " + ByteSizeValue.ofBytes(heap), 10)));
        }
        System.out.println(header);

        StringBuilder separator = new StringBuilder();
        separator.append(String.format("  %s-+-%s", "-".repeat(18), "-".repeat(12)));
        for (int i = 0; i < REFERENCE_HEAPS_BYTES.length; i++) {
            separator.append("-+-").append("-".repeat(10));
        }
        System.out.println(separator);

        for (int numGroups : GROUP_COUNTS) {
            long memBytes = measureMemory(numGroups, scenario.columns);

            StringBuilder row = new StringBuilder();
            row.append(String.format("  %18s | %12s", formatGroupCount(numGroups), ByteSizeValue.ofBytes(memBytes)));
            for (long heap : REFERENCE_HEAPS_BYTES) {
                long limit = heap / 1000;
                double pct = (memBytes * 100.0) / limit;
                String cell;
                if (pct > 100) {
                    cell = String.format(Locale.ROOT, "%.0f%% OVER", pct);
                } else {
                    cell = String.format(Locale.ROOT, "%.1f%%", pct);
                }
                row.append(" | ").append(padCenter(cell, 10));
            }
            System.out.println(row);
        }
    }

    static long measureMemory(int numGroups, ColumnDef[] columns) {
        Block[] blocks = new Block[columns.length];
        try {
            Random random = new Random(42);
            for (int i = 0; i < columns.length; i++) {
                blocks[i] = buildBlock(numGroups, columns[i], random);
            }
            Page page = new Page(blocks);
            return page.ramBytesUsedByBlocks();
        } finally {
            for (Block block : blocks) {
                if (block != null) {
                    block.close();
                }
            }
        }
    }

    private static Block buildBlock(int numRows, ColumnDef col, Random random) {
        return switch (col.type) {
            case LONG -> {
                try (LongBlock.Builder builder = BLOCK_FACTORY.newLongBlockBuilder(numRows)) {
                    for (int i = 0; i < numRows; i++) {
                        builder.appendLong(random.nextLong());
                    }
                    yield builder.build();
                }
            }
            case DOUBLE -> {
                try (DoubleBlock.Builder builder = BLOCK_FACTORY.newDoubleBlockBuilder(numRows)) {
                    for (int i = 0; i < numRows; i++) {
                        builder.appendDouble(random.nextDouble());
                    }
                    yield builder.build();
                }
            }
            case KEYWORD -> {
                try (BytesRefBlock.Builder builder = BLOCK_FACTORY.newBytesRefBlockBuilder(numRows)) {
                    for (int i = 0; i < numRows; i++) {
                        builder.appendBytesRef(new BytesRef(randomString(random, col.keywordLength)));
                    }
                    yield builder.build();
                }
            }
        };
    }

    private static String randomString(Random random, int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) ('a' + random.nextInt(26));
        }
        return new String(chars);
    }

    private static String formatGroupCount(int n) {
        if (n >= 1_000_000) return n / 1_000_000 + "M";
        if (n >= 1_000) return n / 1_000 + "K";
        return Integer.toString(n);
    }

    private static String padCenter(String s, int width) {
        if (s.length() >= width) return s;
        int left = (width - s.length()) / 2;
        int right = width - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }
}
