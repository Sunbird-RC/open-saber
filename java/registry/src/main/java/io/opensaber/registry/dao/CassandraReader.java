package io.opensaber.registry.dao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.common.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CassandraReader {
    String keySpace = null;
    private static Set<String> keySet = null;
    CassandraOperation cassandraOperation = null;
    CassandraConnectionManager cassandraConnectionManager = null;

    CassandraReader(String keySpace, Set<String> entitySet) {
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

    public JsonNode read(String entityType, String uuid){
        Map<String,Object> responseMap = new HashMap<>();
        Response response = cassandraOperation.getRecordById(keySpace, entityType, uuid);
        Map<String, Object> result  = response.getResult();
        List<HashMap<String,Object>> lst = (List<HashMap<String, Object>>) response.getResult().get("response");
        Map<String,Object> entityMap = lst.get(0);
        responseMap.put(entityType,entityMap);
        keySet.forEach(key -> {
            if(entityMap.containsKey(key.toLowerCase()) &&  null != (String)entityMap.get(key.toLowerCase())) {
                Response subResponse = cassandraOperation.getRecordById(keySpace,key, (String) entityMap.get(key.toLowerCase()));
                List<HashMap<String,Object>> sublst = (List<HashMap<String, Object>>) subResponse.getResult().get("response");
                Map<String,Object> subentityMap = sublst.get(0);
                entityMap.remove(key.toLowerCase());
                entityMap.put(key,subentityMap);
            }
        });
        JsonNode jsonNode = new ObjectMapper().convertValue(entityMap,JsonNode.class);
        return jsonNode;
    }
}
