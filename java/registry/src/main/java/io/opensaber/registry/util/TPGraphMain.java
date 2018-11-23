package io.opensaber.registry.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JElementIdProvider;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JGraph;
import com.steelbridgelabs.oss.neo4j.structure.providers.Neo4JNativeElementIdProvider;
import io.opensaber.registry.sink.DatabaseProvider;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.h2.engine.Database;
import org.neo4j.driver.v1.AuthToken;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.umlg.sqlg.structure.SqlgGraph;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class TPGraphMain {
    private static Graph graph;

    public TPGraphMain(DatabaseProvider db) {
        graph = db.getGraphStore();
    }

    public static String createLabel() {
        return UUID.randomUUID().toString();
    }

    public static void createVertex(Graph graph, String label, Vertex parentVertex, JsonNode jsonObject) {
        Vertex vertex = graph.addVertex(label);
        jsonObject.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            if (entryValue.isValueNode()) {
                vertex.property(entry.getKey(), entryValue.asText());
            } else if (entryValue.isObject()) {
                createVertex(graph, entry.getKey(), vertex, entryValue);
            }
        });
        Edge e = addEdge(graph, label, parentVertex, vertex);
        String edgeId = UUID.randomUUID().toString();
        vertex.property("intId", edgeId);
        parentVertex.property(vertex.label(), edgeId);
    }

    public static Edge addEdge(Graph graph, String label, Vertex v1, Vertex v2) {
        return v1.addEdge(label, v2);
    }

    public static Vertex createParentVertex() {
        String personsStr = "Persons";
        String personsId = "ParentEntity_Persons";
        GraphTraversalSource gtRootTraversal = graph.traversal();
        GraphTraversal<Vertex, Vertex> rootVertex = gtRootTraversal.V().hasLabel(personsStr);
        Vertex parentVertex = null;
        if (!rootVertex.hasNext()) {
            parentVertex = graph.addVertex(personsStr);
            parentVertex.property("id", personsId);
            parentVertex.property("label", personsStr);
        } else {
            parentVertex = rootVertex.next();
        }

        return parentVertex;
    }

    public static List<String> verticesCreated = new ArrayList<String>();

    public static void processNode(String parentName, Vertex parentVertex, JsonNode node) {

        Iterator<Map.Entry<String, JsonNode>> entryIterator = node.fields();
        while (entryIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = entryIterator.next();
            if (entry.getValue().isValueNode()) {
                // Create properties
                System.out.println("Create properties within vertex " + parentName);
                System.out.println(parentName + ":" + entry.getKey() + " --> " + entry.getValue());
                parentVertex.property(entry.getKey(), entry.getValue());
            } else if (entry.getValue().isObject()) {
                createVertex(graph, entry.getKey(), parentVertex, entry.getValue());

            } else if (entry.getValue().isArray()) {
                // TODO
            }
        }
    }

    public static enum DBTYPE {NEO4J, POSTGRES};


    // Expectation
    // Only one Grouping vertex = "teachers"  (plural of your parent vertex)
    // Multiple Parent vertex = teacher
    // Multiple child vertex = address
    // For every parent vertex and child vertex, there is a single Edge between
    //    teacher -> address

    public void createTPGraph(String jsonString) throws IOException {
        Instant startTime = Instant.now();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonString);
        Transaction tx = graph.tx();
        processNode(null, createParentVertex(), rootNode);
        tx.commit();

        Instant endTime = Instant.now();
        System.out.println(
                Duration.between(startTime, endTime).toNanos() + "," +
                Duration.between(startTime, endTime).toMillis() + "," +
                Duration.between(startTime, endTime).toMinutes());
    }
}
