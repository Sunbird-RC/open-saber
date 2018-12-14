package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.schema.configurator.ISchemaConfigurator;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.DatabaseProviderWrapper;
import io.opensaber.registry.util.*;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component("tpGraphMain")
public class TPGraphMain {
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Autowired
    EntityParenter entityParenter;

    @Autowired
    private DatabaseProviderWrapper databaseProviderWrapper;

    @Autowired
    private ISchemaConfigurator schemaConfigurator;

    private List<String> privatePropertyList;

    public static enum DBTYPE {NEO4J, POSTGRES}

    private Logger logger = LoggerFactory.getLogger(TPGraphMain.class);

    private OpenSaberInstrumentation watch = new OpenSaberInstrumentation(true);

    public List<String> getPrivatePropertyList() {
        return privatePropertyList;
    }

    public void setPrivatePropertyList(List<String> privatePropertyList) {
        this.privatePropertyList = privatePropertyList;
    }

    private Vertex createVertex(Graph graph, String label) {
        return graph.addVertex(label);
    }

    /**
     * Writes an array into the database. For each array item, if it is an object
     * creates/populates a new vertex/table and stores the reference
     * @param graph
     * @param vertex
     * @param entryKey
     * @param arrayNode
     */
    private void writeArrayNode(Graph graph, Vertex vertex, String entryKey, ArrayNode arrayNode) {
        List<String> arrayEntries = new ArrayList<>();
        boolean isArrayItemObject = arrayNode.get(0).isObject();

        arrayNode.forEach(entry -> {
            JsonNode jsonNode = entry;
            if (jsonNode.isObject()) {
                Vertex createdV = processNode(graph, entryKey, jsonNode);
                arrayEntries.add(createdV.id().toString());
                addEdge(entryKey, vertex, createdV);

            } else {
                arrayEntries.add(jsonNode.asText());
            }
        });

        if (!arrayEntries.isEmpty()) {
            String vIds = "";
            for (String oneV : arrayEntries) {
                vIds = vIds + oneV + ",";
            }
            // Remove the last comma
            vIds = vIds.substring(0, vIds.length() - 1);

            String label = entryKey;
            if (isArrayItemObject) {
                label = RefLabelHelper.getLabel(entryKey, uuidPropertyName);
            }
            vertex.property(label, vIds);
        }
    }

    private Vertex processNode(Graph graph, String label, JsonNode jsonObject) {
        Vertex vertex = createVertex(graph, label);

        vertex.property(TypePropertyHelper.getTypeName(), label);
        jsonObject.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            logger.debug("Processing {} -> {}", entry.getKey(), entry.getValue());

            if (entryValue.isValueNode()) {
                // Directly add under the vertex as a property
                vertex.property(entry.getKey(), entryValue.asText());
            } else if (entryValue.isObject()) {
                // Recursive calls
                Vertex v = processNode(graph, entry.getKey(), entryValue);
                addEdge(entry.getKey(), vertex, v);
                vertex.property(RefLabelHelper.getLabel(entry.getKey(), uuidPropertyName), v.id());
                logger.debug("Added edge between {} and {}", vertex.label(), v.label());
            } else if (entryValue.isArray()) {
                writeArrayNode(graph, vertex, entry.getKey(), (ArrayNode) entry.getValue());
            }
        });
        return vertex;
    }

    /**
     * Adds an edge between two vertices
     * @param label
     * @param v1 the source
     * @param v2 the target
     * @return
     */
    private Edge addEdge(String label, Vertex v1, Vertex v2) {
        return v1.addEdge(label, v2);
    }

    /**
     * Ensures a parent vertex existence at the exit of this function
     *
     * @param graph
     * @param parentLabel
     * @return
     */
    public Vertex ensureParentVertex(Graph graph, String parentLabel) {
        Vertex parentVertex = null;
        P<String> lblPredicate = P.eq(parentLabel);

        GraphTraversalSource gtRootTraversal = graph.traversal().clone();
        Iterator<Vertex> iterVertex = gtRootTraversal.V().hasLabel(lblPredicate);
        if (!iterVertex.hasNext()) {
            parentVertex = graph.addVertex(parentLabel);
            parentVertex.property(uuidPropertyName, parentVertex.id().toString());
            logger.info("Parent label {} created {}", parentLabel, parentVertex.id().toString());
        } else {
            parentVertex = iterVertex.next();
            logger.info("Parent label {} already existing {}", parentLabel, parentVertex.id().toString());
        }

        return parentVertex;
    }

    /**
     * Fetches the parent. In the current use cases, we expect only one
     * top level parent is passed.
     * @param node
     * @return
     */
    public String getParentName(JsonNode node) {
        return node.fieldNames().next();
    }

    /**
     * Writes the node entity into the database.
     * @param graph
     * @param node
     * @return
     */
    public String writeNodeEntity(Graph graph, JsonNode node) {
        String parentName = getParentName(node);
        String parentGroupName = ParentLabelGenerator.getLabel(parentName);
        Vertex parentVertex = entityParenter.getKnownParentVertex(parentName, "shard1");

        Vertex resultVertex = null;
        Iterator<Map.Entry<String, JsonNode>> entryIterator = node.fields();
        while (entryIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = entryIterator.next();

            // It is expected that node is wrapped under a root, which is the parent name/definition
            if (entry.getValue().isObject()) {
                resultVertex = processNode(graph, entry.getKey(), entry.getValue());
                // The parentVertex and the entity are connected. The parentVertex doesn't have
                // identifiers set on itself, whereas the entity just created has reference to parent.
                resultVertex.property(RefLabelHelper.getLabel(parentGroupName, uuidPropertyName), parentVertex.id());

                addEdge(entry.getKey(), resultVertex, parentVertex);
            }
        }

        return resultVertex.id().toString();
    }

    /**
     * Retrieves all UUID for the given labels.
     */
    public List<String> getUUIDs(Graph graph, List<String> parentLabels) {
        List<String> uuids = new ArrayList<>();
        ;
        P<String> predicateStr = P.within(parentLabels);
        GraphTraversal<Vertex, Vertex> graphTraversal = graph.traversal().V();
        GraphTraversal<Vertex, Vertex> gvs = graphTraversal.hasLabel(predicateStr);

        while (gvs.hasNext()) {
            Vertex v = gvs.next();
            if (v != null) {
                uuids.add(v.value(uuidPropertyName).toString());
            }
        }
        return uuids;
    }

    /**
     * Hits the database to read contents
     * @param graph
     * @param wrapRequired whether or not to wrap the read content from database
     * @param configurator defines the configuration for this read operation
     * @param osid the id to be read
     * @return
     * @throws Exception
     */
    public JsonNode readGraph2Json(Graph graph, boolean wrapRequired, ReadConfigurator configurator, String osid) throws Exception {
        ObjectNode entityNode = JsonNodeFactory.instance.objectNode();
        ObjectNode contentNode = JsonNodeFactory.instance.objectNode();
        String entityType = "";

        Iterator<Vertex> itrV = graph.vertices(osid);
        if (!itrV.hasNext()) {
            throw new Exception("Invalid id");
        }

        Vertex currVertex = itrV.next();
        currVertex.properties().forEachRemaining(prop -> {
            if (!RefLabelHelper.isParentLabel(prop.key())) {
                if (RefLabelHelper.isRefLabel(prop.key(), uuidPropertyName) &&
                        configurator.isFetchChildren()) {
                    logger.debug("{} is a referenced entity", prop.key());

                    String refEntityName = RefLabelHelper.getRefEntityName(prop.key());
                    String[] valueArr = prop.value().toString().split("\\s*,\\s*");

                    ArrayNode resultNode = JsonNodeFactory.instance.arrayNode();
                    for (String value : valueArr) {
                        try {
                            JsonNode oneEntity = readGraph2Json(graph, false, configurator, value);
                            resultNode.add(oneEntity);
                        } catch (Exception e) {
                            logger.info("No entity exists with uuid {}", value);
                        }
                    }
                    contentNode.set(refEntityName, resultNode);
                } else {
                    logger.debug("{} is a simple value", prop.key());
                    boolean canAdd = true;
                    if (TypePropertyHelper.isTypeProperty(prop.key())) {
                        canAdd &= configurator.isIncludeTypeAttributes();
                    } else if (privatePropertyList.contains(prop.key())) {
                        canAdd &= configurator.isIncludeEncryptedProp();
                    }

                    if (canAdd) {
                        contentNode.put(prop.key(), prop.value().toString());
                    }
                }
            } else {
                logger.debug("Root type, ignoring");
            }
        });

        // Here we set the id
        contentNode.put(uuidPropertyName, currVertex.id().toString());

        if (wrapRequired) {
            entityType = currVertex.value(TypePropertyHelper.getTypeName());
            entityNode.set(entityType, contentNode);
        } else {
            entityNode = contentNode;
        }

        return entityNode;
    }

    /**
     * Entry point to the dao layer to write a JsonNode entity.
     * @param shardId
     * @param rootNode
     * @return
     */
    public String addEntity(String shardId, JsonNode rootNode) {
        String entityId = "";
        DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
        try (Graph graph = databaseProvider.getGraphStore()) {
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                entityId = writeNodeEntity(graph, rootNode);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Can't close graph");
        }
        return entityId;
    }

    /**
     * Retrieves a record from the database
     * @param shardId
     * @param uuid entity identifier to retrieve
     * @return
     */
    public JsonNode getEntity(String shardId, String uuid) {
        if (null == privatePropertyList) {
            privatePropertyList = new ArrayList<>();
            setPrivatePropertyList(schemaConfigurator.getAllPrivateProperties());
        }

        JsonNode result = JsonNodeFactory.instance.objectNode();
        DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
        ReadConfigurator readConfigurator = new ReadConfigurator();
        try (Graph graph = databaseProvider.getGraphStore()) {
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                result = readGraph2Json(graph, true, readConfigurator, uuid);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Exception occurred during read entity ", e);
        }
        return result;
    }
}
