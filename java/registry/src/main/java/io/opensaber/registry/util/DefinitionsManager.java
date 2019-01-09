package io.opensaber.registry.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private Map<String, SchemaDefinition> schemaDefinationMap = new HashMap<>();

    /**
     * Loads the definitions from the _schemas folder
     */
    @PostConstruct
    public void loadSchemaDefination() {
        try {
            Resource[] resources = definitionsReader.getResources("classpath:public/_schemas/*.json");
            for (Resource resource : resources) {
                String jsonContent = getContent(resource);
                JSONObject jsonObject = new JSONObject(jsonContent);
                SchemaDefinition schemaDefination = new SchemaDefinition(jsonObject);
                schemaDefinationMap.putIfAbsent(schemaDefination.getTitle(), schemaDefination);
            }

        } catch (JSONException | IOException ioe) {
            logger.error("Cannot load json resources. Validation can't work");
        }
    }

    /**
     * Returns the title for all schema loaded
     * 
     * @return
     */
    public Set<String> getAllKnownDefinitions() {
        return schemaDefinationMap.keySet();
    }

    /**
     * Returns all schema definitions that are loaded
     * 
     * @return
     */
    public List<SchemaDefinition> getAllSchemaDefinations() {
        List<SchemaDefinition> schemaDefinations = new ArrayList<>();
        for (Entry<String, SchemaDefinition> entry : schemaDefinationMap.entrySet()) {
            schemaDefinations.add(entry.getValue());
        }
        return schemaDefinations;
    }

    /**
     * Provide a schemaDefinition by given title which is already loaded
     * 
     * @param title
     * @return
     */
    public SchemaDefinition getSchemaDefination(String title) {
        return schemaDefinationMap.getOrDefault(title, null);
    }

    /**
     * Returns a content of resource
     * 
     * @param resource
     * @return
     */
    private String getContent(Resource resource) {
        String content = null;
        try {
            File file = resource.getFile();
            content = new String(Files.readAllBytes(file.toPath()));
            logger.info(resource.getFilename() + "Content: " + content);
        } catch (IOException e) {
            logger.error("Cannot load resource " + resource.getFilename());

        }
        return content;
    }
}
