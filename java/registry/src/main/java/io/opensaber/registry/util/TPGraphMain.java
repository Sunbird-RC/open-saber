package io.opensaber.registry.util;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.sink.DatabaseProvider;

public class TPGraphMain {
    private static Graph graph;

    private DatabaseProvider dbProvider;

    private OpenSaberInstrumentation watch = new OpenSaberInstrumentation(true);

    public TPGraphMain(DatabaseProvider db) {
        dbProvider = db;
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
        vertex.property(label + "id", edgeId);
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

        watch.start("Get Graph");
        graph = dbProvider.getGraphStore();
        watch.stop("End Graph");

        watch.start("Start Transaction");
        Transaction tx = graph.tx();
        processNode(null, createParentVertex(), rootNode);
        tx.commit();
        watch.stop("End Transaction");

        tx.createThreadedTx();

//        watch.start("Close transaction");
//        try {
//            tx.close();
//        } catch (Exception e) {
//
//        }
//        watch.stop("End close Graph");


        Instant endTime = Instant.now();
        System.out.println(
                Duration.between(startTime, endTime).toNanos() + "," +
                Duration.between(startTime, endTime).toMillis() + "," +
                Duration.between(startTime, endTime).toMinutes());
    }
}
