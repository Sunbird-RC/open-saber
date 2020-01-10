package io.opensaber.registry.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.exception.CustomException;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.DecryptionHelper;
import io.opensaber.registry.service.IReadService;
import io.opensaber.registry.service.ISearchService;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.sink.shard.ShardManager;
import io.opensaber.registry.util.DefinitionsManager;
import io.opensaber.registry.util.ReadConfigurator;
import io.opensaber.registry.util.ReadConfiguratorFactory;
import io.opensaber.registry.util.RecordIdentifier;
import io.opensaber.registry.util.ViewTemplateManager;
import io.opensaber.views.ViewTemplate;
import io.opensaber.views.ViewTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;

/**
 * This is helper class, user-service calls this class in-order to access
 * registry functionality
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
	private ISearchService searchService;

	@Autowired
	private ViewTemplateManager viewTemplateManager;

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

	/**
	 * calls validation and then persists the record to registry.
	 * 
	 * @param inputJson
	 * @return
	 * @throws Exception
	 */
	public String addEntity(JsonNode inputJson, String userId) throws Exception {
		RecordIdentifier recordId = null;
		String entityType = inputJson.fields().next().getKey();
		try {
			logger.info("Add api: entity type: {} and shard propery: {}", entityType, shardManager.getShardProperty());
			Shard shard = shardManager.getShard(inputJson.get(entityType).get(shardManager.getShardProperty()));
			watch.start("RegistryController.addToExistingEntity");
			String resultId = registryService.addEntity(shard, userId, inputJson);
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
	 * 
	 * @param inputJson
	 * @param requireLDResponse
	 * @return
	 * @throws Exception
	 */
	public JsonNode readEntity(JsonNode inputJson, String userId, boolean requireLDResponse) throws Exception {
		logger.debug("readEntity starts");
		boolean includeSignatures = false;
		boolean includePrivateFields = false;
		JsonNode resultNode = null;
		String entityType = inputJson.fields().next().getKey();
		String label = inputJson.get(entityType).get(dbConnectionInfoMgr.getUuidPropertyName()).asText();
		RecordIdentifier recordId = RecordIdentifier.parse(label);
		String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
		Shard shard = shardManager.activateShard(shardId);
		logger.info("Read Api: shard id: " + recordId.getShardLabel() + " for label: " + label);
		JsonNode signatureNode = inputJson.get(entityType).get("includeSignatures");
		if (null != signatureNode) {
			includeSignatures = true;
		}
		ReadConfigurator configurator = ReadConfiguratorFactory.getOne(includeSignatures);
		configurator.setIncludeTypeAttributes(requireLDResponse);
		ViewTemplate viewTemplate = viewTemplateManager.getViewTemplate(inputJson);
		if (viewTemplate != null) {
			includePrivateFields = viewTemplateManager.isPrivateFieldEnabled(viewTemplate, entityType);
		}
		configurator.setIncludeEncryptedProp(includePrivateFields);
		resultNode = readService.getEntity(shard, userId, recordId.getUuid(), entityType, configurator);
		if (viewTemplate != null) {
			ViewTransformer vTransformer = new ViewTransformer();
			resultNode = includePrivateFields ? decryptionHelper.getDecryptedJson(resultNode) : resultNode;
			resultNode = vTransformer.transform(viewTemplate, resultNode);
		}
		logger.debug("readEntity ends");
		return resultNode;

	}

	/**
	 * Get entity details from the DB and modifies data according to view template,
	 * requests which need only json format can call this method
	 * 
	 * @param inputJson
	 * @return
	 * @throws Exception
	 */
	public JsonNode readEntity(JsonNode inputJson, String userId) throws Exception {
		return readEntity(inputJson, userId, false);
	}

	/**
	 * Search the input in the configured backend, external api's can use this
	 * method for searching
	 * 
	 * @param inputJson
	 * @return
	 * @throws Exception
	 */
	public JsonNode searchEntity(JsonNode inputJson) throws Exception {
		logger.debug("searchEntity starts");
		JsonNode resultNode = searchService.search(inputJson);
		ViewTemplate viewTemplate = viewTemplateManager.getViewTemplate(inputJson);
		if (viewTemplate != null) {
			ViewTransformer vTransformer = new ViewTransformer();
			resultNode = vTransformer.transform(viewTemplate, resultNode);
		}
		// Search is tricky to support LD. Needs a revisit here.
		logger.debug("searchEntity ends");
		return resultNode;
	}

	/**
	 * Updates the input entity, external api's can use this method to update the
	 * entity
	 * 
	 * @param inputJson
	 * @param userId
	 * @return
	 * @throws Exception
	 */
	public String updateEntity(JsonNode inputJson, String userId) throws Exception {
		logger.debug("updateEntity starts");
		String entityType = inputJson.fields().next().getKey();
		String jsonString = objectMapper.writeValueAsString(inputJson);
		Shard shard = shardManager.getShard(inputJson.get(entityType).get(shardManager.getShardProperty()));
		String label = inputJson.get(entityType).get(dbConnectionInfoMgr.getUuidPropertyName()).asText();
		RecordIdentifier recordId = RecordIdentifier.parse(label);
		logger.info("Update Api: shard id: " + recordId.getShardLabel() + " for uuid: " + recordId.getUuid());
		registryService.updateEntity(shard, userId, recordId.getUuid(), jsonString);
		logger.debug("updateEntity ends");
		return "SUCCESS";
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
		JsonNode auditNode = getSearchQueryNodeForAudit(inputJson);
		JsonNode resultNode = searchService.search(auditNode);
		return resultNode;

	}

	private JsonNode getSearchQueryNodeForAudit(JsonNode inputJson) throws Exception {

		ObjectMapper mapper = new ObjectMapper();

		JsonNode entityTypeNodeArr = inputJson.get("entityType");
		if(null == entityTypeNodeArr) {
			throw new NullPointerException("entityType cannot be null");
		}
		if (!entityTypeNodeArr.isArray()) {
			throw new CustomException("entityType should be an array");
		}
		if (entityTypeNodeArr.size() <= 0) {
			throw new CustomException("entityType should not be an empty array");
		}
		ArrayNode newEntityArrNode = mapper.createArrayNode();
		for (JsonNode entity : entityTypeNodeArr) {
			newEntityArrNode.add(entity.asText() + "_Audit");
		}
		ObjectNode searchNode = mapper.createObjectNode();

		searchNode.set("entityType", newEntityArrNode);
		ObjectNode filterNode = mapper.createObjectNode();

		if (null != inputJson.get("action") && !inputJson.get("action").asText().isEmpty()) {
			ObjectNode actionNode = mapper.createObjectNode();
			String action = inputJson.get("action").asText();
			actionNode.put("eq", action);
			filterNode.set("action", actionNode);
		}

		if (null != inputJson.get("id") && !inputJson.get("id").asText().isEmpty()) {
			ObjectNode idNode = mapper.createObjectNode();
			String id = inputJson.get("id").asText();
			idNode.put("eq", id);
			filterNode.set("recordId", idNode);
		}

		if (null != inputJson.get("startDate") && !inputJson.get("startDate").asText().isEmpty()) {
			ObjectNode startDateNode = mapper.createObjectNode();
			JsonNode startDate = inputJson.get("startDate");
			startDateNode.set("gte",startDate);
			filterNode.set("date", startDateNode);
		}
		if (null != inputJson.get("endDate") && !inputJson.get("endDate").asText().isEmpty()) {
			ObjectNode endDateNode = mapper.createObjectNode();
			JsonNode endDate = inputJson.get("endDate");
			endDateNode.set("lte", endDate);
			filterNode.set("date", endDateNode);
		}
		if (null != inputJson.get("limit") && null != inputJson.get("offset")) {
			searchNode.set("limit", inputJson.get("limit"));
			searchNode.set("offset", inputJson.get("offset"));

		}

		if (null != inputJson.get(uuidPropertyName) && !inputJson.get(uuidPropertyName).asText().isEmpty()) {
			ObjectNode uuidNode = mapper.createObjectNode();
			String uuid = inputJson.get(uuidPropertyName).asText();
			uuidNode.put("eq", uuid);
			filterNode.set(uuidPropertyName, uuidNode);
		}

		searchNode.set("filters", filterNode);

		return searchNode;

	}

}
