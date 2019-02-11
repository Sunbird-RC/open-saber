package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("registryDao")
public class RegistryDaoImpl implements IRegistryDao {
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Autowired
    EntityParenter entityParenter;

    @Autowired
    private DefinitionsManager definitionsManager;

    // TODO - Only database provider must be passed, no shard requirements here.
    @Autowired
    private Shard shard;

    private List<String> privatePropertyList;

    private Logger logger = LoggerFactory.getLogger(RegistryDaoImpl.class);

    private OpenSaberInstrumentation watch = new OpenSaberInstrumentation(true);

    public List<String> getPrivatePropertyList() {
        return privatePropertyList;
    }

    public void setPrivatePropertyList(List<String> privatePropertyList) {
        this.privatePropertyList = privatePropertyList;
    }

    /**
     * Entry point to the dao layer to write a JsonNode entity.
     *
     * @param rootNode
     * @return
     */
    public String addEntity(Graph graph, JsonNode rootNode) {
        VertexWriter vertexWriter = new VertexWriter(graph, shard.getDatabaseProvider(), uuidPropertyName);
        String entityId = vertexWriter.writeNodeEntity(rootNode);
        return entityId;
    }

    /**
     * Retrieves a record from the database
     *
     * @param uuid             entity identifier to retrieve
     * @param readConfigurator
     * @return
     */
    public JsonNode getEntity(Graph graph, String uuid, ReadConfigurator readConfigurator) throws Exception {

        VertexReader vr = new VertexReader(shard.getDatabaseProvider(), graph, readConfigurator, uuidPropertyName, definitionsManager);
        JsonNode result = vr.read(uuid);

        return result;
    }


    public JsonNode getEntity(Graph graph, Vertex vertex, ReadConfigurator readConfigurator) {

        VertexReader vr = new VertexReader(shard.getDatabaseProvider(), graph, readConfigurator, uuidPropertyName, definitionsManager);
        JsonNode result = vr.constructObject(vertex);

        if (!shard.getShardLabel().isEmpty()) {
            // Replace osid with shard details
            String prefix = shard.getShardLabel() + RecordIdentifier.getSeparator();
            JSONUtil.addPrefix((ObjectNode) result, prefix, new ArrayList<String>(Arrays.asList(uuidPropertyName)));
        }

        return result;
    }


    /**
     * This method update the inputJsonNode related vertices in the database
     * Notes:
     * This graph object is the same one used for reading the entire record
     *
     * @param vertex
     * @param inputJsonNode
     */
    public void updateVertex(Graph graph, Vertex vertex, JsonNode inputJsonNode) {
        if (inputJsonNode.isObject()) {
            updateObject(graph, vertex, (ObjectNode) inputJsonNode);
        } else {
            logger.error("Unexpected input passed here.");
        }
    }

    private void updateArray(Graph graph, Vertex vertex, String fieldKey, ArrayNode arrayNode) {
        List<String> osidSet = new ArrayList<>();
        boolean arrayItemIsObject = false;
        for (JsonNode arrayElementNode : arrayNode) {
            String updatedOsid = arrayElementNode.asText();
            if (arrayElementNode.isObject()) {
                updatedOsid = addNewEntity(arrayElementNode, graph, vertex, fieldKey, true);
                arrayItemIsObject = true;
            }
            osidSet.add(updatedOsid);
        }

        if (arrayItemIsObject) {
            logger.debug("Array items are objects");
            Set<String> tempSet = new HashSet<>();
            tempSet.addAll(osidSet);
            tempSet = deleteVertices(graph, vertex, fieldKey, tempSet);
            String updatedOisdValue = String.join(",", tempSet);
            vertex.property(RefLabelHelper.getLabel(fieldKey, uuidPropertyName), updatedOisdValue);
        } else {
            logger.debug("Array items are simple values");
            vertex.property(fieldKey, ArrayHelper.formatToString(osidSet));
        }

    }

    private void updateObject(Graph graph, Vertex vertex, ObjectNode inputJsonNode) {
        inputJsonNode.fields().forEachRemaining(field -> {
            JsonNode fieldValue = field.getValue();
            String fieldKey = field.getKey();
            if (fieldKey.equals(uuidPropertyName)) {
                // Should not update the uuid
            } else {
                if (fieldValue.isValueNode() && !fieldKey.equals(Constants.TYPE_STR_JSON_LD)) {
                    vertex.property(fieldKey, ValueType.getValue(fieldValue));
                } else if (fieldValue.isObject()) {
                    // Ignore, the caller would call us again for each object found
                } else if (fieldValue.isArray()) {
                    updateArray(graph, vertex, fieldKey, (ArrayNode) fieldValue);
                }
            }
        });
    }




    /**
     * Parse the json data to vertex properties, creates new vertex if the node is new else updates the existing vertex
     *
     * @param elementNode
     * @param graph
     * @param vertex
     * @param parentNodeLabel
     * @param isArrayElement
     * @return
     */
    private String addNewEntity(JsonNode elementNode, Graph graph, Vertex vertex, String parentNodeLabel,
                                   boolean isArrayElement) {
        String newChildVertexIdStr = "";
        if (null == elementNode.get(uuidPropertyName)) {
            // Adding a new entity

            VertexWriter vertexWriter = new VertexWriter(graph, shard.getDatabaseProvider(), uuidPropertyName);
            Vertex newChildVertex = vertexWriter.createVertex(parentNodeLabel);
            newChildVertexIdStr = shard.getDatabaseProvider().getId(newChildVertex);
            String rootId = vertex.property(uuidPropertyName).toString();

            if (null == rootId) {
                rootId = vertex.property(Constants.ROOT_KEYWORD).value().toString();
            }
            newChildVertex.property(Constants.ROOT_KEYWORD, rootId);
            updateVertex(graph, newChildVertex, elementNode);

            String nodeOsidLabel = RefLabelHelper.getLabel(parentNodeLabel, uuidPropertyName);
            VertexProperty<Object> vertexProperty = vertex.property(nodeOsidLabel);
            if (isArrayElement && vertexProperty.isPresent()) {
                String existingValue = (String) vertexProperty.value();
                vertex.property(nodeOsidLabel, existingValue + "," + newChildVertexIdStr);
            } else {
                vertex.property(nodeOsidLabel, newChildVertexIdStr);
            }

            if(vertex.label().equalsIgnoreCase(Constants.ARRAY_NODE_KEYWORD)){
                vertexWriter.addEdge(newChildVertex.property(Constants.SIGNATURE_FOR).value().toString(), vertex, newChildVertex);
            } else {
                vertexWriter.addEdge(parentNodeLabel, vertex, newChildVertex);
            }

        }
        return newChildVertexIdStr;
    }

    /**
     * This method is called while updating the entity. If any non-necessary vertex is there, it will be removed from the database
     * TO-DO need to do soft delete
     *
     * @param graph
     * @param rootVertex
     * @param label
     * @param activeOsid
     */
    private Set<String> deleteVertices(Graph graph, Vertex rootVertex, String label, Set<String> activeOsid) {
        String[] osidArray = null;
        VertexProperty vp = rootVertex.property(label + "_" + uuidPropertyName);
        String osidPropValue = ArrayHelper.removeSquareBraces((String) vp.value());
        if (osidPropValue.contains(",")) {
            osidArray = osidPropValue.split(",");
        } else {
            osidArray = new String[]{osidPropValue};
        }
        ReadConfigurator configurator = ReadConfiguratorFactory.getOne(true);
        VertexReader vertexReader = new VertexReader(shard.getDatabaseProvider(), graph,
                configurator, uuidPropertyName, definitionsManager);

        for(int itr = 0; itr < osidArray.length; itr++) {
            Vertex deleteVertex = vertexReader.getVertex(null, osidArray[itr]);
            if (activeOsid == null || (activeOsid != null &&
                    !activeOsid.contains(shard.getDatabaseProvider().getId(deleteVertex)) &&
                    deleteVertex.edges(Direction.IN).hasNext())) {
                deleteVertex.property(Constants.STATUS_KEYWORD, Constants.STATUS_INACTIVE);
            } else {
                activeOsid.add(shard.getDatabaseProvider().getId(deleteVertex));
            }
        }
        return activeOsid;
    }

    public void deleteEntity(Vertex vertex) {
        vertex.property(Constants.STATUS_KEYWORD, Constants.STATUS_INACTIVE);
    }
}
