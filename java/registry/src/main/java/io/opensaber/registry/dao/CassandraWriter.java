package io.opensaber.registry.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.sink.CassandraDBProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;

import javax.json.Json;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class provides methods for cassandra backend support to OS for persisting data
 */
public class CassandraWriter {

    String keySpace = null;
    String uuidPropertyName = null;
    CassandraOperation cassandraOperation = null;
    ObjectMapper objectMapper = new ObjectMapper();

    Logger logger = LoggerFactory.getLogger(CassandraWriter.class);

    CassandraWriter(String keySpace, DBConnectionInfoMgr dbConnectionInfoMgr, String uuidPropertyName) {
        this.keySpace = keySpace;
        this.uuidPropertyName = uuidPropertyName;
        CassandraDBProvider cassandraDBProvider = new CassandraDBProvider();
        cassandraOperation = cassandraDBProvider.getCassandraOperation(dbConnectionInfoMgr, keySpace);
    }

    /**
     * This is the starting method for adding JsonNode to cassandra
     * @param entityObject
     * @return entity id
     */
    public String writeNodeEntity(JsonNode entityObject) {
        AtomicReference<String> id = new AtomicReference<>();
        entityObject.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            if (entryValue.isObject()) {
                // Recursive calls
                id.set(writeSingleNode(entry.getKey(), entryValue));
            } else if (entryValue.isArray()) {
                writeArrayNode(entry.getKey(), (ArrayNode) entry.getValue());
            }
        });
        return id.get();
    }

    /**
     * This method persists the singlenode as table(entitytype as table name) and process furthur any sub-entities is present by calling
     * either recursively or writeArrayNode
     * @param entityType - table name
     * @param jsonValue - Input JsonNode
     * @return
     */
    private String writeSingleNode(String entityType, JsonNode jsonValue) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> subEntity = JSONUtil.convertJsonNodeToMap(jsonValue);
        jsonValue.fields().forEachRemaining(entry -> {
            if (entry.getValue().isObject()) {
                String subNodeId = writeSingleNode(entry.getKey(), entry.getValue());
                subEntity.remove(entry.getKey());
                subEntity.put(entry.getKey() + "_" + uuidPropertyName, subNodeId);
            } else if (entry.getValue().isArray()) {
                List<String> subNodeIdList = null;
                if (entry.getValue().get(0).isValueNode()) {
                    objectMapper = new ObjectMapper();
                    ObjectReader objectReader = objectMapper.reader().forType(new TypeReference<List<String>>() {
                    });
                    List<String> idList = null;
                    try {
                        idList = objectReader.readValue(entry.getValue());
                    } catch (IOException e) {
                        logger.error("Exception in writeSingleNode {}", e);
                    }
                    subEntity.put(entry.getKey(), idList);
                } else {
                    subNodeIdList = writeArrayNode(entry.getKey(), (ArrayNode) entry.getValue());
                    subEntity.remove(entry.getKey());
                    subEntity.put(entry.getKey() + "_" + uuidPropertyName, subNodeIdList);

                }
            }

        });
        subEntity.put("id", id);
        cassandraOperation.insertRecord(keySpace, entityType, subEntity);
        return id;
    }

    /**
     * This method process the arraynode as individual nodes by iterating.
     * @param entryKey - table name
     * @param arrayNode - Input ArrayNode
     * @return
     */
    private List<String> writeArrayNode(String entryKey, ArrayNode arrayNode) {
        List<String> idList = new ArrayList<>();
        for (JsonNode jsonNode : arrayNode) {
            if (jsonNode.isObject()) {
                String id = writeSingleNode(entryKey, jsonNode);
                idList.add(id);
            }
        }
        return idList;
    }

}
