/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.sql.plugin;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestResponseListener;
import org.elasticsearch.xpack.sql.action.AbstractSqlQueryRequest.Field;
import org.elasticsearch.xpack.sql.action.SqlQueryAction;
import org.elasticsearch.xpack.sql.action.SqlQueryRequest;
import org.elasticsearch.xpack.sql.action.SqlQueryResponse;
import org.elasticsearch.xpack.sql.proto.Mode;
import org.elasticsearch.xpack.sql.proto.Protocol;
import org.elasticsearch.xpack.sql.proto.RequestInfo;
import org.elasticsearch.xpack.sql.session.Cursor;
import org.elasticsearch.xpack.sql.session.Cursors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.xpack.sql.proto.RequestInfo.CANVAS;
import static org.elasticsearch.xpack.sql.proto.RequestInfo.CLI;

public class RestSqlQueryAction extends BaseRestHandler {

    private static final DeprecationLogger deprecationLogger = new DeprecationLogger(LogManager.getLogger(RestSqlQueryAction.class));
    private static final ParseField MODE = new ParseField("mode");
    //private static final ParseField CLIENT_ID = new ParseField("client.id");

    private static String CLIENT_ID = "client.id";

    RestSqlQueryAction(Settings settings, RestController controller) {
        super(settings);
        // TODO: remove deprecated endpoint in 8.0.0
        controller.registerWithDeprecatedHandler(
                GET, Protocol.SQL_QUERY_REST_ENDPOINT, this,
                GET, Protocol.SQL_QUERY_DEPRECATED_REST_ENDPOINT, deprecationLogger);
        // TODO: remove deprecated endpoint in 8.0.0
        controller.registerWithDeprecatedHandler(
                POST, Protocol.SQL_QUERY_REST_ENDPOINT, this,
                POST, Protocol.SQL_QUERY_DEPRECATED_REST_ENDPOINT, deprecationLogger);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        SqlQueryRequest sqlRequest;
        try (XContentParser parser = request.contentOrSourceParamParser()) {
            String clientId = request.param(CLIENT_ID);
            if (clientId != null) {
                clientId = clientId.toLowerCase(Locale.ROOT);
                if (!clientId.equals(CLI) && !clientId.equals(CANVAS)) {
                    clientId = null;
                }
            }
            
            sqlRequest = SqlQueryRequest.fromXContent(parser,
                    new RequestInfo(Mode.fromString(request.param("mode")), clientId));
        }
        /*String mode = null;
        String clientId = null;
        try (XContentParser parser = request.contentParser()) {
            parser.nextToken();

            String currentFieldName = null;
            XContentParser.Token token;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else if (token == XContentParser.Token.VALUE_STRING) {
                    if (Boolean.FALSE ==
                           (Field.QUERY.match(currentFieldName, parser.getDeprecationHandler()) ||
                            Field.TIME_ZONE.match(currentFieldName, parser.getDeprecationHandler()) ||
                            Field.REQUEST_TIMEOUT.match(currentFieldName, parser.getDeprecationHandler()) ||
                            Field.PAGE_TIMEOUT.match(currentFieldName, parser.getDeprecationHandler()) ||
                            //Field.CURSOR.match(currentFieldName, parser.getDeprecationHandler()) ||
                            Field.MODE.match(currentFieldName, parser.getDeprecationHandler()) ||
                            Field.CLIENT_ID.match(currentFieldName, parser.getDeprecationHandler()))) 
                    {
                        throw new ElasticsearchParseException("could not parse SQL request. Unexpected text field [{}]",
                                currentFieldName);
                    } else if(Field.MODE.match(currentFieldName, parser.getDeprecationHandler())) {
                        mode = parser.text();
                    } else if(Field.CLIENT_ID.match(currentFieldName, parser.getDeprecationHandler())) {
                        clientId = parser.text();
                    }
                } else if (token == XContentParser.Token.VALUE_NUMBER) {
                    if (Boolean.FALSE == Field.FETCH_SIZE.match(currentFieldName, parser.getDeprecationHandler())) {
                        throw new ElasticsearchParseException("could not parse SQL request. Unexpected numeric field [{}]",
                                currentFieldName);
                    }
                } else if (token == XContentParser.Token.START_ARRAY) {
                    if (Boolean.FALSE == Field.PARAMS.match(currentFieldName, parser.getDeprecationHandler())) {
                        throw new ElasticsearchParseException("could not parse SQL request. Unexpected array field [{}]",
                                currentFieldName);
                    }
                    
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {}
                } else {
                    throw new ElasticsearchParseException("could not parse SQL request. Unexpected token [{}]", token);
                }
            }
            
            if (clientId != null) {
                clientId = clientId.toLowerCase(Locale.ROOT);
                if (!clientId.equals(CLI) && !clientId.equals(CANVAS)) {
                    clientId = null;
                }
            }
            
            sqlRequest = SqlQueryRequest.fromXContent(parser,
                    new RequestInfo(Mode.fromString(mode), clientId));
        }*/

        /*
         * Since we support {@link TextFormat} <strong>and</strong>
         * {@link XContent} outputs we can't use {@link RestToXContentListener}
         * like everything else. We want to stick as closely as possible to
         * Elasticsearch's defaults though, while still layering in ways to
         * control the output more easilly.
         *
         * First we find the string that the user used to specify the response
         * format. If there is a {@code format} paramter we use that. If there
         * isn't but there is a {@code Accept} header then we use that. If there
         * isn't then we use the {@code Content-Type} header which is required.
         */
        String accept = request.param("format");
        if (accept == null) {
            accept = request.header("Accept");
            if ("*/*".equals(accept)) {
                // */* means "I don't care" which we should treat like not specifying the header
                accept = null;
            }
        }
        if (accept == null) {
            accept = request.header("Content-Type");
        }
        assert accept != null : "The Content-Type header is required";

        /*
         * Second, we pick the actual content type to use by first parsing the
         * string from the previous step as an {@linkplain XContent} value. If
         * that doesn't parse we parse it as a {@linkplain TextFormat} value. If
         * that doesn't parse it'll throw an {@link IllegalArgumentException}
         * which we turn into a 400 error.
         */
        XContentType xContentType = accept == null ? XContentType.JSON : XContentType.fromMediaTypeOrFormat(accept);
        if (xContentType != null) {
            return channel -> client.execute(SqlQueryAction.INSTANCE, sqlRequest, new RestResponseListener<SqlQueryResponse>(channel) {
                @Override
                public RestResponse buildResponse(SqlQueryResponse response) throws Exception {
                    XContentBuilder builder = XContentBuilder.builder(xContentType.xContent());
                    response.toXContent(builder, request);
                    return new BytesRestResponse(RestStatus.OK, builder);
                }
            });
        }

        TextFormat textFormat = TextFormat.fromMediaTypeOrFormat(accept);

        long startNanos = System.nanoTime();
        return channel -> client.execute(SqlQueryAction.INSTANCE, sqlRequest, new RestResponseListener<SqlQueryResponse>(channel) {
            @Override
            public RestResponse buildResponse(SqlQueryResponse response) throws Exception {
                Cursor cursor = Cursors.decodeFromString(sqlRequest.cursor());
                final String data = textFormat.format(cursor, request, response);

                RestResponse restResponse = new BytesRestResponse(RestStatus.OK, textFormat.contentType(request),
                        data.getBytes(StandardCharsets.UTF_8));

                Cursor responseCursor = textFormat.wrapCursor(cursor, response);

                if (responseCursor != Cursor.EMPTY) {
                    restResponse.addHeader("Cursor", Cursors.encodeToString(Version.CURRENT, responseCursor));
                }
                restResponse.addHeader("Took-nanos", Long.toString(System.nanoTime() - startNanos));

                return restResponse;
            }
        });
    }

    @Override
    public String getName() {
        return "sql_query";
    }

}
