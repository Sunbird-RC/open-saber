package io.opensaber.registry.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaDefination {
    private static Logger logger = LoggerFactory.getLogger(SchemaDefination.class);
    private final static String TITLE ="title";
    private final static String OSCONFIG ="_osconfig";
    private final static String PRIVATEFIELDS ="privateFields";
    private final static String SIGNEDFIELDS ="signedFields";

    private String jsonSchema;
    private String title;
    private List<String> privateProperties = new ArrayList<>();
    private List<String> signedProperties = new ArrayList<>();

    public SchemaDefination(JSONObject jsonSchema) throws JSONException {
        this.jsonSchema = jsonSchema.toString();
        try {
            title = jsonSchema.getString(TITLE);
            
            JSONObject configJson = jsonSchema.getJSONObject(OSCONFIG);
            JSONArray privateFields = configJson.getJSONArray(PRIVATEFIELDS);
            privateProperties = Arrays.asList(new String[privateFields.length()]);

            JSONArray signedFields = configJson.getJSONArray(SIGNEDFIELDS);
            signedProperties = Arrays.asList(new String[signedFields.length()]);
        } catch (JSONException e) {
            logger.error("while parsing schema defination  error: title or _osconfig key not found for " + jsonSchema);
        }
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return jsonSchema;
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
