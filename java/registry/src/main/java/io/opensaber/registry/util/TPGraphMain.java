package io.opensaber.registry.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JGraph;

import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.middleware.util.LogMarkers;
import io.opensaber.registry.sink.DatabaseProvider;

public class TPGraphMain {

    private static List<String> uuidList;
    private DatabaseProvider dbProvider;
    private String teacherOsid;

    private Logger logger = LoggerFactory.getLogger(TPGraphMain.class);

    private OpenSaberInstrumentation watch = new OpenSaberInstrumentation(true);

    public TPGraphMain(DatabaseProvider db) {
        dbProvider = db;
        uuidList = new ArrayList<String>();
    }

    public String createLabel() {
        return UUID.randomUUID().toString();
    }

    public void createVertex(Graph graph, String label, Vertex parentVertex, JsonNode jsonObject) {
        Vertex vertex = graph.addVertex(label);
        jsonObject.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            if (entryValue.isValueNode()) {
                vertex.property(entry.getKey(), entryValue.asText());
            } else if (entryValue.isObject()) {
                createVertex(graph, entry.getKey(), vertex, entryValue);
            } else if(entryValue.isArray()){
                entry.getValue().forEach(jsonNode -> {
                    if(jsonNode.isObject()){
                        createVertex(graph, entry.getKey(), vertex, jsonNode);
                    } else {
                        vertex.property(entry.getKey(),entryValue.toString());
                        return;
                    }
                });
            }
        });
        addEdge(graph, label, parentVertex, vertex);
        String edgeId = UUID.randomUUID().toString();
        vertex.property("osid", edgeId);
        parentVertex.property(vertex.label()+ "id", edgeId);
        if(label.equalsIgnoreCase("Teacher")){
            teacherOsid = edgeId;
        }
        System.out.println("osid:"+edgeId);
        uuidList.add(edgeId);
    }

    public Edge addEdge(Graph graph, String label, Vertex v1, Vertex v2) {
        return v1.addEdge(label, v2);
    }

    public static Vertex createParentVertex(Graph graph, String parentLabel) {
    	
        GraphTraversalSource gtRootTraversal = graph.traversal();
        GraphTraversal<Vertex, Vertex> rootVertex = gtRootTraversal.V().hasLabel(parentLabel);
        Vertex parentVertex = null;
        if (!rootVertex.hasNext()) {
            if(graph.features().graph().supportsTransactions()){
                Transaction tx = graph.tx();
                parentVertex = graph.addVertex(parentLabel);
                // TODO: this could be parentVertex.id() after we make our own Neo4jIdProvider
                parentVertex.property("osid", UUID.randomUUID().toString());
                tx.commit();
            } else {
                parentVertex = graph.addVertex(parentLabel);
                // TODO: this could be parentVertex.id() after we make our own Neo4jIdProvider
                parentVertex.property("osid", UUID.randomUUID().toString());
            }
        } else {
            parentVertex = rootVertex.next();
        }

        return parentVertex;
    }

    public void processNode(Graph graph, String parentName, Vertex parentVertex, JsonNode node) throws EncryptionException {

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
                    entry.getValue().forEach(jsonNode -> {
                        System.out.println(jsonNode);
                        if(jsonNode.isObject()){
                            createVertex(graph, entry.getKey(), parentVertex, jsonNode);
                        } else {
                            parentVertex.property(entry.getKey(),jsonNode);
                            return;
                        }
                    });

            }
        }
    }

    /**
     * Retrieves all UUID of a given all labels.
     */
    public static List<String> getUUIDs(List<String> parentLabels, DatabaseProvider dbProvider){
    	List<String> uuids = new ArrayList<>();
    	Graph graph = dbProvider.getGraphStore();
        GraphTraversal<Vertex, Vertex> graphTraversal = graph.traversal().V();
        for(String label: parentLabels){
        	 GraphTraversal<Vertex, Vertex> gvs = graphTraversal.hasLabel(label);
        	 Vertex v = gvs.hasNext()? gvs.next():null;
        	 if(v!=null){
        		 uuids.add(v.property("osid").value().toString());
        	 }       	 
        }      	
    	return uuids;	
    }

    public Map readGraph2Json(String osid) throws IOException, Exception {
        Map map = new HashMap();
        Graph graph = dbProvider.getGraphStore();
        Transaction tx = graph.tx();
        Neo4JGraph neo4JGraph = dbProvider.getRawGraph();
        StatementResult sr = neo4JGraph.execute("match (n) where n.osid='" + osid + "' return n");
        while(sr.hasNext()){
            Record record = sr.single();
            InternalNode internalNode = (InternalNode) record.get("n").asNode();
            String label = internalNode.labels().iterator().next();
            map.put(label,internalNode.asValue().asMap());
            //To find connected nodes of the osid node
            /*if(label.equalsIgnoreCase("Teacher")){
                StatementResult sr1 = neo4JGraph.execute("match (s)-[e]->(v) where ID(s) =" + internalNode.id() + " return v");
                List<Record> record1 = sr1.list();
                record1.forEach(node1 -> {
                    InternalNode connectedNode = (InternalNode)node1.get("v").asNode();
                    String connectedLabel = connectedNode.labels().iterator().next();
                    map1.put(connectedLabel,connectedNode.asValue().asMap());
                });
                System.out.println("done");
                *//*record1.stream().forEach(node1 -> {
                    System.out.println((InternalNode)node1.values());
                });*//*
            }*/
        }
        // StatementResult sr = graph.execute("match (n) where n.osid='532e2c1a-1ed0-4b29-8e44-2d5f6f3d711f' return n");
        // System.out.println(sr.hasNext());
       // GraphTraversal<Vertex, Vertex>  gt = gtRootTraversal.clone().V(679077);
        //GraphTraversal<Vertex, Vertex>  gt = gtRootTraversal.clone().V(679077).hasLabel("Teacher");
        //gt.hasNext();
        tx.commit();
        graph.close();
        return map;
    }

    public static enum DBTYPE {NEO4J, POSTGRES};


    // Expectation
    // Only one Grouping vertex = "teachers"  (plural of your parent vertex)
    // Multiple Parent vertex = teacher
    // Multiple child vertex = address
    // For every parent vertex and child vertex, there is a single Edge between
    //    teacher -> address
    public String createTPGraph(JsonNode rootNode, Vertex parentVertex) throws IOException, EncryptionException, Exception {
        try {
            Graph graph = dbProvider.getGraphStore();

            watch.start("Add Transaction");
            try (Transaction tx = graph.tx()) {
                processNode(graph, "Teacher", parentVertex, rootNode);
                tx.commit();
            }
            new Thread(() -> {
                try {
                    graph.close();
                } catch (Exception e) {
                    logger.error("Can't close the graph");
                }
            }).start();
            watch.stop("Add Transaction");
        } catch (Exception e) {
            logger.error(LogMarkers.FATAL, "Can't close graph");
        }
        return teacherOsid;
    }
}
