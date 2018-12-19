package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.middleware.util.LogMarkers;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.DatabaseProviderWrapper;
import io.opensaber.registry.util.EntityParenter;
import io.opensaber.registry.util.RefLabelHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("tpGraphMain")
public class TPGraphMain {
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Autowired
    EntityParenter entityParenter;

    @Autowired
    private DatabaseProviderWrapper databaseProviderWrapper;

    public static enum DBTYPE {NEO4J, POSTGRES}

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
            String vIds = "";
            for (String oneV : arrayEntries) {
                vIds = vIds + oneV + ",";
            }
            // Remove the last comma
            vIds = vIds.substring(0, vIds.length() - 1);
            //vIds += "]";

            vertex.property(RefLabelHelper.getLabel(entryKey, uuidPropertyName), vIds);
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
                vertex.property(RefLabelHelper.getLabel(entry.getKey(), uuidPropertyName), v.id());
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

    /**
     * Ensures a parent vertex existence at the exit of this function
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

    public String getParentName(JsonNode node) {
        return node.fieldNames().next();
    }

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
                resultVertex.property(parentName + "_" + uuidPropertyName, parentVertex.id());

                Edge edge = addEdge(entry.getKey(), parentVertex, resultVertex);
            }
        }

        return resultVertex.id().toString();
    }

    /**
     * Retrieves all vertex UUID for given all labels.
     */
    public List<String> getUUIDs(Graph graph, Set<String> labels) {
        List<String> uuids = new ArrayList<>();;
        P<String> predicateStr = P.within(labels);
        GraphTraversal<Vertex, Vertex> graphTraversal = graph.traversal().V().hasLabel(predicateStr);
        while (graphTraversal.hasNext()){
            Vertex v = graphTraversal.next();
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
                if (RefLabelHelper.isRefLabel(prop.key(), uuidPropertyName)) {
                    logger.debug("{} is a referenced entity", prop.key());
                    // This is another entity. Go retrieve that
                    String refEntityName = RefLabelHelper.getRefEntityName(prop.key());
                    String[] valueArr = new String[]{prop.value().toString()};

                    ArrayNode resultNode = JsonNodeFactory.instance.arrayNode();
                    for(String value: valueArr) {
                        JsonNode oneEntity = readGraph2Json(graph, value);
                        resultNode.add(oneEntity);
                    }
                    objectNode.set(refEntityName, resultNode);
                } else {
                    logger.debug("{} is a simple value", prop.key());
                    objectNode.put(prop.key(), prop.value().toString());
                }
            });
        }

        return objectNode;
    }

    public String addEntity(String shardId, JsonNode rootNode) {
        String entityId = "";
        DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
        try (Graph graph = databaseProvider.getGraphStore()) {
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                entityId = processEntity(graph, rootNode);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Can't close graph",e);
        }
        return entityId;
    }

    public JsonNode getEntity(String shardId, String uuid) {
        JsonNode result = JsonNodeFactory.instance.objectNode();
        DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
        try (Graph graph = databaseProvider.getGraphStore()) {
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                result = readGraph2Json(graph, uuid);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Can't close graph",e);
        }
        return result;
    }

    public void updateEntity(JsonNode rootNode) {
        try {
            ObjectNode objectNode = null;
            DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
            Graph graph = databaseProvider.getGraphStore();
            GraphTraversalSource gtRootTraversal = graph.traversal();
            watch.start("Add Transaction");
            if(graph.features().graph().supportsTransactions()){
                try (Transaction tx = graph.tx()) {
                    mergeAndUpdateVertices(rootNode,graph);
                    tx.commit();
                }
            } else {
                mergeAndUpdateVertices(rootNode,graph);
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
            logger.error(LogMarkers.FATAL, "Can't close graph",e);
        }
    }

    private void mergeAndUpdateVertices(JsonNode rootNode, Graph graph){
        ObjectNode objectNode = null;
        Iterator<Vertex> vertexIterator = null;
        Iterator<Vertex> vertexLst = null;
        String idProp = rootNode.elements().next().get("id").asText();
        JsonNode node = rootNode.elements().next();

        //GraphTraversal<Vertex, Vertex> rootVertex = gtRootTraversal.V(idProp);
        if(graph.features().graph().supportsTransactions()){
            vertexIterator = graph.vertices(idProp);
        } else {
            vertexIterator = graph.vertices(new Long(idProp));
        }
        Vertex rootVertex = vertexIterator.hasNext() ? vertexIterator.next(): null;

        objectNode = mergePropertiesFromTPVertexAndNode(rootVertex,rootNode.deepCopy());
        if(rootVertex.label().equalsIgnoreCase("Teacher")){
            vertexLst = rootVertex.vertices(Direction.OUT);
        } else {
            vertexLst = rootVertex.vertices(Direction.IN,rootVertex.label()).next().vertices(Direction.OUT);
        }
        //rootVertex.vertices(Direction.IN,rootVertex.label()).next().vertices(Direction.OUT);
        //Iterator<Vertex> vertexLst = rootVertex.vertices(Direction.OUT);
        while(vertexLst.hasNext()){
            Vertex childVertex = vertexLst.next();
            if(!childVertex.id().equals(rootVertex.id()) && null == objectNode.get(childVertex.label())){
                objectNode.set(childVertex.label(),(ObjectNode)JsonNodeFactory.instance.objectNode());
            }
            objectNode = mergePropertiesFromTPVertexAndNode(childVertex,objectNode);
        }
        //If validation works
                /*JsonValidationServiceImpl validate = new JsonValidationServiceImpl();
                if(validate.validate(objectNode.toString(),rootVertex.label())){
                    updateVertex(rootVertex,node);
                } else {

                }*/
        updateVertex(rootVertex,node);
    }

    private void updateVertex(Vertex rootVertex, JsonNode node) {
        node.fields().forEachRemaining(record -> {
            rootVertex.property(record.getKey(),record.getValue().asText());
        });
    }

    private ObjectNode mergePropertiesFromTPVertexAndNode(Vertex vertex, ObjectNode objectNode) {
        ObjectNode childObjetcNode = null;
        ArrayNode childArrayObjetcNode = null;
        if(objectNode.get(vertex.label()) instanceof ObjectNode) {
            childObjetcNode = (ObjectNode)objectNode.get(vertex.label());
        } else if(objectNode.get(vertex.label()) instanceof ArrayNode) {
            childArrayObjetcNode = (ArrayNode) objectNode.get(vertex.label());
        }
        if(null != childObjetcNode && childObjetcNode.size()!=0 && !childObjetcNode.isArray() && !vertex.label().equalsIgnoreCase("Teacher")){
            childArrayObjetcNode = JsonNodeFactory.instance.arrayNode();
            childArrayObjetcNode.add(childObjetcNode);
            objectNode.set(vertex.label(),childArrayObjetcNode);
        }
        if(childArrayObjetcNode !=  null){
            ObjectNode childNode = JsonNodeFactory.instance.objectNode();
            vertex.properties().forEachRemaining(vtx -> {
                if(!(vtx.key().contains("osid"))){
                    childNode.put(vtx.key(),vtx.value().toString());
                }
            });
            childArrayObjetcNode.add(childNode);
        } else {
            for (Iterator<VertexProperty<Object>> it = vertex.properties(); it.hasNext(); ) {
                VertexProperty vtx = it.next();
                if(!(vtx.key().contains("osid")) && childObjetcNode.get(vtx.key()) == null){
                    childObjetcNode.put(vtx.key(),vtx.value().toString());
                }

            }
        }
        return objectNode;
    }
}
