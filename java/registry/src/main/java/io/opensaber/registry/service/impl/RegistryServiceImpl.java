package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.core.JsonLdError;
import com.google.gson.Gson;
import io.opensaber.converters.JenaRDF4J;
import io.opensaber.pojos.ComponentHealthInfo;
import io.opensaber.pojos.HealthCheckResponse;
import io.opensaber.registry.dao.RegistryDao;
import io.opensaber.registry.dao.VertexReader;
import io.opensaber.registry.exception.*;
import io.opensaber.registry.middleware.MiddlewareHaltException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.middleware.util.RDFUtil;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.model.RegistrySignature;
import io.opensaber.registry.schema.configurator.ISchemaConfigurator;
import io.opensaber.registry.service.EncryptionHelper;
import io.opensaber.registry.service.EncryptionService;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.service.SignatureService;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.DatabaseProviderWrapper;
import io.opensaber.registry.util.GraphDBFactory;
import io.opensaber.registry.dao.TPGraphMain;
import io.opensaber.registry.util.ReadConfigurator;
import io.opensaber.utils.converters.RDF2Graph;
import io.opensaber.validators.IValidate;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.ext.com.google.common.io.ByteStreams;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.WriterDatasetRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.tinkerpop.gremlin.structure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.spi.ObjectFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class RegistryServiceImpl implements RegistryService {

    private static final String ID_REGEX = "\"@id\"\\s*:\\s*\"_:[a-z][0-9]+\",";
    private static Logger logger = LoggerFactory.getLogger(RegistryServiceImpl.class);
    private DatabaseProvider databaseProvider;

    @Autowired
    EncryptionService encryptionService;
    @Autowired
    SignatureService signatureService;
    @Autowired
    Gson gson;
    @Autowired
    private RegistryDao registryDao;
    @Autowired
    private ISchemaConfigurator schemaConfigurator;
    @Autowired
    private EncryptionHelper encryptionHelper;
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

    @Value("${registry.rootEntity.type}")
    private String registryRootEntityType;

    @Value("${registry.context.base}")
    private String registryContext;

    @Autowired
    TPGraphMain tpGraphMain;

    @Autowired
    DatabaseProviderWrapper databaseProviderWrapper;
    
    @Autowired
    DBConnectionInfoMgr dbConnectionInfoMgr;

    @Autowired
    private IValidate iValidate;

    @Override
    public List getEntityList() {
        return registryDao.getEntityList();
    }

    @Override
    public String addEntity(Model rdfModel, String dataObject, String subject, String property)
            throws DuplicateRecordException, EntityCreationException, EncryptionException, AuditFailedException,
            MultipleEntityException, RecordNotFoundException, IOException, SignatureException.UnreachableException,
            JsonLdError, SignatureException.CreationException {
        try {
            Model signedRdfModel = null;
            RegistrySignature rs = new RegistrySignature();
            String rootLabel = null;

            if (signatureEnabled) {
                Map signReq = new HashMap<String, Object>();
                InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
                String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
                Map<String, Object> reqMap = JSONUtil.frameJsonAndRemoveIds(ID_REGEX, dataObject, gson,
                        fileString);
                signReq.put("entity", reqMap);
                Map<String, Object> entitySignMap = (Map<String, Object>) signatureService.sign(signReq);
                entitySignMap.put("createdDate", rs.getCreatedTimestamp());
                entitySignMap.put("keyUrl", signatureKeyURl);
                signedRdfModel = RDFUtil.getUpdatedSignedModel(rdfModel, registryContext, signatureDomain,
                        entitySignMap, ModelFactory.createDefaultModel());
                rootLabel = addEntity(signedRdfModel, subject, property);

            } else {
                rootLabel = addEntity(rdfModel, subject, property);
            }
            return rootLabel;

        } catch (EntityCreationException | EncryptionException | AuditFailedException | DuplicateRecordException
                | MultipleEntityException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Exception when creating entity: ", ex);
            throw ex;
        }
    }

    @Override
    public String addEntity(Model rdfModel, String subject, String property)
            throws DuplicateRecordException, EntityCreationException, EncryptionException, AuditFailedException,
            MultipleEntityException, RecordNotFoundException {
        try {
            Resource root = getRootNode(rdfModel);
            String label = getRootLabel(root);
            if (encryptionEnabled) {
                encryptModel(rdfModel);
            }
            Graph graph = generateGraphFromRDF(rdfModel);

            // Append _: to the root node label to create the entity as Apache
            // Jena removes the _: for the root node label
            // if it is a blank node
            String id = "entityIdPlaceholder";
            if (persistenceEnabled) {
                id = registryDao.addEntity(graph, label, subject, property);
            }
            return id;
        } catch (EntityCreationException | EncryptionException | AuditFailedException | DuplicateRecordException
                | MultipleEntityException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Exception when creating entity: ", ex);
            throw ex;
        }
    }

    @Override
    public boolean updateEntity(Model entity) throws RecordNotFoundException, EntityCreationException,
            EncryptionException, AuditFailedException, MultipleEntityException, SignatureException.UnreachableException,
            IOException, SignatureException.CreationException, UpdateException {
        boolean isUpdated = false;
        if (persistenceEnabled) {
            Resource root = getRootNode(entity);
            String label = getRootLabel(root);
            String rootType = getTypeForRootLabel(entity, root);
            String actualNodeType = registryDao.getTypeForNodeLabel(label);
            if (rootType.equalsIgnoreCase(actualNodeType)) {
                if (encryptionEnabled) {
                    encryptModel(entity);
                }
                Graph graph = generateGraphFromRDF(entity);
                logger.debug("Service layer graph :", graph);
                isUpdated = registryDao.updateEntity(graph, label, Constants.UPDATE_METHOD_ORIGIN);
                if (signatureEnabled) {
                    if (!rootType.equalsIgnoreCase(registryContextBase + registryRootEntityType)) {
                        label = registryDao.getRootLabelForNodeLabel(label);
                    }
                    getEntityAndUpdateSign(label);
                }
            } else {
                throw new UpdateException(Constants.NODE_TYPE_WRONG_MAPPED_ERROR_MSG);
            }
        }
        return isUpdated;
    }

    /**
     * This method will get entity details and sign the entity and will update
     *
     * @param label
     * @throws EncryptionException
     * @throws AuditFailedException
     * @throws RecordNotFoundException
     * @throws EntityCreationException
     * @throws IOException
     * @throws MultipleEntityException
     * @throws SignatureException.UnreachableException
     * @throws SignatureException.CreationException
     */
    void getEntityAndUpdateSign(String label) throws EncryptionException, AuditFailedException, RecordNotFoundException,
            EntityCreationException, IOException, MultipleEntityException, SignatureException.UnreachableException,
            SignatureException.CreationException {
        final String ID_REGEX = "\"@id\"\\s*:\\s*\"[a-z]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\",";
        Map signReq = new HashMap<String, Object>();
        RegistrySignature rs = new RegistrySignature();
        Model jenaEntityModel = getEntityById(label, true);
        // remove sign part from model
        Model signatureModel = RDFUtil.removeAndRetrieveSignature(jenaEntityModel, registryContextBase);
        String jenaJSON = frameEntity(jenaEntityModel);
        signReq.put("entity", JSONUtil.getStringWithReplacedText(jenaJSON, ID_REGEX, StringUtils.EMPTY));
        Map<String, Object> entitySignMap = (Map<String, Object>) signatureService.sign(signReq);
        entitySignMap.put("createdDate", rs.getCreatedTimestamp());
        entitySignMap.put("keyUrl", signatureKeyURl);
        Graph graph = generateGraphFromRDF(RDFUtil.getUpdatedSignedModel(jenaEntityModel, registryContextBase,
                signatureDomain, entitySignMap, signatureModel));
        registryDao.updateEntity(graph, label, "update");
    }

    /**
     * Optionally gets signatures along with other information.
     *
     * @param label
     * @param includeSignatures
     * @return
     * @throws RecordNotFoundException
     * @throws EncryptionException
     * @throws AuditFailedException
     */
    @Override
    public Model getEntityById(String label, boolean includeSignatures)
            throws RecordNotFoundException, EncryptionException, AuditFailedException {
        Graph graph = registryDao.getEntityById(label, includeSignatures);
        org.eclipse.rdf4j.model.Model model = RDF2Graph.convertGraph2RDFModel(graph, label);
        Model jenaEntityModel = JenaRDF4J.asJenaModel(model);
        if (encryptionEnabled) {
            decryptModel(jenaEntityModel);
        }
        logger.debug("RegistryServiceImpl : rdf4j model :", model);
        return jenaEntityModel;
    }

    public HealthCheckResponse health() throws Exception {
        HealthCheckResponse healthCheck;
        // TODO
        boolean databaseServiceup = databaseProvider.isDatabaseServiceUp();
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

    @Override
    public String frameEntity(Model jenaEntityModel)
            throws IOException, MultipleEntityException, EntityCreationException {
        Resource root = getRootNode(jenaEntityModel);
        String rootLabelType = getTypeForRootLabel(jenaEntityModel, root);
        logger.debug("RegistryServiceImpl : jenaEntityModel for framing: {} \n root : {}, \n rootLabelType: {}",
                jenaEntityModel, root, rootLabelType);
        DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();
        JsonLDWriteContext ctx = new JsonLDWriteContext();
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
        String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
        fileString = fileString.replace("<@type>", rootLabelType);
        ctx.setFrame(fileString);
        WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT);
        PrefixMap pm = RiotLib.prefixMap(g);
        String base = null;
        StringWriter sWriterJena = new StringWriter();
        w.write(sWriterJena, g, pm, base, ctx);
        String jenaJSON = sWriterJena.toString();
        logger.debug("RegistryServiceImpl : jenaJSON for framing : {}", jenaJSON);
        return jenaJSON;
    }

    @Override
    public boolean deleteEntityById(String id) throws AuditFailedException, RecordNotFoundException {
        boolean isDeleted = registryDao.deleteEntityById(id);
        if (!isDeleted) {
            throw new UnsupportedOperationException(Constants.DELETE_UNSUPPORTED_OPERATION_ON_ENTITY);
        }
        return isDeleted;
    }

    private Graph generateGraphFromRDF(Model entity) throws EntityCreationException, MultipleEntityException {
        Graph graph = GraphDBFactory.getEmptyGraph();
        StmtIterator iterator = entity.listStatements();
        while (iterator.hasNext()) {
            Statement rdfStatement = iterator.nextStatement();
            org.eclipse.rdf4j.model.Statement rdf4jStatement = JenaRDF4J.asrdf4jStatement(rdfStatement);
            graph = RDF2Graph.convertRDFStatement2Graph(rdf4jStatement, graph, registryContextBase);
        }
        return graph;
    }

    private String getRootLabel(Resource subject) {
        String label = subject.toString();
        if (subject.isAnon() && subject.getURI() == null) {
            label = String.format("_:%s", label);
        }
        return label;
    }

    private Resource getRootNode(Model entity) throws EntityCreationException, MultipleEntityException {
        List<Resource> rootLabels = RDFUtil.getRootLabels(entity);
        if (rootLabels.size() == 0) {
            throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
        } else if (rootLabels.size() > 1) {
            throw new MultipleEntityException(Constants.ADD_UPDATE_MULTIPLE_ENTITIES_MESSAGE);
        } else {
            return rootLabels.get(0);
        }
    }

    private String getTypeForRootLabel(Model entity, Resource root)
            throws EntityCreationException, MultipleEntityException {
        List<String> rootLabelType = RDFUtil.getTypeForSubject(entity, root);
        if (rootLabelType.size() == 0) {
            throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
        } else if (rootLabelType.size() > 1) {
            throw new MultipleEntityException(Constants.ADD_UPDATE_MULTIPLE_ENTITIES_MESSAGE);
        } else {
            return rootLabelType.get(0);
        }
    }

    private void encryptModel(Model rdfModel) throws EncryptionException {
        setModelWithEncryptedOrDecryptedAttributes(rdfModel, true);
    }

    private void decryptModel(Model rdfModel) throws EncryptionException {
        setModelWithEncryptedOrDecryptedAttributes(rdfModel, false);
    }

    private void setModelWithEncryptedOrDecryptedAttributes(Model rdfModel, boolean isEncryptionRequired)
            throws EncryptionException {
        List<String> privateProperties = schemaConfigurator.getAllPrivateProperties();

        Map<Resource, Map<String, Object>> toBeEncryptedOrDecryptedAttributes = new HashMap<Resource, Map<String, Object>>();
        TypeMapper tm = TypeMapper.getInstance();
        for (String propertyName : privateProperties) {
            RDFNode node = ResourceFactory.createResource(propertyName);// nodeIter.next();
            String predicateStr = node.toString();
            Property predicate = null;
            if (!isEncryptionRequired) {
                String tailOfPredicateStr = predicateStr.substring(predicateStr.lastIndexOf("/") + 1).trim();
                predicateStr = predicateStr.replace(tailOfPredicateStr, "encrypted" + tailOfPredicateStr);
            }
            predicate = ResourceFactory.createProperty(predicateStr);
            StmtIterator stmtIter = rdfModel.listStatements(null, predicate, (RDFNode) null);
            while (stmtIter.hasNext()) {
                Statement s = stmtIter.next();
                Map<String, Object> propertyMap = new HashMap<String, Object>();
                if (toBeEncryptedOrDecryptedAttributes.containsKey(s.getSubject())) {
                    propertyMap = toBeEncryptedOrDecryptedAttributes.get(s.getSubject());
                }
                if (propertyMap.containsKey(predicateStr)) {
                    Object value = propertyMap.get(predicateStr);
                    List valueList = new ArrayList();
                    if (value instanceof List) {
                        valueList = (List) value;
                    }
                    valueList.add(s.getObject().asLiteral().getLexicalForm());
                }
                propertyMap.put(predicateStr, s.getObject().asLiteral().getLexicalForm());
                toBeEncryptedOrDecryptedAttributes.put(s.getSubject(), propertyMap);
            }
        }
        for (Map.Entry<Resource, Map<String, Object>> entry : toBeEncryptedOrDecryptedAttributes.entrySet()) {
            Map<String, Object> listPropertyMap = new HashMap<String, Object>();
            Map<String, Object> propertyMap = entry.getValue();
            entry.getValue().forEach((k, v) -> {
                if (v instanceof List) {
                    listPropertyMap.put(k, v);
                }
            });
            listPropertyMap.forEach((k, v) -> propertyMap.remove(k));
            Map<String, Object> encAttributes = new HashMap<String, Object>();
            if (isEncryptionRequired) {
                encAttributes = encryptionService.encrypt(propertyMap);
            } else {
                encAttributes = encryptionService.decrypt(propertyMap);
            }
            for (Map.Entry<String, Object> listEntry : listPropertyMap.entrySet()) {
                Object v = entry.getValue();
                List values = (List) v;
                List encValues = new ArrayList();
                String encDecValue = null;
                for (Object listV : values) {
                    if (isEncryptionRequired) {
                        encDecValue = encryptionService.encrypt(listV);
                    } else {
                        encDecValue = encryptionService.decrypt(listV);
                    }
                    encValues.add(encDecValue);
                }
                listEntry.setValue(encValues);
            }
            encAttributes.putAll(listPropertyMap);
            Resource encSubject = entry.getKey();
            for (Map.Entry<String, Object> propEntry : encAttributes.entrySet()) {
                Property predicate = ResourceFactory.createProperty(propEntry.getKey());
                StmtIterator stmtIter = rdfModel.listStatements(entry.getKey(), predicate, (RDFNode) null);
                List<Statement> stmtList = stmtIter.toList();
                if (stmtList.size() > 0) {
                    String datatype = stmtList.get(0).getObject().asLiteral().getDatatypeURI();
                    RDFDatatype rdt = tm.getSafeTypeByName(datatype);
                    rdfModel.remove(stmtList);
                    String predicateStr = predicate.toString();
                    String tailOfPredicateStr = predicateStr.substring(predicateStr.lastIndexOf("/") + 1).trim();
                    if (isEncryptionRequired) {
                        predicateStr = predicateStr.replace(tailOfPredicateStr, "encrypted" + tailOfPredicateStr);
                    } else {
                        if (schemaConfigurator.isEncrypted(tailOfPredicateStr)) {
                            predicateStr = predicateStr.replace(tailOfPredicateStr, tailOfPredicateStr.substring(9));
                        }
                    }
                    Property encPredicate = ResourceFactory.createProperty(predicateStr);
                    if (propEntry.getValue() instanceof List) {
                        List values = (List) propEntry.getValue();
                        for (Object value : values) {
                            Literal literal = ResourceFactory.createTypedLiteral((String) value, rdt);
                            rdfModel.add(encSubject, encPredicate, literal);
                        }
                    } else {
                        Literal literal = ResourceFactory.createTypedLiteral((String) propEntry.getValue(), rdt);
                        rdfModel.add(encSubject, encPredicate, literal);
                    }
                }
            }
        }
    }

    @Override
    public String getEntityFramedById(String id, boolean includeSignatures) throws RecordNotFoundException,
            EncryptionException, AuditFailedException, IOException, MultipleEntityException, EntityCreationException {
        Graph graph = registryDao.getEntityById(id, includeSignatures);
        org.eclipse.rdf4j.model.Model model = RDF2Graph.convertGraph2RDFModel(graph, id);
        logger.debug("RegistryServiceImpl : rdf4j model :", model);
        Model jenaEntityModel = JenaRDF4J.asJenaModel(model);
        if (encryptionEnabled) {
            decryptModel(jenaEntityModel);
        }
        return frameEntity(jenaEntityModel);
    }

    // Presently only existing for test cases, must be removed.
    @Override
    public void setDatabaseProvider(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
        registryDao.setDatabaseProvider(databaseProvider);
    }

    public String addEntity(String shardId, String jsonString) throws Exception {
        String entityId = "";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonString);
        rootNode = encryptionHelper.getEncryptedJson(rootNode);

        entityId = tpGraphMain.addEntity(shardId, rootNode);

        return entityId;
    }

    public JsonNode getEntity(String id, ReadConfigurator configurator) {
        JsonNode result = tpGraphMain.getEntity("", id, configurator);
        return result;
    }

    @Override
    public void updateEntity(String jsonString) throws Exception {
        Iterator<Vertex> vertexIterator = null;
        Vertex rootVertex = null;
        List<String> privatePropertyList = schemaConfigurator.getAllPrivateProperties();

        JsonNode rootNode = objectMapper.readTree(jsonString);
        rootNode = encryptionHelper.getEncryptedJson(rootNode);
        String idProp = rootNode.elements().next().get("id").asText();
        JsonNode childElementNode = rootNode.elements().next();
        DatabaseProvider databaseProvider = databaseProviderWrapper.getDatabaseProvider();
        Graph graph = databaseProvider.getGraphStore();
        ReadConfigurator readConfigurator = new ReadConfigurator();
        readConfigurator.setIncludeSignatures(false);
        VertexReader vr = new VertexReader(graph, readConfigurator, uuidPropertyName, privatePropertyList);
        boolean isTransactionEnabled = databaseProvider.supportsTransaction(graph);
        if(isTransactionEnabled){
            try (Transaction tx = graph.tx()) {
                vertexIterator = graph.vertices(idProp);
                rootVertex = vertexIterator.hasNext() ? vertexIterator.next(): null;
                ObjectNode entityNode = (ObjectNode) vr.read(rootVertex.id().toString());
                //merge with entitynode
                entityNode =  merge(entityNode,rootNode);
                //TO-DO validation is failing
                boolean isValidate = iValidate.validate(entityNode.toString(),"Teacher");
                tpGraphMain.updateVertex(rootVertex,childElementNode);
                tx.commit();
            }
        } else {
            vertexIterator = graph.vertices(new Long(idProp));
            rootVertex = vertexIterator.hasNext() ? vertexIterator.next(): null;
            ObjectNode entityNode = (ObjectNode) vr.read(rootVertex.id().toString());
            entityNode =  merge(entityNode,rootNode);
            //TO-DO validation is failing
            boolean isValidate = iValidate.validate(entityNode.toString(),"Teacher");
            tpGraphMain.updateVertex(rootVertex,childElementNode);
        }
    }

    /** Merging input json node to DB entity node, this method in turn calls mergeDestinationWithSourceNode method for deep copy of properties and objects
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
     * @param propKeyValue
     * @param entityNode
     * @param entityKey
     */
    private void mergeDestinationWithSourceNode(ObjectNode propKeyValue, ObjectNode entityNode, String entityKey) {
        ObjectNode subEntity = (ObjectNode) entityNode.get(entityKey);
        propKeyValue.fields().forEachRemaining(prop -> {
            if(prop.getValue().isValueNode()){
                subEntity.set(prop.getKey(),prop.getValue());
            } else if(prop.getValue().isObject()){
                if(subEntity.get(prop.getKey()).size() == 0) {
                    subEntity.set(prop.getKey(),prop.getValue());
                } else if (subEntity.get(prop.getKey()).isObject()) {
                    ArrayNode arrnode = JsonNodeFactory.instance.arrayNode();
                    arrnode.add(subEntity.get(prop.getKey()));
                    arrnode.add(prop.getValue());
                    subEntity.set(prop.getKey(),arrnode);
                }
            }
        });
    }

}