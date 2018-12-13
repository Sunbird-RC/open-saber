package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.schema.configurator.ISchemaConfigurator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EncryptionHelper {
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    private ISchemaConfigurator schemaConfigurator;

    public JsonNode createEncryptedJson(JsonNode rootNode) throws EncryptionException {
        Map<String, Object> encMap = new HashMap<String, Object>();
        List<String> privatePropertyLst = schemaConfigurator.getAllPrivateProperties();
        if (rootNode.isObject()) {
            populateObject(rootNode, privatePropertyLst, encMap);
        }
        Map<String, Object> encodedMap = (Map<String, Object>) encryptionService.encrypt(encMap);
        if (rootNode.isObject()) {
            populateJsonObject(rootNode, privatePropertyLst, encodedMap);
        }
        return rootNode;
    }

    private void populateObject(JsonNode rootNode, List<String> privatePropertyLst, Map<String, Object> encMap) {
         rootNode.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            if (entryValue.isValueNode()) {
                if (privatePropertyLst.contains(entry.getKey()))
                    encMap.put(entry.getKey(), entryValue.asText());
            } else if (entryValue.isObject()) {
                populateObject(entryValue, privatePropertyLst, encMap);
            }
        });
    }

    /**
     * Given the root node, based on the privatePropertyList, fetch the encrypted value
     * and replace the root node value (original unencrypted).
     *
     * @param rootNode
     * @param privatePropertyLst
     * @param encodedMap
     */
    private void populateJsonObject(JsonNode rootNode, List<String> privatePropertyLst, Map<String, Object> encodedMap) {
        rootNode.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();

            if (entryValue.isValueNode() && privatePropertyLst.contains(entry.getKey())) {
                // We encrypt only string nodes.
                String encryptedVal = encodedMap.get(entry.getKey()).toString();
                JsonNode encryptedValNode = JsonNodeFactory.instance.textNode(encryptedVal);
                entry.setValue(encryptedValNode);
            } else if (entryValue.isObject()) {
                populateJsonObject(entryValue, privatePropertyLst, encodedMap);
            }
        });
    }
}
