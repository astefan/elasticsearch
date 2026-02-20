/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.qa.single_node;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.test.TestClustersThreadFilter;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.xpack.esql.generator.Column;
import org.elasticsearch.xpack.esql.generator.QueryExecuted;
import org.elasticsearch.xpack.esql.generator.command.CommandGenerator;
import org.elasticsearch.xpack.esql.generator.command.CommandGenerator.CommandDescription;
import org.elasticsearch.xpack.esql.generator.command.CommandGenerator.ValidationResult;
import org.elasticsearch.xpack.esql.generator.command.pipe.ChangePointGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.DissectGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.DropAllGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.DropGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.EnrichGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.EvalGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.ForkGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.GrokGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.InlineStatsGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.KeepGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.LimitGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.LookupJoinGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.MvExpandGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.RenameGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.SampleGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.SortGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.StatsGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.UriPartsGenerator;
import org.elasticsearch.xpack.esql.generator.command.pipe.WhereGenerator;
import org.elasticsearch.xpack.esql.generator.command.source.FromGenerator;
import org.elasticsearch.xpack.esql.qa.rest.generative.GenerativeRestTest;
import org.junit.ClassRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.elasticsearch.xpack.esql.generator.command.source.FromGenerator.SET_UNMAPPED_FIELDS_PREFIX;
import static org.elasticsearch.xpack.esql.generator.command.source.FromGenerator.UNMAPPED_FIELDS_ENABLED;

/**
 * Replay test for debugging generative testing failures. Paste a failing query into {@link #QUERY}
 * and run this test to replay it command-by-command through the same execution and validation
 * infrastructure used by {@link GenerativeIT}.
 * <p>
 * The test splits the query on pipe boundaries, executes each prefix incrementally, and runs the
 * same {@code checkResults} / {@code checkException} logic from {@link GenerativeRestTest}. This
 * helps identify which pipe command causes the failure and whether the error is correctly caught
 * by the allowed-error handling.
 * <p>
 * To use:
 * <ol>
 *   <li>Paste the failing query into {@link #QUERY}</li>
 *   <li>Run: {@code gradlew :x-pack:plugin:esql:qa:server:single-node:javaRestTest --tests "*.GenerativeReplayIT"}</li>
 *   <li>Check the log output for schema changes and errors at each pipeline step</li>
 * </ol>
 */
@ThreadLeakFilters(filters = TestClustersThreadFilter.class)
public class GenerativeReplayIT extends GenerativeRestTest {

    private static final Logger log = LogManager.getLogger(GenerativeReplayIT.class);

    @ClassRule
    public static ElasticsearchCluster cluster = Clusters.testCluster();

    /**
     * Paste the full query to replay here.
     */
    private static final String QUERY = "SET unmapped_fields=\"nullify\"; from airports_web " +
        "| enrich languages_policy on abbrev " +
        "| where unmapped_field_foo IS NULL AND scalerank <= -86 AND  NOT name LIKE \"???\" " +
        "OR scalerank IN (3, 59, 93) OR language_name LIKE \"*test*\"";

    private static final Map<String, CommandGenerator> GENERATORS_BY_NAME = Map.ofEntries(
        Map.entry("from", FromGenerator.INSTANCE),
        Map.entry(ChangePointGenerator.CHANGE_POINT, ChangePointGenerator.INSTANCE),
        Map.entry(DissectGenerator.DISSECT, DissectGenerator.INSTANCE),
        Map.entry(DropGenerator.DROP, DropGenerator.INSTANCE),
        Map.entry(DropAllGenerator.DROP_ALL, DropAllGenerator.INSTANCE),
        Map.entry(EnrichGenerator.ENRICH, EnrichGenerator.INSTANCE),
        Map.entry(EvalGenerator.EVAL, EvalGenerator.INSTANCE),
        Map.entry(ForkGenerator.FORK, ForkGenerator.INSTANCE),
        Map.entry(GrokGenerator.GROK, GrokGenerator.INSTANCE),
        Map.entry(InlineStatsGenerator.INLINE_STATS, InlineStatsGenerator.INSTANCE),
        Map.entry(KeepGenerator.KEEP, KeepGenerator.INSTANCE),
        Map.entry(LimitGenerator.LIMIT, LimitGenerator.INSTANCE),
        Map.entry(LookupJoinGenerator.LOOKUP_JOIN, LookupJoinGenerator.INSTANCE),
        Map.entry(MvExpandGenerator.MV_EXPAND, MvExpandGenerator.INSTANCE),
        Map.entry(RenameGenerator.RENAME, RenameGenerator.INSTANCE),
        Map.entry(SampleGenerator.SAMPLE, SampleGenerator.INSTANCE),
        Map.entry(SortGenerator.SORT, SortGenerator.INSTANCE),
        Map.entry(StatsGenerator.STATS, StatsGenerator.INSTANCE),
        Map.entry(UriPartsGenerator.URI_PARTS, UriPartsGenerator.INSTANCE),
        Map.entry(WhereGenerator.WHERE, WhereGenerator.INSTANCE)
    );

    private static final Pattern COMMAND_NAME_PATTERN = Pattern.compile(
        "(?i)^\\s*(?:SET\\s+[^;]+;\\s*)?(?:\\|\\s*)?(from|where|eval|keep|drop|sort|limit|stats|rename|enrich|grok|dissect"
            + "|mv_expand|fork|sample|lookup\\s+join|inline\\s+stats|change_point|uri_parts)\\b"
    );

    /**
     * Extracts the command name from a pipe segment string.
     */
    static String parseCommandName(String segment) {
        Matcher m = COMMAND_NAME_PATTERN.matcher(segment);
        if (m.find()) {
            return m.group(1).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        }
        return "unknown";
    }

    /**
     * Looks up the generator for a command name. Falls back to a pass-through generator
     * that always returns {@link CommandGenerator#VALIDATION_OK}.
     */
    private static CommandGenerator generatorFor(String commandName) {
        CommandGenerator gen = GENERATORS_BY_NAME.get(commandName);
        return gen != null ? gen : PASSTHROUGH_GENERATOR;
    }

    private static final CommandGenerator PASSTHROUGH_GENERATOR = new CommandGenerator() {
        @Override
        public CommandDescription generate(
            List<CommandDescription> previousCommands,
            List<Column> previousOutput,
            QuerySchema schema,
            org.elasticsearch.xpack.esql.generator.QueryExecutor executor
        ) {
            return EMPTY_DESCRIPTION;
        }

        @Override
        public ValidationResult validateOutput(
            List<CommandDescription> previousCommands,
            CommandDescription command,
            List<Column> previousColumns,
            List<List<Object>> previousOutput,
            List<Column> columns,
            List<List<Object>> output
        ) {
            return VALIDATION_OK;
        }
    };

    /**
     * Splits a query into pipe-separated segments, respecting parentheses nesting
     * so that pipes inside FORK branches or subqueries are not treated as command separators.
     */
    static List<String> splitPipeSegments(String query) {
        List<String> segments = new ArrayList<>();
        int depth = 0;
        int start = 0;

        String prefix = "";
        Matcher setPrefixMatcher = Pattern.compile("^(SET\\s+[^;]+;)\\s*", Pattern.CASE_INSENSITIVE).matcher(query);
        if (setPrefixMatcher.find()) {
            prefix = setPrefixMatcher.group(1);
            query = query.substring(setPrefixMatcher.end());
        }

        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '|' && depth == 0) {
                segments.add(query.substring(start, i).trim());
                start = i + 1;
            }
        }
        String last = query.substring(start).trim();
        if (last.isEmpty() == false) {
            segments.add(last);
        }

        if (prefix.isEmpty() == false && segments.isEmpty() == false) {
            segments.set(0, prefix + segments.get(0));
        }
        return segments;
    }

    @Override
    public void test() {
        if (QUERY.isBlank()) {
            log.info("QUERY is blank, nothing to replay");
            return;
        }

        List<String> segments = splitPipeSegments(QUERY);
        log.info("=== Replaying query with {} pipe segment(s) ===", segments.size());
        for (int i = 0; i < segments.size(); i++) {
            log.info("  segment[{}]: {}", i, segments.get(i));
        }

        StringBuilder queryBuilder = new StringBuilder();
        QueryExecuted previousResult = null;
        List<CommandDescription> previousCommands = new ArrayList<>();

        for (int step = 0; step < segments.size(); step++) {
            String segment = segments.get(step);
            String pipeSegment = step == 0 ? segment : " | " + segment;
            String commandName = parseCommandName(segment);
            CommandGenerator generator = generatorFor(commandName);

            queryBuilder.append(step == 0 ? segment : " | " + segment);
            String currentQuery = queryBuilder.toString();

            log.info("--- Step {} [{}] ---", step, commandName);
            log.info("Query: {}", currentQuery);

            QueryExecuted result = execute(currentQuery, step);

            if (result.exception() != null) {
                log.error("EXCEPTION at step {}: {}", step, result.exception().getMessage());
                checkException(result);
                log.info("Exception was accepted by allowed-error handling");
                break;
            }

            CommandDescription desc;
            Map<String, Object> context;
            if (generator instanceof FromGenerator) {
                if (segment.startsWith(SET_UNMAPPED_FIELDS_PREFIX)) {
                    context = Map.of(UNMAPPED_FIELDS_ENABLED, true);
                } else {
                    context = new HashMap<>();
                }
            } else {
                context = new HashMap<>();
            }
            desc = new CommandDescription(commandName, generator, pipeSegment, context);
            ValidationResult validation = null;

            try {
                validation = checkResults(previousCommands, generator, desc, previousResult, result);
            } catch (AssertionError ae) {
                log.warn("Validation failed at step {} with AssertionError: {}", step, ae.getMessage());
            }

            List<Column> schema = result.outputSchema();
            int rowCount = result.result() != null ? result.result().size() : 0;
            log.info(
                "Schema ({}): {}",
                schema != null ? schema.size() : 0,
                schema != null ? schema.stream().map(c -> c.name() + ":" + c.type()).collect(Collectors.joining(", ")) : "null"
            );
            log.info("Rows: {}", rowCount);

            if (previousResult != null && schema != null) {
                List<String> prevCols = previousResult.outputSchema().stream().map(Column::name).toList();
                List<String> currCols = schema.stream().map(Column::name).toList();
                List<String> added = currCols.stream().filter(c -> prevCols.contains(c) == false).toList();
                List<String> removed = prevCols.stream().filter(c -> currCols.contains(c) == false).toList();
                if (added.isEmpty() == false) {
                    log.info("  Columns added: {}", added);
                }
                if (removed.isEmpty() == false) {
                    log.info("  Columns removed: {}", removed);
                }
            }

            previousCommands.add(desc);
            previousResult = result;

            if (validation != null && validation.success() == false) {
                log.warn("Validation failed at step {} but was accepted: {}", step, validation.errorMessage());
                break;
            }
        }

        log.info("=== Replay completed ===");
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    @Override
    protected boolean supportsSourceFieldMapping() {
        return cluster.getNumNodes() == 1;
    }
}
