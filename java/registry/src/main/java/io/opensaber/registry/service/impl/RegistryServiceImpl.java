package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import io.opensaber.pojos.ComponentHealthInfo;
import io.opensaber.pojos.HealthCheckResponse;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.dao.RegistryDaoImpl;
import io.opensaber.registry.dao.VertexReader;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.*;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.util.DefinitionsManager;
import io.opensaber.registry.util.ReadConfigurator;
import io.opensaber.validators.IValidate;
import org.apache.tinkerpop.gremlin.structure.Direction;
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
    RegistryDaoImpl tpGraphMain;

    @Autowired
    DBConnectionInfoMgr dbConnectionInfoMgr;

    @Autowired
    private IValidate iValidate;

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
                if (!(vertex.property(Constants.STATUS_KEYWORD).isPresent() && vertex.property(Constants.STATUS_KEYWORD).value().equals(Constants.STATUS_INACTIVE))) {
                    tpGraphMain.deleteEntity(vertex);
                    tx.commit();
                } else {
                    //throw exception node already deleted
                    throw new RecordNotFoundException("Cannot perform the operation");
                }
            } else {
                throw new RecordNotFoundException("No such record found");
            }

            databaseProvider.commitTransaction(graph, tx);
        }
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
            try (OSGraph osGraph = dbProvider.getOSGraph()) {
                Graph graph = osGraph.getGraphStore();
                Transaction tx = dbProvider.startTransaction(graph);
                entityId = tpGraphMain.addEntity(graph, rootNode);
                shard.getDatabaseProvider().commitTransaction(graph, tx);
                dbProvider.commitTransaction(graph, tx);
            }
        }

        return entityId;
    }

    @Override
    public JsonNode getEntity(String id, ReadConfigurator configurator) throws Exception {
        DatabaseProvider dbProvider = shard.getDatabaseProvider();
        try (OSGraph osGraph = dbProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            Transaction tx = dbProvider.startTransaction(graph);
            JsonNode result = tpGraphMain.getEntity(graph, id, configurator);
            shard.getDatabaseProvider().commitTransaction(graph, tx);
            dbProvider.commitTransaction(graph, tx);
            return result;
        }
    }

    @Override
    public void updateEntity(String id, String jsonString) throws Exception {
        Iterator<Vertex> vertexIterator = null;
        Vertex inputNodeVertex = null;
        Vertex rootVertex = null;

        JsonNode rootNode = objectMapper.readTree(jsonString);

        if (encryptionEnabled) {
            rootNode = encryptionHelper.getEncryptedJson(rootNode);
        }

        JsonNode childElementNode = rootNode.elements().next();
        DatabaseProvider databaseProvider = shard.getDatabaseProvider();
        ReadConfigurator readConfigurator = new ReadConfigurator();
        readConfigurator.setIncludeSignatures(signatureEnabled);

        try (OSGraph osGraph = databaseProvider.getOSGraph()) {
            Graph graph = osGraph.getGraphStore();
            Transaction tx = databaseProvider.startTransaction(graph);
            VertexReader vr = new VertexReader(databaseProvider, graph, readConfigurator, uuidPropertyName, definitionsManager);
            String entityNodeType;

            if (null != tx) {
                ObjectNode entityNode = null;
                vertexIterator = graph.vertices(id);
                inputNodeVertex = vertexIterator.hasNext() ? vertexIterator.next() : null;
                if ((inputNodeVertex.property(Constants.STATUS_KEYWORD).isPresent() &&
                        inputNodeVertex.property(Constants.STATUS_KEYWORD).value().equals(Constants.STATUS_INACTIVE))) {
                    throw new RecordNotFoundException("Cannot perform the operation");
                }
                if (inputNodeVertex.property(Constants.ROOT_KEYWORD).isPresent()) {
                    rootVertex = graph.vertices(inputNodeVertex.property(Constants.ROOT_KEYWORD).value()).next();
                } else {
                    rootVertex = inputNodeVertex;
                }

                entityNode = (ObjectNode) vr.read(rootVertex.id().toString());

                //merge with entitynode
                entityNode = merge(entityNode, rootNode);
                entityNodeType = entityNode.fields().next().getKey();
                //TO-DO validation is failing
                //boolean isValidate = iValidate.validate("Teacher",entityNode.toString());
                tpGraphMain.updateVertex(graph, inputNodeVertex, childElementNode);
                //sign the entitynode
                if (signatureEnabled) {
                    signatureHelper.signJson(entityNode);
                    JsonNode signNode = entityNode.get(entityNodeType).get(Constants.SIGNATURES_STR);
                    if (signNode.isArray()) {
                        signNode = getEntitySignNode(signNode, entityNodeType);
                    }

                    Iterator<Vertex> vertices = rootVertex.vertices(Direction.IN, Constants.SIGNATURES_STR);
                    if (null != vertices && vertices.hasNext()) {
                        Vertex signArrayNode = vertices.next();
                        Iterator<Vertex> sign = signArrayNode.vertices(Direction.OUT, entityNodeType);
                        Vertex signVertex = sign.next();
                        // Other signatures are not updated, only the entity level signature.
                        tpGraphMain.updateVertex(graph, signVertex, signNode);
                    }
                }
                databaseProvider.commitTransaction(graph, tx);
            } else {
                // TinkerGraph section for test cases
                vertexIterator = graph.vertices(new Long(id));
                inputNodeVertex = vertexIterator.hasNext() ? vertexIterator.next() : null;
                ObjectNode entityNode = (ObjectNode) vr.read(inputNodeVertex.id().toString());
                entityNode = merge(entityNode, rootNode);
                entityNodeType = entityNode.fields().next().getKey();

                //TO-DO validation is failing
                // boolean isValidate = iValidate.validate("Teacher",entityNode.toString());
                tpGraphMain.updateVertex(graph, inputNodeVertex, childElementNode);

                //sign the entitynode
                if (signatureEnabled) {
                    signatureHelper.signJson(entityNode);
                    JsonNode signNode = entityNode.get(entityNodeType).get(Constants.SIGNATURES_STR);
                    if (signNode.isArray()) {
                        signNode = getEntitySignNode(signNode, entityNodeType);
                    }
                    Iterator<Vertex> vertices = rootVertex.vertices(Direction.IN, Constants.SIGNATURES_STR);
                    while (null != vertices && vertices.hasNext()) {
                        Vertex signVertex = vertices.next();
                        if (signVertex.property(Constants.SIGNATURE_FOR).value().equals(entityNodeType)) {
                            tpGraphMain.updateVertex(graph, signVertex, signNode);
                        }
                    }

                }
            }
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
            if (signNode.get(Constants.SIGNATURE_FOR).asText().equals(registryRootEntityType) &&
                    null == signNode.get(uuidPropertyName)) {
                entitySignNode = signNode;
                break;
            }
        }
        return entitySignNode;
    }

    /**
     * Merging input json node to DB entity node, this method in turn calls mergeDestinationWithSourceNode method for deep copy of properties and objects
     *
     * @param entityNode
     * @param rootNode
     * @return
     */
    private ObjectNode merge(ObjectNode entityNode, JsonNode rootNode) {
        rootNode.fields().forEachRemaining(entryJsonNode -> {
            ObjectNode propKeyValue = (ObjectNode) entryJsonNode.getValue();
            mergeDestinationWithSourceNode(propKeyValue, entityNode, entryJsonNode.getKey());
        });
        return entityNode;
    }

    /**
     * @param propKeyValue - user given entity node
     * @param entityNode   - read from the database
     * @param entityKey    - user given entity key (wrapper node supplied by the user)
     */
    private void mergeDestinationWithSourceNode(ObjectNode propKeyValue, ObjectNode entityNode, String entityKey) {
        ObjectNode subEntity = (ObjectNode) entityNode.findValue(entityKey);
        propKeyValue.fields().forEachRemaining(prop -> {
            String propKey = prop.getKey();
            JsonNode propValue = prop.getValue();
            if (propValue.isValueNode() && !uuidPropertyName.equalsIgnoreCase(propKey)) {
                subEntity.set(propKey, propValue);
            } else if (propValue.isObject()) {
                if (subEntity.get(propKey).size() == 0) {
                    subEntity.set(propKey, propValue);
                } else if (subEntity.get(propKey).isObject()) {
                    //As of now filtering only @type
                    List<String> filterKeys = Arrays.asList(Constants.JsonldConstants.TYPE);
                    //removing keys with name osid and type
                    JSONUtil.removeNodes((ObjectNode) subEntity.get(propKey), filterKeys);
                    //constructNewNodeToParent
                    subEntity.set(propKey, propValue);
                }
            } else if (subEntity.get(propKey).isArray()) {
                List<String> filterKeys = Arrays.asList(Constants.JsonldConstants.TYPE);
                propValue.forEach(arrayElement -> {
                    //removing keys with name @type
                    JSONUtil.removeNodes((ObjectNode) arrayElement, filterKeys);
                });
                //constructNewNodeToParent
                subEntity.set(propKey, propValue);
            }
        });
    }

}