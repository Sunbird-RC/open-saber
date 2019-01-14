package io.opensaber.registry.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Creates Definition for a given JsonNode 
 * This accepts a schema
 *
 */
public class Definition {
    private static Logger logger = LoggerFactory.getLogger(Definition.class);
    private final static String TITLE = "title";
    private final static String OSCONFIG = "_osconfig";

    private String content;
    private String title;
    private List<String> privateFields = new ArrayList<>();
    private List<String> signedFields = new ArrayList<>();

    /**
     * To parse a jsonNode of given schema type
     * @param schema
     */
    public Definition(JsonNode schema) {
        content = schema.toString();
        try {
            title = schema.get(TITLE).asText();
            ObjectMapper mapper = new ObjectMapper();

            JsonNode configJson = schema.get(OSCONFIG);
            OSSchemaConfiguration configProperties = mapper.treeToValue(configJson, OSSchemaConfiguration.class);

            privateFields = configProperties.getPrivateFields();
            signedFields = configProperties.getSignedFields();
        } catch (Exception e) {
            logger.error("while parsing schema defination  error: title or _osconfig key not found for " + schema);
        }
    }

    /**
     * Holds the title for a given schema
     * @return
     */
    public String getTitle() {
        return title;
    }

    /**
     * Holds the String representation of schema 
     * @return
     */
    public String getContent() {
        return content;
    }

    /**
     * Holds the field names to be used for signature
     * @return
     */
    public List<String> getSignedFields() {
        return signedFields;
    }

    /**
     * Holds field name to be encrypted
     * @return
     */
    public List<String> getPrivateFields() {
        return privateFields;
    }

    public boolean isEncrypted(String fieldName) {
        if (fieldName != null) {
            return fieldName.substring(0, Math.min(fieldName.length(), 9)).equalsIgnoreCase("encrypted");
        } else
            return false;
    }

    public boolean isPrivate(String fieldName) {
        return privateFields.contains(fieldName);
    }

}
