package io.opensaber.registry.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.PostConstruct;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component("definitionsManager")
public class DefinitionsManager {
    private static Logger logger = LoggerFactory.getLogger(DefinitionsManager.class);

    @Autowired
    private DefinitionsReader definitionsReader;
    private Map<String, SchemaDefination> schemaDefinationMap = new HashMap<>();

    /**
     * Loads the definitions from the _schemas folder
     */
    @PostConstruct
    public void loadSchemaDefination(){
        try {
            Resource[] resources = definitionsReader.getResources("classpath:public/_schemas/*.json");
            for (Resource resource : resources) {
                String jsonContent = getContent(resource);
                JSONObject jsonObject = new JSONObject(jsonContent);
                SchemaDefination schemaDefination = new SchemaDefination(jsonObject);
                schemaDefinationMap.putIfAbsent(schemaDefination.getTitle(), schemaDefination);
            }

        } catch (JSONException |IOException ioe) {
            logger.error("Cannot load json resources. Validation can't work");
        }
    }
    /**
     * Returns the title for all schema loaded
     * @return
     */
    public Set<String> getAllKnownDefinitions() {       
        return schemaDefinationMap.keySet();
    }
    /**
     * Returns all schema definitions that are loaded
     * @return
     */
    public List<SchemaDefination> getAllSchemaDefinations() {
        List<SchemaDefination> schemaDefinations = new ArrayList<>();
        for (Entry<String, SchemaDefination> entry : schemaDefinationMap.entrySet()) {
            schemaDefinations.add(entry.getValue());
        }
        return schemaDefinations;
    }
    /**
     * Provide a schemaDefinition by given title which is already loaded 
     * @param title
     * @return
     */
    public SchemaDefination getSchemaDefination(String title) {
        return schemaDefinationMap.getOrDefault(title, null);
    }

    /**
     * Returns a content of resource 
     * @param resource
     * @return
     */
    private String getContent(Resource resource) {
        InputStream in;
        try {
            in = resource.getInputStream();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());

        } catch (IOException e) {
            logger.error("Cannot load resource " + resource.getFilename());

        }
        return null;
    }
}
