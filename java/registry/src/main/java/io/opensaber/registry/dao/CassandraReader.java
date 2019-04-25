package io.opensaber.registry.dao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.sink.CassandraDBProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.common.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * This class provides methods for reading the data from Cassandra
 */
public class CassandraReader {
    String keySpace = null;
    String uuidPropertyName = null;
    CassandraOperation cassandraOperation = null;

    private Logger logger = LoggerFactory.getLogger(CassandraReader.class);

    CassandraReader(String keySpace, DBConnectionInfoMgr dbConnectionInfoMgr, String uuidPropertyName) {
        this.keySpace = keySpace;
        this.uuidPropertyName = uuidPropertyName;
        CassandraDBProvider cassandraDBProvider = new CassandraDBProvider();
        cassandraOperation = cassandraDBProvider.getCassandraOperation(dbConnectionInfoMgr, keySpace);
    }

    /**
     * This method reads the data from cassandra with inputs entity-type and osid
     * @param entityType - table name
     * @param osid
     * @return
     */
    public JsonNode read(String entityType, String osid) {
        Map<String, Object> responseMap = new HashMap<>();
        Response response = cassandraOperation.getRecordById(keySpace, entityType, osid);
        List<HashMap<String, Object>> lst = (List<HashMap<String, Object>>) response.getResult().get("response");
        HashMap<String, Object> entityMap = lst.get(0);
        responseMap.put(entityType, entityMap);
        try {
            entityMap.entrySet().stream().
                    filter(entry -> entry.getKey().contains(uuidPropertyName)).
                    forEach(entry -> {
                        String[] newKey = entry.getKey().split("_");
                        if (null == entityMap.get(entry.getKey())) {
                            entityMap.remove(entry.getKey());
                        } else if (entityMap.get(entry.getKey()) instanceof String) {
                            Response subResponse = cassandraOperation.getRecordById(keySpace, newKey[0], (String) entityMap.get(entry.getKey()));
                            List<HashMap<String, Object>> sublst = (List<HashMap<String, Object>>) subResponse.getResult().get("response");
                            Map<String, Object> subentityMap = sublst.get(0);
                            entityMap.put(newKey[0], subentityMap);
                            entityMap.remove(entry.getKey());

                        } else if (entityMap.get(entry.getKey()) instanceof List && ((List) entityMap.get(entry.getKey())).size() > 0) {
                            Response subResponse = cassandraOperation.getRecordsByPrimaryKeys(keySpace, newKey[0], (List<String>) entityMap.get(entry.getKey()), null);
                            List<HashMap<String, Object>> sublst = (List<HashMap<String, Object>>) subResponse.getResult().get("response");
                            entityMap.put(newKey[0], sublst);
                            entityMap.remove(entry.getKey());
                        }

                    });
        } catch (Exception e) {
            logger.error("exception in read {}",e);
        }
        JsonNode jsonNode = new ObjectMapper().convertValue(responseMap, JsonNode.class);
        return jsonNode;
    }
}
