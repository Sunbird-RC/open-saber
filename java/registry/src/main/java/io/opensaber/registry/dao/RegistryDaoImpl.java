package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.middleware.util.Constants;
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
public class RegistryDaoImpl implements IRegistryDao {
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Autowired
    EntityParenter entityParenter;

    @Autowired
    private Shard shard;

    @Autowired
    private ISchemaConfigurator schemaConfigurator;

    private List<String> privatePropertyList;

    private Logger logger = LoggerFactory.getLogger(RegistryDaoImpl.class);

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
     * @param rootNode
     * @return
     */
    public String addEntity(JsonNode rootNode) {
        String entityId = "";
        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = databaseProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                entityId = writeNodeEntity(graph, rootNode);
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Can't close graph", e);
        }
        return entityId;
    }

    /**
     * Retrieves a record from the database
     * @param uuid    entity identifier to retrieve
     * @param readConfigurator
     * @return
     */
    public JsonNode getEntity(String uuid, ReadConfigurator readConfigurator) {
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


    /** This method update the inputJsonNode related vertices in the database
     * @param rootVertex
     * @param inputJsonNode
     */
    public void updateVertex( Vertex rootVertex, JsonNode inputJsonNode) {
        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        try(OSGraph osGraph = databaseProvider.getOSGraph()){
            Graph graph = osGraph.getGraphStore();
            try (Transaction tx = databaseProvider.startTransaction(graph)) {
                inputJsonNode.fields().forEachRemaining(subEntityField -> {
                    String fieldKey = subEntityField.getKey();
                    JsonNode subEntityNode = subEntityField.getValue();
                    if (subEntityNode.isValueNode()) {
                        rootVertex.property(fieldKey,subEntityField.getValue().asText());
                    } else if (subEntityNode.isObject()) {
                        parseJsonObject(subEntityNode,graph,rootVertex,fieldKey,false);
                    } else if(subEntityNode.isArray()){
                        List<String> osidList = new ArrayList<String>();
                        subEntityNode.forEach(arrayElementNode -> {
                            if(arrayElementNode.isObject()){
                                String updatedOsid = parseJsonObject(arrayElementNode,graph,rootVertex,fieldKey, true);
                                osidList.add(updatedOsid);
                            }
                        });
                        deleteVertices(graph,rootVertex,fieldKey,osidList);
                        String updatedOisdValue = String.join(",",osidList);
                        rootVertex.property(RefLabelHelper.getLabel(fieldKey, uuidPropertyName),updatedOisdValue);
                    }
                });
                databaseProvider.commitTransaction(graph, tx);
            }
        } catch (Exception e) {
            logger.error("Exception occurred during update entity ", e);
        }
    }


    /** Parse the json data to vertex properties, creates new vertex if the node is new else updates the existing vertex
     * @param elementNode
     * @param graph
     * @param rootVertex
     * @param parentNodeLabel
     * @param isArrayElement
     * @return
     */
    private String parseJsonObject(JsonNode elementNode, Graph graph, Vertex rootVertex, String parentNodeLabel, boolean isArrayElement) {
        if(null == elementNode.get(uuidPropertyName)){
            if(!isArrayElement){
                deleteVertices(graph, rootVertex, parentNodeLabel, null);
            }
            //Add new vertex
            Vertex newChildVertex = createVertex(graph, parentNodeLabel);
            updateProperties(elementNode,newChildVertex);
            String nodeOsidLabel = RefLabelHelper.getLabel(parentNodeLabel, uuidPropertyName);
            VertexProperty<Object> vertexProperty =  rootVertex.property(nodeOsidLabel);
            if(isArrayElement && vertexProperty.isPresent()){
                String existingValue = (String)vertexProperty.value();
                rootVertex.property(nodeOsidLabel,existingValue+","+newChildVertex.id().toString());
            } else {
                rootVertex.property(nodeOsidLabel, newChildVertex.id().toString());
            }
            addEdge(parentNodeLabel, rootVertex, newChildVertex);
            return newChildVertex.id().toString();
        } else {
            Vertex updateVertex = graph.vertices(elementNode.get(uuidPropertyName).asText()).next();
            updateProperties(elementNode,updateVertex);
            return updateVertex.id().toString();
        }
    }

    /** updates the vertex properties with given json node elements
     * @param elementNode
     * @param vertex
     */
    private void updateProperties(JsonNode elementNode, Vertex vertex){
        elementNode.fields().forEachRemaining(subElementNode -> {
            JsonNode value = subElementNode.getValue();
            String keyType = subElementNode.getKey();
            if (value.isObject()) {

            } else if (value.isValueNode() && !keyType.equals("@type") && !keyType.equals(uuidPropertyName)) {
                    vertex.property(keyType, value.asText());
            }
        });
    }

    /**This method is called while updating the entity. If any non-necessary vertex is there, it will be removed from the database
     * TO-DO need to do soft delete
     * @param graph
     * @param rootVertex
     * @param label
     * @param activeOsid
     */
    private void deleteVertices(Graph graph, Vertex rootVertex, String label, List<String> activeOsid) {
        String[] osidArray = null;
        VertexProperty vp = rootVertex.property(label+"_"+uuidPropertyName);
        String osidPropValue = (String) vp.value();
        if(osidPropValue.contains(",")){
            osidArray = osidPropValue.split(",");
        } else {
            osidArray = new String[]{osidPropValue};
        }
        Iterator<Vertex> vertices = graph.vertices(osidArray);
        //deleting existing vertices
        vertices.forEachRemaining(deleteVertex -> {
            if(activeOsid == null || (activeOsid != null && !activeOsid.contains(deleteVertex.id()))){
                deleteVertex.property(Constants.STATUS_KEYWORD,Constants.STATUS_INACTIVE);
                Edge edge = deleteVertex.edges(Direction.IN,label).next();
                edge.remove();
                //deleteVertex.edges(Direction.IN,label).next().remove();
                //deleteVertex.remove();
                //addEdge(label,deleteVertex,rootVertex);

            }
        });
    }
}
