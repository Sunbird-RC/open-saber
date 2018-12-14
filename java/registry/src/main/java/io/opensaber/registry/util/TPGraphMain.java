package io.opensaber.registry.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JGraph;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.DatabaseProviderWrapper;
import org.apache.tinkerpop.gremlin.process.traversal.P;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component("tpGraphMain")
public class TPGraphMain {
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    private Logger logger = LoggerFactory.getLogger(TPGraphMain.class);

    private OpenSaberInstrumentation watch = new OpenSaberInstrumentation(true);

    private Vertex createVertex(Graph graph, String label) {
        return graph.addVertex(label);
    }

    private void processArrayNode(Graph graph, Vertex vertex, String entryKey, JsonNode entryValue) {
        List<String> arrayEntries = new ArrayList<>();
        entryValue.forEach(jsonNode -> {
            if (jsonNode.isObject()) {
                Vertex createdV = processNode(graph, entryKey, vertex, jsonNode);
                arrayEntries.add(createdV.id().toString());
                addEdge(entryKey, vertex, createdV);
            } else {
                arrayEntries.add(entryValue.toString());
            }
        });

        if (!arrayEntries.isEmpty()) {
            String vIds = "[";
            for (String oneV : arrayEntries) {
                vIds = vIds + oneV + ",";
            }
            vIds = vIds.substring(0, vIds.length() - 1);
            vIds += "]";

            vertex.property(entryKey + uuidPropertyName, vIds);
        }
    }

    private Vertex processNode(Graph graph, String label, Vertex parentVertex, JsonNode jsonObject) {
        Vertex vertex = createVertex(graph, label);
        jsonObject.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            logger.debug("Processing {} -> {}", entry.getKey(), entry.getValue());

            if (entryValue.isValueNode()) {
                // Directly add under the vertex as a property
                vertex.property(entry.getKey(), entryValue.asText());
            } else if (entryValue.isObject()) {
                // Recursive calls
                Vertex v = processNode(graph, entry.getKey(), vertex, entryValue);
                addEdge(entry.getKey(), vertex, v);
                vertex.property(entry.getKey() + uuidPropertyName, v.id());
                logger.debug("Added edge between {} and {}", vertex.label(), v.label());
            } else if (entryValue.isArray()) {
                processArrayNode(graph, vertex, entry.getKey(), entry.getValue());
            }
        });
        return vertex;
    }

    private Edge addEdge(String label, Vertex v1, Vertex v2) {
        return v1.addEdge(label, v2);
    }

    public Vertex createParentVertex(Graph graph, String parentLabel) {
        Vertex parentVertex = null;
        P<String> lblPredicate = P.eq(parentLabel);

        GraphTraversalSource gtRootTraversal = graph.traversal();
//        GraphTraversal<Vertex, Vertex> rootVertex = gtRootTraversal.clone().V().hasLabel(parentLabel);
        Iterator<Vertex> iterVertex = gtRootTraversal.V().hasLabel(lblPredicate);
        if (!iterVertex.hasNext()) {
            parentVertex = graph.addVertex(parentLabel);
            parentVertex.property(uuidPropertyName, parentVertex.id().toString());
        } else {
            parentVertex = iterVertex.next();
            System.out.println(parentVertex.id().toString());
        }

        return parentVertex;
    }

    public String getParentName(JsonNode node) {
        return node.fieldNames().next();
    }

    @Autowired
    EntityParenter entityParenter;

    public String processEntity(Graph graph, JsonNode node) {
        String parentName = getParentName(node);
        Vertex parentVertex = entityParenter.getKnownParentVertex(parentName, "shard1");

        Vertex resultVertex = null;
        Iterator<Map.Entry<String, JsonNode>> entryIterator = node.fields();
        while (entryIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = entryIterator.next();

            // It is expected that node is wrapped under a root, which is the parent name/definition
            if (entry.getValue().isObject()) {
                resultVertex = processNode(graph, entry.getKey(), parentVertex, entry.getValue());
                // The parentVertex and the entity are connected. The parentVertex doesn't have
                // identifiers set on itself, whereas the entity just created has reference to parent.
                resultVertex.property(uuidPropertyName, parentVertex.id());

                Edge edge = addEdge(entry.getKey(), parentVertex, resultVertex);
            }
        }

        return resultVertex.id().toString();
    }

    /**
     * Retrieves all UUID of a given all labels.
     */
    public List<String> getUUIDs(Graph graph, List<String> parentLabels) {
        List<String> uuids = new ArrayList<>();;
        P<String> predicateStr = P.within(parentLabels);
        GraphTraversal<Vertex, Vertex> graphTraversal = graph.traversal().V();
        GraphTraversal<Vertex, Vertex> gvs = graphTraversal.hasLabel(predicateStr);

        while (gvs.hasNext()){
            Vertex v = gvs.next();
            if (v != null) {
                uuids.add(v.value(uuidPropertyName).toString());
            }
        }
        return uuids;
    }

    public JsonNode readGraph2Json(Graph graph, String osid) {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();

        Iterator<Vertex> itrV = graph.vertices(osid);
        if (itrV.hasNext()) {
            Vertex currVertex = itrV.next();
            currVertex.properties().forEachRemaining(prop -> {
                String propertyName = prop.label();
                int uuidPropertyIdx = propertyName.indexOf(uuidPropertyName);
                if (propertyName.compareTo(uuidPropertyName) != 0 &&
                        uuidPropertyIdx != -1) {
                    // This is a child entity. Go retrieve that
                    String childEntityName = propertyName.substring(0, uuidPropertyIdx);
                    Object valueObj = prop.value();
                    if (valueObj.getClass().isArray()) {
                        objectNode.put(childEntityName, "TODO array");
                    } else {
                        objectNode.set(childEntityName, readGraph2Json(graph, prop.value().toString()));
                    }
                } else {
                    objectNode.put(prop.label(), prop.value().toString());
                }
            });
        }

        return objectNode;
    }

    public static enum DBTYPE {NEO4J, POSTGRES}

    @Autowired
    private DatabaseProviderWrapper databaseProviderWrapper;


    public String addEntity(String shardId, JsonNode rootNode) {
        String entityId = "";
        DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
        try (Graph graph = databaseProvider.getGraphStore()) {
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                entityId = processEntity(graph, rootNode);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Can't close graph");
        }
        return entityId;
    }
}
