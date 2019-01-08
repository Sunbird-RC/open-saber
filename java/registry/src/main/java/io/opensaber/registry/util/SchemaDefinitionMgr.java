package io.opensaber.registry.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaDefinitionMgr {
    private static Logger logger = LoggerFactory.getLogger(SchemaDefinitionMgr.class);
    private Map<String, SchemaDefination> schemaDefinationMap = new HashMap<>();

    public SchemaDefinitionMgr(List<String> jsonSchemas) {
        for (int i = 0; i < jsonSchemas.size(); i++) {
            try {
                addSchemaDefinition(jsonSchemas.get(i));
            } catch (JSONException e) {
                logger.error("Loading defination for " + jsonSchemas);
            }
        }

    }

    private void addSchemaDefinition(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        SchemaDefination schemaDefination = new SchemaDefination(jsonObject);
        schemaDefinationMap.putIfAbsent(schemaDefination.getTitle(), schemaDefination);
    }

    public List<SchemaDefination> getAllSchemaDefinations() {
        List<SchemaDefination> schemaDefinations = new ArrayList<>();
        for (Entry<String, SchemaDefination> entry : schemaDefinationMap.entrySet()) {
            schemaDefinations.add(entry.getValue());
        }
        return schemaDefinations;
    }

    public SchemaDefination getSchemaDefination(String title) {
        return schemaDefinationMap.getOrDefault(title, null);
    }
}
