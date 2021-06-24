package io.opensaber.registry.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.flipkart.zjsonpatch.JsonPatch;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.middleware.MiddlewareHaltException;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.middleware.util.OSSystemFields;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.*;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.sink.shard.ShardManager;
import io.opensaber.registry.util.*;
import io.opensaber.validators.IValidate;
import io.opensaber.validators.ValidationException;
import io.opensaber.views.ViewTemplate;
import io.opensaber.views.ViewTransformer;

import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * This is helper class, user-service calls this class in-order to access registry functionality
 */
@Component
public class RegistryHelper {

    private static Logger logger = LoggerFactory.getLogger(RegistryHelper.class);

    @Autowired
    private ShardManager shardManager;

    @Autowired
    RegistryService registryService;

    @Autowired
    IReadService readService;

    @Autowired
    IValidate validationService;

    @Autowired
    private ISearchService searchService;

    @Autowired
    private ViewTemplateManager viewTemplateManager;

    @Autowired
    EntityStateHelper entityStateHelper;

    @Autowired
    private KeycloakAdminUtil keycloakAdminUtil;

    @Autowired
    private DefinitionsManager definitionsManager;

    @Autowired
    private DBConnectionInfoMgr dbConnectionInfoMgr;

    @Autowired
    private DecryptionHelper decryptionHelper;

    @Autowired
    private OpenSaberInstrumentation watch;

    @Autowired
    private ObjectMapper objectMapper;
        
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Value("${audit.frame.suffix}")
    public String auditSuffix;

    @Value("${audit.frame.suffixSeparator}")
    public String auditSuffixSeparator;

    @Value("${conditionalAccess.internal}")
    private String internalFieldsProp;

    @Value("${conditionalAccess.private}")
    private String privateFieldsProp;
    /**
     * calls validation and then persists the record to registry.
     * @param inputJson
     * @return
     * @throws Exception
     */
    public String addEntity(JsonNode inputJson, String userId) throws Exception {
        String entityType = inputJson.fields().next().getKey();
        validationService.validate(entityType, objectMapper.writeValueAsString(inputJson));
        ObjectNode existingNode = objectMapper.createObjectNode();
        existingNode.set(entityType, objectMapper.createObjectNode());
        entityStateHelper.changeStateAfterUpdate(existingNode, inputJson);
        return addEntity(inputJson, userId, entityType);
    }

    public String inviteEntity(JsonNode inputJson, String userId) throws Exception {
        String entityType = inputJson.fields().next().getKey();
        validationService.validateIgnoreRequired(entityType, objectMapper.writeValueAsString(inputJson));

        String entitySubjectPath = definitionsManager.getSubjectPath(entityType);
        JsonNode entitySubjectNode = inputJson.get(entityType).findPath(entitySubjectPath);
        if (entitySubjectNode.isMissingNode()) {
            throw new ValidationException("Missing required field for invitation: " + entitySubjectPath);
        }

        String entitySubject = entitySubjectNode.asText();
        String owner = keycloakAdminUtil.createUser(entitySubject, entityType);
        OSSystemFields.osOwner.setOsOwner(inputJson.get(entityType), owner);

        return addEntity(inputJson, userId, entityType);
    }

    private String addEntity(JsonNode inputJson, String userId, String entityType) throws Exception {
        RecordIdentifier recordId = null;
        try {
            logger.info("Add api: entity type: {} and shard propery: {}", entityType, shardManager.getShardProperty());
            Shard shard = shardManager.getShard(inputJson.get(entityType).get(shardManager.getShardProperty()));
            watch.start("RegistryController.addToExistingEntity");
            String resultId = registryService.addEntity(shard,userId,inputJson);
            recordId = new RecordIdentifier(shard.getShardLabel(), resultId);
            watch.stop("RegistryController.addToExistingEntity");
            logger.info("AddEntity,{}", recordId.toString());
        } catch (Exception e) {
            logger.error("Exception in controller while adding entity !", e);
            throw new Exception(e);
        }
        return recordId.toString();
    }

    /**
     * Get entity details from the DB and modifies data according to view template
     * @param inputJson
     * @param requireLDResponse
     * @return
     * @throws Exception
     */
    public JsonNode readEntity(JsonNode inputJson, String userId, boolean requireLDResponse) throws Exception {
        logger.debug("readEntity starts");
        String entityType = inputJson.fields().next().getKey();
        String label = inputJson.get(entityType).get(dbConnectionInfoMgr.getUuidPropertyName()).asText();
        boolean includeSignatures = inputJson.get(entityType).get("includeSignatures") != null;

        return readEntity(userId, entityType, label, includeSignatures, viewTemplateManager.getViewTemplate(inputJson), requireLDResponse);

    }

    public JsonNode readEntity(String userId, String entityType, String label, boolean includeSignatures, ViewTemplate viewTemplate, boolean requireLDResponse) throws Exception {
        boolean includePrivateFields =  false;
        JsonNode resultNode = null;
        RecordIdentifier recordId = RecordIdentifier.parse(label);
        String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
        Shard shard = shardManager.activateShard(shardId);
        logger.info("Read Api: shard id: " + recordId.getShardLabel() + " for label: " + label);
        ReadConfigurator configurator = ReadConfiguratorFactory.getOne(includeSignatures);
        configurator.setIncludeTypeAttributes(requireLDResponse);
        if (viewTemplate != null) {
            includePrivateFields = viewTemplateManager.isPrivateFieldEnabled(viewTemplate,entityType);
        }
        configurator.setIncludeEncryptedProp(includePrivateFields);
        resultNode =  readService.getEntity(shard, userId, recordId.getUuid(), entityType, configurator);
        if (!isOwner(resultNode.get(entityType), userId)) {
//            throw new Exception("Unauthorized");
            //TODO: return public fields
        }
        if (viewTemplate != null) {
            ViewTransformer vTransformer = new ViewTransformer();
            resultNode = includePrivateFields ? decryptionHelper.getDecryptedJson(resultNode) : resultNode;
            resultNode = vTransformer.transform(viewTemplate, resultNode);
        }
        logger.debug("readEntity ends");
        return resultNode;
    }

    private boolean isOwner(JsonNode entity, String userId) {
        String osOwner = OSSystemFields.osOwner.toString();
        return !entity.has(osOwner) || entity.get(osOwner).asText().equals(userId);
    }

    /**
     * Get entity details from the DB and modifies data according to view template, requests which need only json format can call this method
     * @param inputJson
     * @return
     * @throws Exception
     */
    public JsonNode readEntity(JsonNode inputJson, String userId) throws Exception {
        return readEntity(inputJson,userId,false);
    }

    /** Search the input in the configured backend, external api's can use this method for searching
     * @param inputJson
     * @return
     * @throws Exception
     */
    public JsonNode searchEntity(JsonNode inputJson) throws Exception {
        logger.debug("searchEntity starts");
        JsonNode resultNode = searchService.search(inputJson);
        removeNonPublicFields((ObjectNode) resultNode);
        ViewTemplate viewTemplate = viewTemplateManager.getViewTemplate(inputJson);
        if (viewTemplate != null) {
            ViewTransformer vTransformer = new ViewTransformer();
            resultNode = vTransformer.transform(viewTemplate, resultNode);
        }
        // Search is tricky to support LD. Needs a revisit here.
        logger.debug("searchEntity ends");
        return resultNode;
    }

    private void removeNonPublicFields(ObjectNode searchResultNode) throws Exception {
        ObjectReader stringListReader = objectMapper.readerFor(new TypeReference<List<String>>() {});
        List<String> nonPublicNodePathContainers = Arrays.asList(internalFieldsProp, privateFieldsProp);
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = searchResultNode.fields();
        while (fieldIterator.hasNext()) {
            ArrayNode entityResults = (ArrayNode) fieldIterator.next().getValue();
            for(int i = 0; i < entityResults.size(); i++) {
                ObjectNode entityResult = (ObjectNode) entityResults.get(i);
                List<String> nodePathsForRemoval = new ArrayList<>();
                for(String nodePathContainer: nonPublicNodePathContainers) {
                    if (entityResult.has(nodePathContainer)) {
                        nodePathsForRemoval.addAll(stringListReader.readValue(entityResult.get(nodePathContainer)));
                    }
                }
                JSONUtil.removeNodesByPath(entityResult, nodePathsForRemoval);
                entityResult.remove(nonPublicNodePathContainers);
            }
        }
    }

    /** Updates the input entity, external api's can use this method to update the entity
     * @param inputJson
     * @param userId
     * @return
     * @throws Exception
     */
    private String updateEntityNoStateChange(JsonNode inputJson, String userId) throws Exception {
        logger.debug("updateEntity starts");
        String entityType = inputJson.fields().next().getKey();
        String jsonString = objectMapper.writeValueAsString(inputJson);
        validationService.validateIgnoreRequired(entityType, jsonString);
        Shard shard = shardManager.getShard(inputJson.get(entityType).get(shardManager.getShardProperty()));
        String label = inputJson.get(entityType).get(dbConnectionInfoMgr.getUuidPropertyName()).asText();
        RecordIdentifier recordId = RecordIdentifier.parse(label);
        logger.info("Update Api: shard id: " + recordId.getShardLabel() + " for uuid: " + recordId.getUuid());
        registryService.updateEntity(shard, userId, recordId.getUuid(), jsonString);
        logger.debug("updateEntity ends");
        return "SUCCESS";
    }

    public String updateEntity(JsonNode inputJson, String userId) throws Exception {
        JsonNode existingNode = readEntity(inputJson, userId);
        return updateEntityAndState(existingNode, inputJson, userId);
    }

    private String updateEntityAndState(JsonNode existingNode, JsonNode updatedNode, String userId) throws Exception {
        entityStateHelper.changeStateAfterUpdate(existingNode, updatedNode);
        return updateEntityNoStateChange(updatedNode, userId);
    }

    public void addEntityProperty(String entityName, String entityId, String propertyName, JsonNode inputJson) throws Exception {
        String userId = "";
        JsonNode existingNode = readEntity("", entityName, entityId, false, null, false);
        JsonNode updateNode = existingNode.deepCopy();
        JsonNode propertyNode = updateNode.get(entityName).get(propertyName);
        if (propertyNode != null && !propertyNode.isMissingNode()) {
            if (propertyNode.isArray()){
                ((ArrayNode)propertyNode).add(inputJson);
            } else {
                ((ObjectNode)updateNode).set(propertyName, inputJson);
            }
        } else {
            // if array property
            JsonNode newPropertyNode = objectMapper.createArrayNode();
            ((ArrayNode)newPropertyNode).add(inputJson);
            ((ObjectNode)updateNode.get(entityName)).set(propertyName, newPropertyNode);
            try {
                validationService.validate(entityName, objectMapper.writeValueAsString(updateNode));
            } catch (MiddlewareHaltException me) {
                // try a field node since array validation failed
                newPropertyNode = objectMapper.createObjectNode();
                ((ObjectNode) updateNode).set(propertyName, newPropertyNode);
            }
        }
        updateEntityAndState(existingNode, updateNode, "");
    }

    public void updateEntityProperty(String entityName, String entityId, String propertyName, String propertyId, JsonNode inputJson) throws Exception {
        String userId = "";
        JsonNode existingNode = readEntity("", entityName, entityId, false, null, false);

        JsonNode updateNode = existingNode.deepCopy();
        ArrayNode propertyArrayNode = (ArrayNode) updateNode.get(entityName).get(propertyName);
        boolean found = false;
        for (JsonNode propertyNode : propertyArrayNode) {
            if (propertyNode.get(uuidPropertyName).asText().equals(propertyId)) {
                found = true;
                inputJson.fields().forEachRemaining(f -> {
                    ((ObjectNode)propertyNode).set(f.getKey(), f.getValue());
                });
                break;
            }
        }
        if (!found) throw new Exception("No property with given id");
        updateEntityAndState(existingNode, updateNode, "");
    }

    public void attestEntity(String entityName, JsonNode node, String[] jsonPaths, String userId) throws Exception {
        String patch = String.format("[{\"op\":\"add\", \"path\": \"attested\", \"value\": {\"attestation\":{\"id\":\"%s\"}, \"path\": \"%s\"}}]", userId, jsonPaths[0]);
        JsonPatch.applyInPlace(objectMapper.readTree(patch), node.get(entityName));
        updateEntityNoStateChange(node, userId);
    }

    public void sendForAttestation(String entityName, String entityId, String uuidPath) throws Exception {
        JsonNode entityNode = readEntity("", entityName, entityId, false, null, false);
        JsonNode updatedNode = entityStateHelper.sendForAttestation(entityNode, uuidPath);
        updateEntityNoStateChange(updatedNode, "");
    }

    public void attest(String entityName, String entityId, String uuidPath, boolean isGranted) throws Exception {
        JsonNode entityNode = readEntity("", entityName, entityId, false, null, false);
        JsonNode updatedNode = entityStateHelper.attestClaim(entityNode, uuidPath);
        updateEntityNoStateChange(updatedNode, "");
    }

    /**
	 * Get Audit log information , external api's can use this method to get the
	 * audit log of an antity
	 * 
	 * @param inputJson
	 * @return
	 * @throws Exception
	 */

    public JsonNode getAuditLog(JsonNode inputJson) throws Exception {
        logger.debug("get audit log starts");
        String entityType = inputJson.fields().next().getKey();
        JsonNode queryNode =inputJson.get(entityType);
        
        ArrayNode newEntityArrNode = objectMapper.createArrayNode();
        newEntityArrNode.add(entityType + auditSuffixSeparator + auditSuffix);
        ((ObjectNode)queryNode).set("entityType", newEntityArrNode);
        
        JsonNode resultNode = searchService.search(queryNode);
        
        ViewTemplate viewTemplate = viewTemplateManager.getViewTemplate(inputJson);
        if (viewTemplate != null) {
            ViewTransformer vTransformer = new ViewTransformer();
            resultNode = vTransformer.transform(viewTemplate, resultNode);
        }
        logger.debug("get audit log ends");
        
        return resultNode;

    }

    public String getKeycloakUserId(HttpServletRequest request) throws Exception {
        KeycloakAuthenticationToken principal = (KeycloakAuthenticationToken) request.getUserPrincipal();
        if (principal != null) {
            return principal.getAccount().getPrincipal().getName();
        }
        throw new Exception("Forbidden");
    }

    public JsonNode getRequestedUserDetails(HttpServletRequest request, String entityName) throws Exception {
        KeycloakAuthenticationToken principal = (KeycloakAuthenticationToken) request.getUserPrincipal();
        if (principal != null) {
            String userId = principal.getAccount().getPrincipal().getName();
            ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.set("entityType", JsonNodeFactory.instance.arrayNode().add(entityName));
            ObjectNode filters = JsonNodeFactory.instance.objectNode();
            filters.set(OSSystemFields.osOwner.toString(), JsonNodeFactory.instance.objectNode().put("eq", userId));
            payload.set("filters", filters);

            watch.start("RegistryController.searchEntity");
            JsonNode result = searchEntity(payload);
            watch.stop("RegistryController.searchEntity");
            return result;
        }
        throw new Exception("Forbidden");
    }

    public void authorize(String entityName, String entityId, HttpServletRequest request) throws Exception {
        String userId = getKeycloakUserId(request);
        JsonNode resultNode = readEntity(userId, entityName, entityId, false, null, false);
        if(!isOwner(resultNode.get(entityName), userId)) {
            throw new Exception("User is trying to update someone's data");
        }
    }
}
