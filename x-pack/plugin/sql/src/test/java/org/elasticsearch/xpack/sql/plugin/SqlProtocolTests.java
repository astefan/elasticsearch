/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.sql.plugin;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.sql.action.SqlQueryRequest;
import org.elasticsearch.xpack.sql.action.SqlQueryResponse;
import org.elasticsearch.xpack.sql.analysis.index.IndexResolver;
import org.elasticsearch.xpack.sql.execution.PlanExecutor;
import org.elasticsearch.xpack.sql.proto.Mode;
import org.elasticsearch.xpack.sql.proto.Protocol;
import org.elasticsearch.xpack.sql.proto.RequestInfo;
import org.elasticsearch.xpack.sql.proto.StringUtils;
import org.junit.Before;

import java.sql.Types;
import java.util.Collections;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;

public class SqlProtocolTests extends ESTestCase {
    
    private Client client;
    private NamedWriteableRegistry registry;
    private IndexResolver indexResolver;
    private PlanExecutor planExecutor;

    @Before
    public void init() throws Exception {
        client = mock(Client.class);
        SearchModule searchModule = new SearchModule(Settings.EMPTY, false, Collections.emptyList());
        registry = new NamedWriteableRegistry(searchModule.getNamedWriteables());
        indexResolver = new IndexResolver(client, "foo");
        planExecutor = new PlanExecutor(client, indexResolver, registry);
    }

    public void testBooleanDataTypeReturn() {
        String query = "SELECT TRUE";
        Mode mode = randomFrom(Mode.values());
        SqlQueryRequest request = request(query, randomFrom(Mode.values()));

        assertStatements(request, Types.BOOLEAN, mode, (response) -> {
            assertEquals("true", response.columns().get(0).name());
            assertEquals("boolean", response.columns().get(0).esType());
        });
    }
    
    private SqlQueryRequest request(String query, Mode mode) {
        return new SqlQueryRequest(query, Collections.emptyList(), null, Protocol.TIME_ZONE, Protocol.FETCH_SIZE,
                Protocol.REQUEST_TIMEOUT, Protocol.PAGE_TIMEOUT, "", new RequestInfo(mode));
    }
    
    private void assertJdbcType(int expected, int actualJdbcType, Mode mode) {
        if (mode == Mode.JDBC) {
            assertEquals(expected, actualJdbcType);
        } else {
            assertNull(actualJdbcType);
        }
    }
    
    private void assertStatements(SqlQueryRequest request, int expectedJdbcType, Mode mode, Consumer<SqlQueryResponse> assertions) {
        ActionListener<SqlQueryResponse> listener = new ActionListener<SqlQueryResponse>() {

            @Override
            public void onResponse(SqlQueryResponse response) {
                logger.info(response);
                assertEquals(1, response.columns().size());
                assertJdbcType(expectedJdbcType, response.columns().get(0).jdbcType(), mode);
                assertions.accept(response);
            }

            @Override
            public void onFailure(Exception e) {
                fail("Shouldn't have reached this step. Exception thrown:" + e);
            }
            
        };
        TransportSqlQueryAction.operation(planExecutor, request, listener, StringUtils.EMPTY, StringUtils.EMPTY);
    }
}
