package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.sink.CassandraDBProvider;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CassandraWriter {

    String keySpace = null;
    CassandraOperation cassandraOperation = null;
    private static Set<String> keySet = null;

    CassandraWriter(String keySpace, Set<String> entitySet, DBConnectionInfoMgr dbConnectionInfoMgr) {
        this.keySpace = keySpace;
        keySet = entitySet;
        CassandraDBProvider cassandraDBProvider = new CassandraDBProvider();
        cassandraOperation = cassandraDBProvider.getCassandraOperation(dbConnectionInfoMgr, keySpace);
    }

    public String addToCassandra(JsonNode mapObject) {
        AtomicReference<String> id = new AtomicReference<>();
        mapObject.fields().forEachRemaining(entry -> {
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

    private String writeSingleNode(String key, JsonNode jsonValue) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> subEntity =  JSONUtil.convertJsonNodeToMap(jsonValue);
        keySet.forEach(key1 -> {
            if(subEntity.containsKey(key1)) {
                JsonNode subNode = jsonValue.get(key1);
                if(subNode.isObject()){
                    String subNodeId = writeSingleNode(key1,subNode);
                    subEntity.put(key1,subNodeId);
                } else if(subNode.isArray()) {
                    List<String> subNodeIdList = writeArrayNode(key1,(ArrayNode)subNode);
                    subEntity.put(key1,subNodeIdList);
                }
            }
        });
        subEntity.put("id",id);
        cassandraOperation.insertRecord(keySpace,key,subEntity);
        return id;
    }

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
