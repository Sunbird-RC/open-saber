package io.opensaber.registry.interceptor.request.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.CharStreams;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ErrorCode;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;


import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;

public class RequestJsonTransformer implements IRequestTransformer<String> {

    private static RequestJsonTransformer instance;
    private static Logger logger = LoggerFactory.getLogger(RequestJsonTransformer.class);

    static {
        try {
            instance = new RequestJsonTransformer();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private String mappingJson;
/*    private JsonNode fieldMapping;
    

    public JsonNode getFieldMapping() {
        return fieldMapping;
    }*/

    public static RequestJsonTransformer getInstance() {
        return instance;
    }

    public RequestJsonTransformer() throws IOException {
         loadDefaultMapping();
    }

    private void loadDefaultMapping() throws IOException {
        InputStreamReader in = new InputStreamReader
        		(new ClassPathResource("frame.json").getInputStream());
        mappingJson = CharStreams.toString(in);
      
/*        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(mappingJson);*/
    }

    @Override
    public ResponseData<String> transform(Data<String> data) throws TransformationException {
        try {
            ObjectMapper mapper = new ObjectMapper();           
            ObjectNode input =(ObjectNode) mapper.readTree(data.getData().toString());          
            ObjectNode fieldObjects = (ObjectNode) mapper.readTree(mappingJson);
            
           // ObjectNode node = (ObjectNode) new ObjectMapper().readTree(input.path("request").toString());

            ObjectNode requestnode = (ObjectNode) input.path("request");
            logger.info("Object requestnode "+requestnode);
            logger.info("Object inputnode "+input);
            String type = getTypeFromNode(requestnode);
            requestnode.setAll(fieldObjects);            

            String jsonldResult = mapper.writeValueAsString(input);
            //read the root node: as type[entity]            
            return new ResponseData<>(jsonldResult.replace("<@type>", type));
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            throw new TransformationException(ex.getMessage(), ex, ErrorCode.JSON_TO_JSONLD_TRANFORMATION_ERROR);
        }
    }
    private String getTypeFromNode(ObjectNode requestNode) throws JsonProcessingException{ 
    	String rootValue ="";   	
    	if (requestNode.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fieldsO = requestNode.fields();

			while (fieldsO.hasNext()) {
				Map.Entry<String, JsonNode> entryO = fieldsO.next();
				if (!entryO.getValue().isValueNode()) {
					rootValue = entryO.getKey();
				}
			}
    	}
    	//requestNode.path("")set("<@type>", rootValue);
        //String jsonldResult =new ObjectMapper().writeValueAsString(requestNode).replace("<@type>", type);
    	return rootValue;
    }
    
    private String getJsonldWithType(ObjectNode requestNode) throws JsonProcessingException{ 
    	String type ="";   	
    	if (requestNode.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fieldsO = requestNode.fields();

			while (fieldsO.hasNext()) {
				Map.Entry<String, JsonNode> entryO = fieldsO.next();
				if (!entryO.getValue().isValueNode()) {
					type = entryO.getKey();
				}
			}
    	}
        String jsonldResult =new ObjectMapper().writeValueAsString(requestNode).replace("<@type>", type);
    	return jsonldResult;
    }

}
