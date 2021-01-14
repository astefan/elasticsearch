package org.elasticsearch.backwards;

import org.apache.http.HttpHost;
import org.elasticsearch.Version;
import org.elasticsearch.backwards.IndexingIT.Node;
import org.elasticsearch.backwards.IndexingIT.Nodes;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.NotEqualMessageBuilder;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;

public class SqlSearchIT extends ESRestTestCase {

    private static String index = "test_sql_mixed_versions";
    private static int numShards;
    private static int numReplicas = 1;
    private static int numDocs;
    private static Nodes nodes;
    private static List<Node> allNodes;
    private static List<Node> newNodes;
    private static List<Node> bwcNodes;
    private static Version bwcVersion;
    private static Version newVersion;
    private static Map<String, Object> expectedResponse;

    @Before
    public void prepareTestData() throws IOException {
        nodes = IndexingIT.buildNodeAndVersions(client());
        numShards = nodes.size();
        numDocs = randomIntBetween(numShards, 16);
        allNodes = new ArrayList<>();
        allNodes.addAll(nodes.getBWCNodes());
        allNodes.addAll(nodes.getNewNodes());
        newNodes = new ArrayList<>();
        newNodes.addAll(nodes.getNewNodes());
        bwcNodes = new ArrayList<>();
        bwcNodes.addAll(nodes.getBWCNodes());
        bwcVersion = nodes.getBWCNodes().get(0).getVersion();
        newVersion = nodes.getNewNodes().get(0).getVersion();

        if (client().performRequest(new Request("HEAD", "/" + index)).getStatusLine().getStatusCode() == 404) {
            expectedResponse = new HashMap<>();
            expectedResponse.put("columns", singletonList(columnInfo("test", "text")));
            List<List<String>> rows = new ArrayList<>(numDocs);

            createIndex(index, Settings.builder()
                .put(IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), numShards)
                .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, numReplicas).build());
            for (int i = 0; i < numDocs; i++) {
                String randomValue = "test_" + randomAlphaOfLength(2);
                Request request = new Request("PUT", index + "/_doc/" + i);
                request.setJsonEntity("{\"test\": \"" + randomValue + "\"}");
                assertOK(client().performRequest(request));
                rows.add(singletonList(randomValue));
            }
            ensureGreen(index);
        }
    }

    public void testMinVersionAsNewVersion() throws Exception {
        try (RestClient client = buildClient(restClientSettings(),
            newNodes.stream().map(Node::getPublishAddress).toArray(HttpHost[]::new))) {
            Request request = new Request("POST", "_sql");
            request.setJsonEntity("{\"query\":\"SELECT * FROM " + index + "\"}");
//            assertBusy(() -> {
//                ResponseException responseException = expectThrows(ResponseException.class, () -> client.performRequest(newVersionRequest));
//                assertThat(responseException.getResponse().getStatusLine().getStatusCode(),
//                    equalTo(RestStatus.INTERNAL_SERVER_ERROR.getStatus()));
//                assertThat(responseException.getMessage(),
//                    containsString("{\"error\":{\"root_cause\":[],\"type\":\"search_phase_execution_exception\""));
//                assertThat(responseException.getMessage(), containsString("caused_by\":{\"type\":\"version_mismatch_exception\","
//                    + "\"reason\":\"One of the shards is incompatible with the required minimum version [" + newVersion + "]\""));
//            });
            assertResponse(expectedResponse, runSql(client, request));
        }
    }

    private Map<String, Object> columnInfo(String name, String type) {
        Map<String, Object> column = new HashMap<>();
        column.put("name", name);
        column.put("type", type);
        return unmodifiableMap(column);
    }

    private void assertResponse(Map<String, Object> expected, Map<String, Object> actual) {
        if (false == expected.equals(actual)) {
            NotEqualMessageBuilder message = new NotEqualMessageBuilder();
            message.compareMaps(actual, expected);
            fail("Response does not match:\n" + message.toString());
        }
    }

    private Map<String, Object> runSql(RestClient client, Request request) throws IOException {
        Response response = client.performRequest(request);
        try (InputStream content = response.getEntity().getContent()) {
            return XContentHelper.convertToMap(JsonXContent.jsonXContent, content, false);
        }
    }
}
