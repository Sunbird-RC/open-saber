package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.util.DefinitionsManager;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CassandraWriter {
    DefinitionsManager definitionsManager;

    String keySpace = null;
    CassandraOperation cassandraOperation = null;
    CassandraConnectionManager cassandraConnectionManager = null;
    private static Set<String> keySet = null;

    CassandraWriter(DefinitionsManager definitionsManager, String keySpace, Set<String> entitySet) {
        this.definitionsManager = definitionsManager;
        this.keySpace = keySpace;
        keySet = entitySet;
        if(null == cassandraConnectionManager) {
            getConnection();
        }
    }

    void getConnection() {
        cassandraOperation = ServiceFactory.getInstance();
        cassandraConnectionManager =
                CassandraConnectionMngrFactory.getObject("standalone");
        boolean result =
                cassandraConnectionManager.createConnection("127.0.0.1", "9042", null, null, keySpace);
    }

    /**
     * Writes the node entity into the database.
     *
     * @param node
     * @return
     */
    public String writeNodeEntity(JsonNode node) {
        Vertex resultVertex = null;
        String rootOsid = null;
        Iterator<Map.Entry<String, JsonNode>> entryIterator = node.fields();
        while (entryIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = entryIterator.next();
            ObjectNode entryObject = (ObjectNode) entry.getValue();
            // It is expected that node is wrapped under a root, which is the
            // parent name/definition
            if (entry.getValue().isObject()) {
               /* resultVertex = processNode(entry.getKey(), entry.getValue());
                rootOsid = databaseProvider.getId(resultVertex);
                entryObject.put(uuidPropertyName,rootOsid);*/
            }
        }
        return rootOsid;
    }

    public String addToCassandra(JsonNode mapObject) {
        AtomicReference<String> id = null;
        mapObject.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            //logger.debug("Processing {} -> {}", entry.getKey(), entry.getValue());
            if (entryValue.isObject()) {
                // Recursive calls
                id.set(writeSingleNode(entry.getKey(), entryValue));
            } else if (entryValue.isArray()) {
                writeArrayNode(entry.getKey(), (ArrayNode) entry.getValue());
            }
        });
        return id.get();
    }

    String writeSingleNode(String key, JsonNode jsonValue) {
        ObjectMapper objectMapper = new ObjectMapper();
        String id = UUID.randomUUID().toString();
        Map<String, Object> subEntity =  JSONUtil.convertJsonNodeToMap(jsonValue);
        keySet.forEach(key1 -> {
            if(subEntity.containsKey(key1)) {
                JsonNode subNode = jsonValue.get(key1);
                /*Map<String,Object> subMap = (Map<String, Object>) subEntity.get(key1);
                JsonNode subEntityNode = objectMapper.convertValue(subMap, JsonNode.class);*/
                if(subNode.isObject()){
                    String subNodeId = writeSingleNode(key1,subNode);
                    subEntity.put(key1,subNodeId);
                } else if(subNode.isArray()) {
                    Set<String> subNodeIdSet = writeArrayNode(key1,(ArrayNode)subNode);
                    subEntity.put(key1,subNodeIdSet);
                }
            }
        });
        subEntity.put("id",id);
        cassandraOperation.insertRecord(keySpace,key,subEntity);
        return id;
    }

    Set<String> writeArrayNode(String entryKey, ArrayNode arrayNode) {
        Set<String> idSet = new LinkedHashSet<>();
        for (JsonNode jsonNode : arrayNode) {
            if (jsonNode.isObject()) {
                String id = writeSingleNode(entryKey, jsonNode);
                idSet.add(id);
            }
        }
        return idSet;
    }

}
