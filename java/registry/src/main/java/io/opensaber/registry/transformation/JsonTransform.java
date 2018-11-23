package io.opensaber.registry.transformation;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.middleware.transform.Data;
import io.opensaber.registry.middleware.transform.ErrorCode;
import io.opensaber.registry.middleware.transform.ITransformer;
import io.opensaber.registry.middleware.transform.TransformationException;
import io.opensaber.registry.middleware.util.CommunicationType;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.Constants.JsonldConstants;
import io.opensaber.registry.middleware.util.JSONUtil;

public class JsonTransform implements ITransformer<Object> {

	private static Logger logger = LoggerFactory.getLogger(JsonTransform.class);
	private final static String EXCEPTION_MESSAGE = "Communication type is invalid";
	private String prefix = "";
	private static final String SEPERATOR = ":";
	private final ObjectMapper mapper = new ObjectMapper();

	private List<String> keysToPurge = new ArrayList<>();

	private String context;
	private List<String> nodeTypes = new ArrayList<>();
	private String domain = "";

	public JsonTransform(String context, String domain) {
		this.context = context;
		this.domain = domain;
	}

	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException, IOException {
		return null;
	}

	@Override
	public Data<Object> transform(Data<Object> data, CommunicationType communicationType)
			throws TransformationException, IOException {

		ObjectNode input = (ObjectNode) mapper.readTree(data.getData().toString());
		Data<Object> transformedData = getTransformedData(input, communicationType);
		return transformedData;
	}

	private Data<Object> getTransformedData(ObjectNode input, CommunicationType communicationType)
			throws TransformationException, IOException {

		if (communicationType == CommunicationType.request) {
			ObjectNode fieldObjects = (ObjectNode) mapper.readTree(context);
			setNodeTypeToAppend(fieldObjects);
			ObjectNode resultNode = input;
			String rootType = getTypeFromNode(resultNode);
			logger.debug("Domain  value " + domain);
			if (domain.isEmpty())
				throw new TransformationException(Constants.INVALID_FRAME,
						ErrorCode.JSON_TO_JSONLD_TRANFORMATION_ERROR);
			setPrefix(domain);
			JSONUtil.addPrefix(resultNode, prefix, nodeTypes);
			logger.info("Appending prefix to requestNode " + resultNode);

			resultNode = (ObjectNode) resultNode.path(rootType);
			resultNode.setAll(fieldObjects);
			logger.info("Object requestnode " + resultNode);
			String jsonldResult = mapper.writeValueAsString(resultNode);
			return new Data<>(jsonldResult.replace("<@type>", domain));

		} else if (communicationType == CommunicationType.response) {
			JsonNode jsonNode = null;
			try {
				jsonNode = getconstructedJson(input, keysToPurge);
			} catch (IOException | ParseException e) {
				logger.error(e.getMessage(), e);
				throw new TransformationException(e.getMessage(), e, ErrorCode.JSONLD_TO_JSON_TRANFORMATION_ERROR);
			}
			return new Data<>(jsonNode);
		} else {
			throw new TransformationException(EXCEPTION_MESSAGE, ErrorCode.UNSUPPOTERTED_TYPE);
		}
	}

	/*
	 * Given a input like the following, {entity:{"a":1, "b":1}} returns
	 * "entity" being the type of the json object.
	 */
	private String getTypeFromNode(ObjectNode requestNode) throws JsonProcessingException {
		String rootValue = "";
		if (requestNode.isObject()) {
			logger.info("root node to set as type " + requestNode.fields().next().getKey());
			rootValue = requestNode.fields().next().getKey();
		}
		return rootValue;
	}

	private void setNodeTypeToAppend(ObjectNode fieldObjects) {
		ObjectNode context = (ObjectNode) fieldObjects.path(JsonldConstants.CONTEXT);
		nodeTypes.add(JsonldConstants.ID);
		context.fields().forEachRemaining(entry -> {
			if (entry.getValue().has(JsonldConstants.TYPE)
					&& entry.getValue().get(JsonldConstants.TYPE).asText().equalsIgnoreCase(JsonldConstants.ID)) {
				nodeTypes.add(entry.getKey());
			}
		});
		logger.info("nodeType size " + nodeTypes.size());
	}

	private JsonNode getconstructedJson(ObjectNode rootDataNode, List<String> keysToPurge)
			throws IOException, ParseException {

		setPurgeData(keysToPurge);
		ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
		for (JsonNode graphNode : rootDataNode.path(JsonldConstants.GRAPH)) {
			ObjectNode rootNode = addRootTypeNode(graphNode);
			if (keysToPurge.size() != 0)
				JSONUtil.removeNodes(rootNode, keysToPurge);// purgedKeys(rootNode);
			arrayNode.add(rootNode);
			JSONUtil.trimPrefix(rootNode, prefix);
		}
		return arrayNode;
	}

	private ObjectNode addRootTypeNode(JsonNode graphNode) {
		String rootNodeType = graphNode.path(JsonldConstants.TYPE).asText();
		setPrefix(rootNodeType.toLowerCase());
		ObjectNode rootNode = JsonNodeFactory.instance.objectNode();
		rootNode.set(rootNodeType, graphNode);
		return rootNode;

	}

	@Override
	public void setPurgeData(List<String> keyToPruge) {
		this.keysToPurge = keyToPruge;

	}

	private void setPrefix(String prefix) {
		this.prefix = prefix.toLowerCase() + SEPERATOR;
	}

	/*
	 * private void setPrefix(String type) { prefix = type.toLowerCase() +
	 * SEPERATOR; }
	 */

}
