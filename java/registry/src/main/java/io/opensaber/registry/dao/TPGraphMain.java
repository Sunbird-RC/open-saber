package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.schema.configurator.ISchemaConfigurator;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.util.EntityParenter;
import io.opensaber.registry.util.ParentLabelGenerator;
import io.opensaber.registry.util.ReadConfigurator;
import io.opensaber.registry.util.RefLabelHelper;
import io.opensaber.registry.util.TypePropertyHelper;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("tpGraphMain")
public class TPGraphMain {
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Autowired
    EntityParenter entityParenter;

    @Autowired
    private Shard shard;

    @Autowired
    private ISchemaConfigurator schemaConfigurator;

    private List<String> privatePropertyList;

    private Logger logger = LoggerFactory.getLogger(TPGraphMain.class);

    private OpenSaberInstrumentation watch = new OpenSaberInstrumentation(true);

    public List<String> getPrivatePropertyList() {
        return privatePropertyList;
    }

    public void setPrivatePropertyList(List<String> privatePropertyList) {
        this.privatePropertyList = privatePropertyList;
    }

    private Vertex createVertex(Graph graph, String label) {
        Vertex vertex = graph.addVertex(label);

        vertex.property(TypePropertyHelper.getTypeName(), label);
        try {
            UUID uuid = UUID.fromString(vertex.id().toString());
        } catch (IllegalArgumentException e) {
            // Must be not a neo4j store. Create an explicit osid property.
            // Note this will be OS unique record, but the database provider might choose to use only
            // id field.
            vertex.property(uuidPropertyName, shard.getDatabaseProvider().generateId(vertex));
        }

        return vertex;
    }

    /**
     * Writes an array into the database. For each array item, if it is an object
     * creates/populates a new vertex/table and stores the reference
     *
     * @param graph
     * @param vertex
     * @param entryKey
     * @param arrayNode
     */
    private void writeArrayNode(Graph graph, Vertex vertex, String entryKey, ArrayNode arrayNode) {
        List<String> uidList = new ArrayList<>();
        boolean isArrayItemObject = arrayNode.get(0).isObject();

        for(JsonNode jsonNode : arrayNode) {
            if (jsonNode.isObject()) {
                Vertex createdV = processNode(graph, entryKey, jsonNode);
                uidList.add(createdV.id().toString());
                addEdge(entryKey, vertex, createdV);
            } else {
                uidList.add(jsonNode.asText());
            }
        }

        String label = entryKey;
        if (isArrayItemObject) {
            label = RefLabelHelper.getLabel(entryKey, uuidPropertyName);
        }
        vertex.property(label, StringUtils.arrayToCommaDelimitedString(uidList.toArray()));
    }

    private Vertex processNode(Graph graph, String label, JsonNode jsonObject) {
        Vertex vertex = createVertex(graph, label);

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
                //String idToSet = databaseProviderWrapper.getDatabaseProvider().generateId(v);
                vertex.property(RefLabelHelper.getLabel(entry.getKey(), uuidPropertyName), v.id().toString());
                logger.debug("Added edge between {} and {}", vertex.label(), v.label());
            } else if (entryValue.isArray()) {
                writeArrayNode(graph, vertex, entry.getKey(), (ArrayNode) entry.getValue());
            }
        });
        return vertex;
    }

    /**
     * Adds an edge between two vertices
     *
     * @param label
     * @param v1    the source
     * @param v2    the target
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
            parentVertex = createVertex(graph, parentLabel);
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
     *
     * @param node
     * @return
     */
    public String getParentName(JsonNode node) {
        return node.fieldNames().next();
    }

    /**
     * Writes the node entity into the database.
     *
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
                //String idToSet = databaseProviderWrapper.getDatabaseProvider().generateId(parentVertex);
                resultVertex.property(RefLabelHelper.getLabel(parentGroupName, uuidPropertyName), parentVertex.id().toString());

                addEdge(entry.getKey(), resultVertex, parentVertex);
            }
        }

        return resultVertex.id().toString();
    }

    /**
     * Retrieves all vertex UUID for given all labels.
     */
	public List<String> getUUIDs(Graph graph, Set<String> labels) {
		List<String> uuids = new ArrayList<>();
		// Temporarily adding all the vertex ids.
		//TODO: get graph traversal by passed labels
		GraphTraversal<Vertex, Vertex> graphTraversal = graph.traversal().V();
		while (graphTraversal.hasNext()) {
			Vertex v = graphTraversal.next();
			uuids.add(v.id().toString());
			logger.debug("vertex info- label :" + v.label() + " id: " + v.id());
		}
		return uuids;
	}

    /**
     * Entry point to the dao layer to write a JsonNode entity.
     *
     * @param shardId
     * @param rootNode
     * @return
     */
    public String addEntity(String shardId, JsonNode rootNode) throws Exception {
        String entityId = "";
        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = databaseProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                entityId = writeNodeEntity(graph, rootNode);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Can't close graph",e);
        }
        return entityId;
    }

    /**
     * Retrieves a record from the database
     *
     * @param shardId
     * @param uuid    entity identifier to retrieve
     * @param readConfigurator
     * @return
     */
    public JsonNode getEntity(String shardId, String uuid, ReadConfigurator readConfigurator) {
        if (null == privatePropertyList) {
            privatePropertyList = new ArrayList<>();
            setPrivatePropertyList(schemaConfigurator.getAllPrivateProperties());
        }

        JsonNode result = JsonNodeFactory.instance.objectNode();
        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = databaseProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                VertexReader vr = new VertexReader(graph, readConfigurator, uuidPropertyName, privatePropertyList);
                result = vr.read(uuid);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Exception occurred during read entity ", e);
        }
        return result;
    }

    /** This method update the vertex with inputJsonNode
     * @param graph
     * @param rootVertex
     * @param inputJsonNode
     */
    public void updateVertex(Graph graph, Vertex rootVertex, JsonNode inputJsonNode) {
        inputJsonNode.fields().forEachRemaining(record -> {
            JsonNode elementNode = record.getValue();
            if(elementNode.isValueNode()){
               // rootVertex.property(record.getKey(),record.getValue().asText());
            } else if(elementNode.isObject()){
                deleteExistingVerticesIfPresent(graph,rootVertex,elementNode,record.getKey());
                //Add new vertex
                Vertex newChildVertex = createVertex(graph,record.getKey());
                elementNode.fields().forEachRemaining(subElementNode -> {
                    JsonNode value = subElementNode.getValue();
                    if(value.isObject()){

                    } else {
                            if(value.isValueNode() && !value.equals("@type")){
                            newChildVertex.property(subElementNode.getKey(),value.asText());
                        }
                    }
                });
                //newChildVertex.property(RefLabelHelper.getLabel(uuidPropertyName, newChildVertex.id().toString()));
                rootVertex.property(RefLabelHelper.getLabel(record.getKey(), uuidPropertyName), newChildVertex.id().toString());
                addEdge(record.getKey(),rootVertex,newChildVertex);
            }
        });
    }

    private void deleteExistingVerticesIfPresent(Graph graph, Vertex rootVertex, JsonNode childNode, String label) {
        VertexProperty vp = rootVertex.property(label+"_"+uuidPropertyName);
        Object vertexOsid = (String) vp.value();
        Iterator<Vertex> vertices = graph.vertices(vertexOsid);
        //deleting existing vertices
        vertices.forEachRemaining(deleteVertex -> {
            deleteVertex.edges(Direction.IN,label).next().remove();
            deleteVertex.remove();
        });
    }

}
