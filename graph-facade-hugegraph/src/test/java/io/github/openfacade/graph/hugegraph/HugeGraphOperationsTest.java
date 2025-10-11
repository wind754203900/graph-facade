package io.github.openfacade.graph.hugegraph;

import io.github.openfacade.graph.api.DataType;
import io.github.openfacade.graph.api.GraphException;
import io.github.openfacade.graph.schema.CreateEdgeRequest;
import io.github.openfacade.graph.schema.CreateEdgeSchemaRequest;
import io.github.openfacade.graph.schema.CreateNodeRequest;
import io.github.openfacade.graph.schema.CreateNodeSchemaRequest;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.structure.graph.Vertex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class HugeGraphOperationsTest {

    @Container
    private static final GenericContainer<?> hugeGraphServer = new GenericContainer<>(
            DockerImageName.parse("hugegraph/hugegraph:1.5.0"))
            .withExposedPorts(8080)
            .waitingFor(
                    org.testcontainers.containers.wait.strategy.Wait.forHttp("/versions")
                            .forPort(8080)
                            .withStartupTimeout(java.time.Duration.ofSeconds(60))
            );

    private HugeClient hugeClient;
    private HugeGraphOperations operations;

    @BeforeEach
    void setUp() {
        // The container will automatically wait for the health check before proceeding
        String host = hugeGraphServer.getHost();
        Integer port = hugeGraphServer.getMappedPort(8080);

        // Create HugeGraph client
        String url = "http://" + host + ":" + port;
        hugeClient = HugeClient.builder(url, "hugegraph")
                .configTimeout(20)
                .build();

        operations = new HugeGraphOperations(hugeClient);
    }

    @AfterEach
    void tearDown() {
        if (hugeClient != null) {
            hugeClient.close();
        }
    }

    @Test
    void testCreateNodeSchemaAndNodeSuccess() throws GraphException {
        String nodeSchemaName = "person";
        CreateNodeSchemaRequest req = CreateNodeSchemaRequest.builder()
                .name(nodeSchemaName)
                .propertyKeys(Map.of(
                        "name", DataType.TEXT,
                        "age", DataType.INT))
                .build();
        operations.createNodeSchema(req);


        String nodeId = "alice";
        Map<String, Object> properties = Map.of(
                "name", "Alice",
                "age", 30);

        CreateNodeRequest createNodeRequest = CreateNodeRequest.builder()
                .nodeId(nodeId)
                .nodeSchema(nodeSchemaName)
                .properties(properties)
                .build();

        operations.createNode(createNodeRequest);

        Vertex createdVertex = hugeClient.graph().getVertex(nodeId);
        assertNotNull(createdVertex);
        assertEquals("person", createdVertex.label());
        assertEquals("Alice", createdVertex.property("name"));
        assertEquals(30, createdVertex.property("age"));
    }

    @Test
    void testCreateEdgeSchemaAndEdgeSuccess() throws GraphException {
        // First create node schemas and nodes that will be connected by the edge
        String personSchemaName = "person";
        CreateNodeSchemaRequest personSchemaReq = CreateNodeSchemaRequest.createNodeSchemaRequest(personSchemaName,
                Map.of("name", DataType.TEXT, "age", DataType.INT));
        operations.createNodeSchema(personSchemaReq);

        // Create source node
        String aliceId = "alice";
        Map<String, Object> aliceProperties = Map.of(
                "name", "Alice",
                "age", 30);
        CreateNodeRequest aliceReq = CreateNodeRequest.createNodeRequest(aliceId, personSchemaName, aliceProperties);
        operations.createNode(aliceReq);

        // Create target node
        String bobId = "bob";
        Map<String, Object> bobProperties = Map.of(
                "name", "Bob",
                "age", 25);
        CreateNodeRequest bobReq = CreateNodeRequest.createNodeRequest(bobId, personSchemaName, bobProperties);
        operations.createNode(bobReq);

        // Create edge schema
        String knowsSchemaName = "knows";
        CreateEdgeSchemaRequest edgeSchemaReq = new CreateEdgeSchemaRequest();
        edgeSchemaReq.setName(knowsSchemaName);
        edgeSchemaReq.setSourceNodeSchemaName(personSchemaName);
        edgeSchemaReq.setTargetNodeSchemaName(personSchemaName);
        edgeSchemaReq.setPropertyKeys(Map.of(
                "weight", DataType.DOUBLE));
        operations.createEdgeSchema(edgeSchemaReq);

        // Create edge
        Map<String, Object> edgeProperties = new HashMap<>();
        edgeProperties.put("sourceId", aliceId);
        edgeProperties.put("targetId", bobId);
        edgeProperties.put("weight", 0.8);

        CreateEdgeRequest edgeReq = new CreateEdgeRequest();
        edgeReq.setEdgeName("alice_knows_bob");
        edgeReq.setEdgeSchemaName(knowsSchemaName);
        edgeReq.setProperties(edgeProperties);

        // This should not throw an exception
        operations.createEdge(edgeReq);
    }
}
