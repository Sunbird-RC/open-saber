package io.opensaber.registry.transformation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.middleware.transform.commons.Constants.JsonldConstants;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ErrorCode;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;

@Component
public class JsonToLdTransformer implements IResponseTransformer<Object> {

	private static Logger logger = LoggerFactory.getLogger(JsonToLdTransformer.class);
	private List<String> keysToTrim = new ArrayList<>();

	public Data<Object> transform(Data<Object> data, List<String> keysToTrim) throws TransformationException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode input = (ObjectNode) mapper.readTree(data.getData().toString());
			JsonNode jsonNode = getconstructedJson(input, keysToTrim);
			return new Data<>(jsonNode);
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
			throw new TransformationException(ex.getMessage(), ex, ErrorCode.JSONLD_TO_JSON_TRANFORMATION_ERROR);
		}
	}

	private JsonNode getconstructedJson(JsonNode rootDataNode, List<String> keysToTrim)
			throws IOException, ParseException {

		JsonNode graphNode = rootDataNode.path(JsonldConstants.GRAPH).get(0);
		setKeysToTrim(keysToTrim);
		if (keysToTrim.size() != 0)
			return trimedKeyOfNodes(graphNode);
		return graphNode;
	}

	private ObjectNode trimedKeyOfNodes(JsonNode node) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		Iterator<Map.Entry<String, JsonNode>> fieldsO = node.fields();
		while (fieldsO.hasNext()) {
			Map.Entry<String, JsonNode> entryO = fieldsO.next();

			if (entryO.getValue().isValueNode()) {
				if (!keysToTrim.contains(entryO.getKey()))
					result.set(entryO.getKey().toString(), entryO.getValue());

			} else if (entryO.getValue().isArray()) {
				ArrayNode arrayNode = getArrayNode(entryO);
				result.set(entryO.getKey(), arrayNode);

			} else if (!entryO.getValue().isValueNode()) {
				ObjectNode jsonNode = trimedKeyOfNodes(entryO.getValue());
				result.set(entryO.getKey(), jsonNode);
			}
		}

		return result;
	}

	private ArrayNode getArrayNode(Map.Entry<String, JsonNode> entry) {
		ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
		for (int i = 0; i < entry.getValue().size(); i++) {
			if (entry.getValue().get(i).isObject()) {
				ObjectNode jsonNode = trimedKeyOfNodes(entry.getValue().get(i));
				arrayNode.add(jsonNode);
			} else if (entry.getValue().get(i).isValueNode()) {
				arrayNode.add(entry.getValue().get(i));
			}
		}

		return arrayNode;
	}

	private void setKeysToTrim(List<String> keysToTrim) {
		this.keysToTrim = keysToTrim;
	}

}
