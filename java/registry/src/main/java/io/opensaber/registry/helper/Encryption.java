package io.opensaber.registry.helper;

import com.fasterxml.jackson.databind.JsonNode;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Encryption {
    @Autowired
    EncryptionService encryptionService;

    public Map<String,Object> createEncryptedJson(JsonNode rootNode, List<String> privatePropertyLst) throws EncryptionException {
        Map<String, Object> encMap = new HashMap<String, Object>();
        if(rootNode.isObject()){
            populateObject(rootNode,privatePropertyLst,encMap);
        }
        return (Map<String, Object>)encryptionService.encrypt(encMap);
    }

    private void populateObject(JsonNode rootNode, List<String> privatePropertyLst, Map<String, Object> encMap) {
        rootNode.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            if(entryValue.isValueNode()) {
                if(privatePropertyLst.contains(entry.getKey()))
                    encMap.put(entry.getKey(),entryValue.asText());
            } else if(entryValue.isObject()) {
                populateObject(entryValue, privatePropertyLst, encMap);
            }
        });
    }
}
