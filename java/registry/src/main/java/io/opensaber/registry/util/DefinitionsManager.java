package io.opensaber.registry.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // Loads the definitions from the _schemas folder
    public Set<String> getAllKnownDefinitions() {
        Set<String> keys = new HashSet<>();
        try {
            SchemaDefinationMgr schemaDefinationMgr = new SchemaDefinationMgr(getAllJsonSchemas());
            for (SchemaDefination schemaDefination : schemaDefinationMgr.getAllSchemaDefinations()) {
                keys.add(schemaDefination.getTitle());
            }
        } catch (IOException ioe) {
            logger.error("Cannot load json resources. Validation can't work");
        }

        return keys;
    }

    private List<String> getAllJsonSchemas() throws IOException {
        List<String> jsonSchemas = new ArrayList<>();

        Resource[] resources = definitionsReader.getResources("classpath:public/_schemas/*.json");
        for (Resource resource : resources) {
            String jsonContent = getContent(resource);
            jsonSchemas.add(jsonContent);
        }

        return jsonSchemas;
    }

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
            logger.error("Cannot load resource " + resource);

        }
        return null;
    }
}
