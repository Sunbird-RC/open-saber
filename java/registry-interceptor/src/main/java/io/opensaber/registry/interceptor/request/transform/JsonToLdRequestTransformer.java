package io.opensaber.registry.interceptor.request.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.CharStreams;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ErrorCode;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;

public class JsonToLdRequestTransformer implements IRequestTransformer<Object> {

	private static JsonToLdRequestTransformer instance;
	private static Logger logger = LoggerFactory.getLogger(JsonToLdRequestTransformer.class);
	private String context;
	private final static String REQUEST = "request";

	static {
		try {
			instance = new JsonToLdRequestTransformer();
		} catch (IOException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	public static JsonToLdRequestTransformer getInstance() {
		return instance;
	}

	public JsonToLdRequestTransformer() throws IOException {
		loadDefaultMapping();
	}

	private void loadDefaultMapping() throws IOException {
		InputStreamReader in = new InputStreamReader(new ClassPathResource("frame.json").getInputStream());
		context = CharStreams.toString(in);
		logger.info("Context modified "+context);

	}

	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode input = (ObjectNode) mapper.readTree(data.getData().toString());
			ObjectNode fieldObjects = (ObjectNode) mapper.readTree(context);

			ObjectNode requestnode = (ObjectNode) input.path(REQUEST);
			String type = getTypeFromNode(requestnode);
			requestnode = (ObjectNode) requestnode.path(type);
			requestnode.setAll(fieldObjects);
			input.set(REQUEST, requestnode);
			logger.info("Object requestnode " + requestnode);

			String jsonldResult = mapper.writeValueAsString(input);
			return new Data<>(jsonldResult.replace("<@type>", type));
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
			throw new TransformationException(ex.getMessage(), ex, ErrorCode.JSON_TO_JSONLD_TRANFORMATION_ERROR);
		}
	}
	
	private ObjectNode addContextToRequestNode(ObjectNode requestnode) throws IOException{
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode fieldObjects = (ObjectNode) mapper.readTree(context);
		requestnode.setAll(fieldObjects);
		return requestnode;

	}

	private String getTypeFromNode(ObjectNode requestNode) throws JsonProcessingException {
		String rootValue = "";
		if (requestNode.isObject()) {
			logger.info("information " + requestNode.fields().next().getKey());
			rootValue = requestNode.fields().next().getKey();
		}
		return rootValue;
	}

}
