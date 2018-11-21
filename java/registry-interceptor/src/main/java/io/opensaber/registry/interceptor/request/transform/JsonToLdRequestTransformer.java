package io.opensaber.registry.interceptor.request.transform;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.middleware.transform.Data;
import io.opensaber.registry.middleware.transform.ErrorCode;
import io.opensaber.registry.middleware.transform.ITransformer;
import io.opensaber.registry.middleware.transform.TransformationException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.Constants.JsonldConstants;
import io.opensaber.registry.middleware.util.JSONUtil;

public class JsonToLdRequestTransformer implements ITransformer<Object> {

	private final static String REQUEST = "request";
	private static final String SEPERATOR = ":";
	private static Logger logger = LoggerFactory.getLogger(JsonToLdRequestTransformer.class);
	private String context;
	private List<String> nodeTypes = new ArrayList<>();
	private String prefix = "";
	private String domain = "";

	public JsonToLdRequestTransformer(String context, String domain){
		this.context = context;
		this.domain = domain;
	}

	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode input = (ObjectNode) mapper.readTree(data.getData().toString());
			ObjectNode fieldObjects = (ObjectNode) mapper.readTree(context);
			setNodeTypeToAppend(fieldObjects);
			ObjectNode requestnode = input;

			String type = getTypeFromNode(requestnode);
			//setPrefix(type);
			if(domain.isEmpty())
				throw new TransformationException(Constants.INVALID_FRAME, ErrorCode.JSON_TO_JSONLD_TRANFORMATION_ERROR);
			setPrefix(domain);
			JSONUtil.addPrefix(requestnode, prefix, nodeTypes);
			logger.info("Appending prefix to requestNode " + requestnode);
			logger.info("Domain  value "+domain);

			requestnode = (ObjectNode) requestnode.path(type);
			requestnode.setAll(fieldObjects);
			input.set(REQUEST, requestnode);
			logger.info("Object requestnode " + requestnode);
			String jsonldResult = mapper.writeValueAsString(input);
			return new Data<>(jsonldResult.replace("<@type>", domain));
		} catch (Exception ex) {
			logger.error("Error trnsx : "+ex.getMessage(), ex);
			throw new TransformationException(ex.getMessage(), ex, ErrorCode.JSON_TO_JSONLD_TRANFORMATION_ERROR);
		}
	}

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

	private void setPrefix(String type) {
		prefix = type.toLowerCase() + SEPERATOR;
	}

	@Override
	public void setPurgeData(List<String> keyToPruge) {

	}

}
