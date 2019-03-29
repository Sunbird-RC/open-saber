package io.opensaber.registry.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensaber.pojos.APIMessage;
import io.opensaber.views.ViewTemplate;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ViewTemplateManager {

    public static final String viewLocation = "classpath*:views/*.json";
    private static final String viewTemplateId = "viewTemplateId";
    private static final String viewTemplate = "viewTemplate";

    @Autowired
    private OSResourceLoader osResourceLoader;
    private Map<String, String> jsonNodes = new HashMap<>();    
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Loads the templates from the views folder
     */
    @PostConstruct
    public void  loadTemplates() throws Exception{
        osResourceLoader.loadResource(viewLocation);
        this.jsonNodes = osResourceLoader.getJsonNodes();

    }
    
    /**
     * Returns the view template based on the request parameter viewTemplateId, viewTemplate 
     * 
     * @param apiMessage
     * @return
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    public ViewTemplate getViewTemplate(APIMessage apiMessage) throws JsonParseException, JsonMappingException, IOException{

        ViewTemplate viewTemp = null;
        JsonNode requestNode = apiMessage.getRequest().getRequestMapNode();

        if(requestNode.has(viewTemplateId)){
            viewTemp = getViewTemplateByName(requestNode.get(viewTemplateId).asText());
        }else if(requestNode.has(viewTemplate)){
            viewTemp = getViewTemplateByContent(requestNode.get(viewTemplate).asText());
        }
        return viewTemp;
    }
    
    private ViewTemplate getViewTemplateByName(String templateName) throws JsonParseException, JsonMappingException, IOException{
        String templateNodeStr = jsonNodes.get(templateName);
        return mapper.readValue(templateNodeStr, ViewTemplate.class);
    }
    
    private ViewTemplate getViewTemplateByContent(String templateContent) throws JsonParseException, JsonMappingException, IOException{
        return mapper.readValue(templateContent, ViewTemplate.class);
    }
    
}
