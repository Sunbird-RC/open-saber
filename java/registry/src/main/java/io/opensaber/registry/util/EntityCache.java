package io.opensaber.registry.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EntityCache {
	
    private static Logger logger = LoggerFactory.getLogger(EntityCache.class);
    private final static String RECORD_NOT_FOUND ="Record not found" ;
	@Autowired
	private EntityCacheManager entityCacheManager;	
	private Map<String, List<String>> recordShardMap;
	
	public EntityCache(){
		this.recordShardMap = entityCacheManager.getShardUUIDs();
	}
	
	/**
	 * Provide the shard only for a given record.
	 * @param recordId
	 * @return
	 * @throws IOException
	 */
	public String getShard(String recordId) throws IOException{		
		 for (Entry<String, List<String>> entry : recordShardMap.entrySet()) {
		        String shardId = entry.getKey();
		        if(entry.getValue().contains(recordId)){
		        	logger.info("Record "+recordId+" found a match in cache for shard "+shardId);
		        	return shardId;
		        }else{
		        	logger.error("Record "+recordId+" not found in cache");
		        	throw new IOException(RECORD_NOT_FOUND); 
		        }
		    }		 
		return null;
	}
	

}
