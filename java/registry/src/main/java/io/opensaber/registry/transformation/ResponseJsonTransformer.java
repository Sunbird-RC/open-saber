package io.opensaber.registry.transformation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.middleware.transform.commons.Constants.JsonldConstants;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ErrorCode;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.transform.commons.TransformationException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.text.ParseException;
import java.util.*;

@Component
public class ResponseJsonTransformer implements IResponseTransformer<String> {

    private static ResponseJsonTransformer instance;
    private static Logger logger = LoggerFactory.getLogger(ResponseJsonTransformer.class);
    ObjectNode result = JsonNodeFactory.instance.objectNode();

    static {
        try {
            instance = new ResponseJsonTransformer();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }


    public static ResponseJsonTransformer getInstance() {
        return instance;
    }
    
    @Override
    public ResponseData<String> transform(Data<String> data) throws TransformationException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode input = mapper.readTree(data.getData());
            //TODO: remove prefix + add Teacher root node.
            constructJson(input);            
            String jsonldResult = mapper.writeValueAsString(result);
            return new ResponseData<>(jsonldResult);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            throw new TransformationException(ex.getMessage(), ex, ErrorCode.JSONLD_TO_JSON_TRANFORMATION_ERROR);
        }
    }
    
    private void constructJson(JsonNode rootDataNode) throws IOException, ParseException {

    	JsonNode framedJsonldNode = rootDataNode.path(JsonldConstants.GRAPH);
        for (JsonNode dataNode : framedJsonldNode) {
            processObjectNode(dataNode);
            logger.info("result objectnode "+result);
        }
    }

    private void processObjectNode(JsonNode node){

        	Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
            	Map.Entry<String, JsonNode> entry = fields.next();
    			logger.info("node as object key, "+entry.getKey());

            	if(entry.getValue().isObject()){
            			ObjectNode jsonNode = getJsonNodes(entry.getValue());
            			result.set(entry.getKey(), jsonNode);
            		}
            		//else throw error.
    	}
    }
    
	private ObjectNode getJsonNodes(JsonNode node) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		if (node.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fieldsO = node.fields();

			while (fieldsO.hasNext()) {
				Map.Entry<String, JsonNode> entryO = fieldsO.next();
				logger.info("node as key.............., " + entryO.getKey());
				
				if (entryO.getValue().isValueNode()) {
					logger.info("node as value key, " + entryO.getKey());

					if (!(entryO.getKey().toString().equalsIgnoreCase("@id")
							|| entryO.getKey().toString().equalsIgnoreCase("@type"))) {
						result.set(entryO.getKey().toString(), entryO.getValue());
					}
				} else if (entryO.getValue().isArray()) {
					logger.info("node as Array key, " + entryO.getKey());
					ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
					for (int i = 0; i < entryO.getValue().size(); i++) {
						if (entryO.getValue().get(i).isObject()) {
							ObjectNode jsonNode = getJsonNodes(entryO.getValue().get(i));
							arrayNode.add(jsonNode);
						}else{
							ObjectNode jsonNode = getJsonNodes(entryO.getValue());
							arrayNode.add(jsonNode);
						}
					}
					result.set(entryO.getKey(), arrayNode);
				} else if (!entryO.getValue().isValueNode()) {
					logger.info("node as !value key, " + entryO.getKey());

					ObjectNode jsonNode = getJsonNodes(entryO.getValue());
					result.set(entryO.getKey(), jsonNode);
				}
			}
		}
		return result;
	}
	
}
