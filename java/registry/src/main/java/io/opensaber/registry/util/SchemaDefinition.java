package io.opensaber.registry.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaDefinition {
    private static Logger logger = LoggerFactory.getLogger(SchemaDefinition.class);
    private final static String TITLE = "title";
    private final static String OSCONFIG = "_osconfig";

    private String schema;
    private String title;
    private List<String> privateProperties = new ArrayList<>();
    private List<String> signedProperties = new ArrayList<>();

    public SchemaDefinition(JSONObject schema) throws JsonParseException, JsonMappingException, IOException {
        this.schema = schema.toString();
        try {
            title = schema.getString(TITLE);
            ObjectMapper mapper = new ObjectMapper();

            JSONObject configJson = schema.getJSONObject(OSCONFIG);
            OSConfigProperties configProperties = mapper.readValue(configJson.toString(), OSConfigProperties.class);

            privateProperties = configProperties.getPrivateFields();
            signedProperties = configProperties.getSignedFields();
        } catch (JSONException e) {
            logger.error("while parsing schema defination  error: title or _osconfig key not found for " + schema);
        }
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return schema;
    }

    public List<String> getSignedFields() {
        return signedProperties;
    }

    public List<String> getPrivateFields() {
        return privateProperties;
    }

    public boolean isEncrypted(String fieldName) {
        if (fieldName != null) {
            return fieldName.substring(0, Math.min(fieldName.length(), 9)).equalsIgnoreCase("encrypted");
        } else
            return false;
    }

    public boolean isPrivate(String fieldName) {
        return privateProperties.contains(fieldName);
    }

}
