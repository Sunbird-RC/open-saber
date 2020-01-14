package io.opensaber.registry.util;

import java.util.Iterator;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.exception.audit.AuditException;
import io.opensaber.registry.exception.audit.EmptyArrayException;
import io.opensaber.registry.exception.audit.EntityTypeMissingException;
import io.opensaber.registry.exception.audit.InvalidArguementException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.pojos.FilterOperators;

@Component
public class AuditHelper {

	@Autowired
	private ObjectMapper mapper;

	private static Logger logger = LoggerFactory.getLogger(AuditHelper.class);

	public JsonNode getSearchQueryNodeForAudit(JsonNode inputJson, String uuidPropertyName)
			throws AuditException {


		JsonNode entityTypeNodeArr = inputJson.get("entityType");
		if (entityTypeNodeArr == null) {
			logger.error("entityType is null , entityType :{}", entityTypeNodeArr);
			throw new EntityTypeMissingException("entityType cannot be null");
		}
		if (!entityTypeNodeArr.isArray()) {
			logger.error("entityType is not an array , entityType :{}", entityTypeNodeArr);
			throw new InvalidArguementException("entityType should be an array");
		}

		ArrayNode newEntityArrNode = mapper.createArrayNode();
		for (JsonNode entity : entityTypeNodeArr) {
			if (entity.asText().length() > 1) {
				newEntityArrNode.add(entity.asText() + "_Audit");
			}
		}
		if (newEntityArrNode.size() < 1) {
			logger.error("entityType is empty , entityType :{}", entityTypeNodeArr);
			throw new EmptyArrayException("entityType should not be an empty array");
		}
		ObjectNode searchNode = getFilterNode(inputJson, uuidPropertyName, newEntityArrNode);

		return searchNode;

	}

	private ObjectNode getFilterNode(JsonNode inputJson, String uuidPropertyName, ArrayNode newEntityArrNode) {
		ObjectNode searchNode = mapper.createObjectNode();

		searchNode.set("entityType", newEntityArrNode);
		ObjectNode filterNode = mapper.createObjectNode();
		Iterator<Entry<String, JsonNode>> itrJson = inputJson.fields();
		while (itrJson.hasNext()) {
			String filterOp = itrJson.next().getKey();
			switch (filterOp) {
			case Constants.ACTION:
				ObjectNode actionNode = mapper.createObjectNode();
				String action = inputJson.get(filterOp).asText();
				actionNode.put(FilterOperators.eq.name(), action);
				filterNode.set(filterOp, actionNode);
				break;

			case Constants.ID:
				ObjectNode idNode = mapper.createObjectNode();
				String id = inputJson.get(filterOp).asText();
				idNode.put(FilterOperators.eq.name(), id);
				filterNode.set("recordId", idNode);
				break;

			case Constants.START_DATE:
				ObjectNode startDateNode = mapper.createObjectNode();
				JsonNode startDate = inputJson.get(filterOp);
				startDateNode.set(FilterOperators.gte.name(), startDate);
				filterNode.set("date", startDateNode);
				break;

			case Constants.END_DATE:
				ObjectNode endDateNode = mapper.createObjectNode();
				JsonNode endDate = inputJson.get(filterOp);
				endDateNode.set(FilterOperators.lte.name(), endDate);
				filterNode.set("date", endDateNode);

				break;

			case Constants.LIMIT:
				searchNode.set(filterOp, inputJson.get(filterOp));
				break;

			case Constants.OFFSET:
				searchNode.set(filterOp, inputJson.get(filterOp));
				break;

			}

		}

		if (null != inputJson.get(uuidPropertyName) && !inputJson.get(uuidPropertyName).asText().isEmpty()) {
			ObjectNode uuidNode = mapper.createObjectNode();
			String uuid = inputJson.get(uuidPropertyName).asText();
			uuidNode.put(FilterOperators.eq.name(), uuid);
			filterNode.set(uuidPropertyName, uuidNode);
		}

		searchNode.set("filters", filterNode);
		return searchNode;
	}
}
