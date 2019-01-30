package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import io.opensaber.pojos.ComponentHealthInfo;
import io.opensaber.pojos.HealthCheckResponse;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.dao.VertexReader;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.*;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.util.*;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Component
public class RegistryServiceImpl implements RegistryService {

    private static final String ID_REGEX = "\"@id\"\\s*:\\s*\"_:[a-z][0-9]+\",";
    private static Logger logger = LoggerFactory.getLogger(RegistryServiceImpl.class);

    @Autowired
    EncryptionService encryptionService;
    @Autowired
    SignatureService signatureService;
    @Autowired
    Gson gson;
    @Autowired
    private IRegistryDao registryDao;
    @Autowired
    private DefinitionsManager definitionsManager;
    @Autowired
    private EncryptionHelper encryptionHelper;
    @Autowired
    private SignatureHelper signatureHelper;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${encryption.enabled}")
    private boolean encryptionEnabled;

    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Value("${signature.enabled}")
    private boolean signatureEnabled;

    @Value("${persistence.enabled}")
    private boolean persistenceEnabled;

    @Value("${signature.domain}")
    private String signatureDomain;

    @Value("${signature.keysURL}")
    private String signatureKeyURl;

    @Value("${frame.file}")
    private String frameFile;

    @Value("${registry.context.base}")
    private String registryContextBase;

    @Value("${registry.context.base}")
    private String registryContext;

    @Autowired
    private Shard shard;

    @Autowired
    DBConnectionInfoMgr dbConnectionInfoMgr;

    @Autowired
    private EntityParenter entityParenter;

    public HealthCheckResponse health() throws Exception {
        HealthCheckResponse healthCheck;
        // TODO
        boolean databaseServiceup = shard.getDatabaseProvider().isDatabaseServiceUp();
        boolean overallHealthStatus = databaseServiceup;
        List<ComponentHealthInfo> checks = new ArrayList<>();

        ComponentHealthInfo databaseServiceInfo = new ComponentHealthInfo(Constants.OPENSABER_DATABASE_NAME,
                databaseServiceup);
        checks.add(databaseServiceInfo);

        if (encryptionEnabled) {
            boolean encryptionServiceStatusUp = encryptionService.isEncryptionServiceUp();
            ComponentHealthInfo encryptionHealthInfo = new ComponentHealthInfo(
                    Constants.SUNBIRD_ENCRYPTION_SERVICE_NAME, encryptionServiceStatusUp);
            checks.add(encryptionHealthInfo);
            overallHealthStatus = overallHealthStatus && encryptionServiceStatusUp;
        }

        if (signatureEnabled) {
            boolean signatureServiceStatusUp = signatureService.isServiceUp();
            ComponentHealthInfo signatureServiceInfo = new ComponentHealthInfo(Constants.SUNBIRD_SIGNATURE_SERVICE_NAME,
                    signatureServiceStatusUp);
            checks.add(signatureServiceInfo);
            overallHealthStatus = overallHealthStatus && signatureServiceStatusUp;
        }

        healthCheck = new HealthCheckResponse(Constants.OPENSABER_REGISTRY_API_NAME, overallHealthStatus, checks);
        logger.info("Heath Check :  ", checks.toArray().toString());
        return healthCheck;
    }

    /**
     * delete the vertex and changing the status
     *
     * @param uuid
     * @throws Exception
     */
    @Override
    public void deleteEntityById(String uuid) throws Exception {
        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = databaseProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            Transaction tx = databaseProvider.startTransaction(graph);
            Iterator<Vertex> vertexItr = graph.vertices(uuid);
            if (vertexItr.hasNext()) {
                Vertex vertex = vertexItr.next();
                if (!(vertex.property(Constants.STATUS_KEYWORD).isPresent() &&
                        vertex.property(Constants.STATUS_KEYWORD).value().equals(Constants.STATUS_INACTIVE))) {
                    registryDao.deleteEntity(vertex);
                } else {
                    // throw exception node already deleted
                    throw new RecordNotFoundException("Cannot perform the operation");
                }
            } else {
                throw new RecordNotFoundException("No such record found");
            }

            databaseProvider.commitTransaction(graph, tx);
        }
    }

    private void addIndex(DatabaseProvider dbProvider, String shardId, String vertexLabel) {
//        new Thread(() -> {
//            try (OSGraph osGraph = dbProvider.getOSGraph()) {
//                Graph graph = osGraph.getGraphStore();
//                Transaction tx = dbProvider.startTransaction(graph);
//
//                // creates/updates indices for the vertex or table gets persists)
//                ensureIndexExists(dbProvider, shardId, vertexLabel);
//                dbProvider.commitTransaction(graph, tx);
//            } catch (Exception e) {
//                logger.info("Can't create index on table " + vertexLabel);
//            }
//            logger.info("Indexing done " + vertexLabel);
//        }).start();
    }

    public String addEntity(String jsonString) throws Exception {
        String entityId = "entityPlaceholderId";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonString);

        if (encryptionEnabled) {
            rootNode = encryptionHelper.getEncryptedJson(rootNode);
        }

        if (signatureEnabled) {
            signatureHelper.signJson(rootNode);
        }

        if (persistenceEnabled) {
            DatabaseProvider dbProvider = shard.getDatabaseProvider();
            String vertexLabel;
            try (OSGraph osGraph = dbProvider.getOSGraph()) {
                Graph graph = osGraph.getGraphStore();
                Transaction tx = dbProvider.startTransaction(graph);
                entityId = registryDao.addEntity(graph, rootNode);
                shard.getDatabaseProvider().commitTransaction(graph, tx);
                dbProvider.commitTransaction(graph, tx);
                vertexLabel = rootNode.fieldNames().next();
            }

            logger.info("Starting to ensure index on " + vertexLabel + " for shard " + shard.getShardId());
            addIndex(dbProvider, shard.getShardId(), vertexLabel);
        }

        return entityId;
    }

    /**
     * Ensures index for a vertex exists 
     * Unique index and non-unique index is supported
     * @param dbProvider
     * @param label   a type vertex label (example:Teacher)
     * @param shardId
     */
    private void ensureIndexExists(DatabaseProvider dbProvider, String shardId, String label) {

        Vertex parentVertex = entityParenter.getKnownParentVertex(label, shardId);
        Definition definition = definitionsManager.getDefinition(label);
        List<String> indexFields = definition.getOsSchemaConfiguration().getIndexFields();
        List<String> indexUniqueFields = definition.getOsSchemaConfiguration().getUniqueIndexFields();

        try {
            if (!indexFieldsExists(parentVertex, indexFields)){
                dbProvider.createIndex(label, indexFields);
                setPropertyValuesOnParentVertex(parentVertex, indexFields);
                logger.debug("Added index on "
                        + parentVertex.property(Constants.INDEX_FIELDS).value());

            }

            if(!indexFieldsExists(parentVertex, indexUniqueFields)){
                dbProvider.createUniqueIndex(label, indexUniqueFields);
                setPropertyValuesOnParentVertex(parentVertex, indexUniqueFields);
                logger.debug("Added unique index on "
                        + parentVertex.property(Constants.INDEX_FIELDS).value());

            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Can't ensure index" + e);
        }

    }
    
    /**
     * Checks if fields exist for parent vertex property
     * @param parentVertex
     * @param fields
     * @return
     */
    private boolean indexFieldsExists(Vertex parentVertex, List<String> fields) {
        String[] indexFields = null;
        boolean contains = false;
        if (parentVertex.property(Constants.INDEX_FIELDS).isPresent()) {
            String values = (String) parentVertex.property(Constants.INDEX_FIELDS).value();
            indexFields = values.split(",");
            for (String field : fields) {
                contains = Arrays.stream(indexFields).anyMatch(field::equals);
            }
        }
        return contains;
    }
    
    /**
     * Append the values to parent vertex INDEX_FIELDS property
     * @param parentVertex
     * @param values
     */
    private void setPropertyValuesOnParentVertex(Vertex parentVertex, List<String> values) {
        String existingValue = (String) parentVertex.property(Constants.INDEX_FIELDS).value();
        for (String value : values) {
            existingValue = existingValue.isEmpty() ? value : (existingValue + "," + value);
            parentVertex.property(Constants.INDEX_FIELDS, existingValue);
        }
    }

    @Override
    public JsonNode getEntity(String id, ReadConfigurator configurator) throws Exception {
        DatabaseProvider dbProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = dbProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            Transaction tx = dbProvider.startTransaction(graph);
            JsonNode result = registryDao.getEntity(graph, id, configurator);

            if (!shard.getShardLabel().isEmpty()) {
                // Replace osid with shard details
                String prefix = shard.getShardLabel() + RecordIdentifier.getSeparator();
                JSONUtil.addPrefix((ObjectNode) result, prefix, new ArrayList<String>(Arrays.asList(uuidPropertyName)));
            }

            shard.getDatabaseProvider().commitTransaction(graph, tx);
            dbProvider.commitTransaction(graph, tx);
            return result;
        }
    }

    /**
     * Removes the entity signature from the node
     * @param entityNodeType
     * @param node
     */
    private void removeEntitySignature(String entityNodeType, ObjectNode node) {
        ArrayNode signatureArr = (ArrayNode) node.get(entityNodeType).get(Constants.SIGNATURES_STR);
        int entitySignatureIdx = -1;
        for(int itr = 0; itr < signatureArr.size(); itr++) {
            JsonNode signature = signatureArr.get(itr);
            if (signature.get(Constants.SIGNATURE_FOR).toString().contains(entityNodeType)) {
                entitySignatureIdx = itr;
                break;
            }
        }
        if (entitySignatureIdx != -1) {
            signatureArr.remove(entitySignatureIdx);
        }
    }

    @Override
    public void updateEntity(String id, String jsonString) throws Exception {
        JsonNode inputNode = objectMapper.readTree(jsonString);

        if (encryptionEnabled) {
            inputNode = encryptionHelper.getEncryptedJson(inputNode);
        }

        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = databaseProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            Transaction tx = databaseProvider.startTransaction(graph);

            // Read the node and
            // TODO - decrypt properties to pass validation
            ReadConfigurator readConfigurator = ReadConfiguratorFactory.getForUpdateValidation();
            VertexReader vr = new VertexReader(databaseProvider, graph, readConfigurator, uuidPropertyName, definitionsManager);
            String entityNodeType;
            JsonNode readNode = vr.read(id);
            Vertex rootVertex = vr.getRootVertex();
            entityNodeType = readNode.fields().next().getKey();

            // Merge the new changes
            JsonNode mergedNode = mergeWrapper("/" + entityNodeType, (ObjectNode) readNode, (ObjectNode) inputNode);
            logger.debug("After merge the payload is " + mergedNode.toString());

            // Re-sign, i.e., remove and add entity signature again
            if (signatureEnabled) {
                logger.debug("Removing earlier signature and adding new one");
                removeEntitySignature(entityNodeType, (ObjectNode) mergedNode);
                signatureHelper.signJson(mergedNode);
            }

            // TODO - Validate before update
            //iValidate.validate(entityNodeType, mergedNode.toString());
            //logger.debug("Validated payload before update");

            // Finally update
            ((ObjectNode) mergedNode.get(entityNodeType)).put(uuidPropertyName, id);
            registryDao.updateVertex(graph, rootVertex, inputNode.get(entityNodeType));

            databaseProvider.commitTransaction(graph, tx);
        }
    }

    /**
     * filters entity sign node from the signatures json array
     *
     * @param signatures
     * @return
     */
    private JsonNode getEntitySignNode(JsonNode signatures, String registryRootEntityType) {
        JsonNode entitySignNode = null;
        Iterator<JsonNode> signItr = signatures.elements();
        while (signItr.hasNext()) {
            JsonNode signNode = signItr.next();
            if (signNode.get(Constants.SIGNATURE_FOR).asText().equals(registryRootEntityType)
                    && null == signNode.get(uuidPropertyName)) {
                entitySignNode = signNode;
                break;
            }
        }
        return entitySignNode;
    }

    /**
     * Merging input json node to DB entity node, this method in turn calls
     * mergeDestinationWithSourceNode method for deep copy of properties and
     * objects
     *
     * @param databaseNode - the one found in db
     * @param inputNode - the one passed by user
     * @return
     */
    private ObjectNode mergeWrapper(String entityType, ObjectNode databaseNode, ObjectNode inputNode) {
        // We know the user is likely to update less fields and so iterate over it.
        ObjectNode result = databaseNode.deepCopy();
        inputNode.fields().forEachRemaining(prop -> {
            merge(entityType, result, (ObjectNode) prop.getValue());
        });
        return result;
    }

    private void merge(String entityType, ObjectNode result, ObjectNode inputNode) {
        inputNode.fields().forEachRemaining(prop -> {
            String propKey = prop.getKey();
            JsonNode propValue = prop.getValue();

            if ((propValue.isValueNode() && !uuidPropertyName.equalsIgnoreCase(propKey)) ||
                propValue.isArray()) {
                // Must be a value node and not a uuidPropertyName key pair
                //((ObjectNode)result.get(entityType)).set(propKey, propValue);
                ((ObjectNode)result.at(entityType)).set(propKey, propValue);
            } else if (propValue.isObject()) {
                merge(entityType + "/" + propKey, result, (ObjectNode) propValue);
            }
        });
    }
}