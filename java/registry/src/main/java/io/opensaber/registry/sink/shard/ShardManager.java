package io.opensaber.registry.sink.shard;

import io.opensaber.registry.exception.CustomException;
import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.SearchService;
import io.opensaber.registry.sink.DBProviderFactory;
import io.opensaber.registry.sink.DatabaseProvider;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component("shardManager")
public class ShardManager {
    private static Logger logger = LoggerFactory.getLogger(ShardManager.class);
	@Autowired
	private DBConnectionInfoMgr dbConnectionInfoMgr;
	@Autowired
	private DBProviderFactory dbProviderFactory;
	@Autowired
	private IShardAdvisor shardAdvisor;	
	@Autowired
	private SearchService searchService;
	private ShardDatabaseProvider shardDbProvider;
	
	/**
	 * intiatiate a DBShard and ensure activating a databaseProvider.
	 * used for add end point. 
	 * @param attributeValue
	 * @throws IOException
	 */
	private ShardDatabaseProvider activateDbShard(Object attributeValue) throws CustomException {
		DBConnectionInfo connectionInfo = shardAdvisor.getShard(attributeValue);
	    DatabaseProvider databaseProvider = dbProviderFactory.getInstance(connectionInfo);
	    shardDbProvider = new ShardDatabaseProvider(connectionInfo.getShardId(), databaseProvider);
		searchService.setDatabaseProvider(databaseProvider);
		logger.info("Activate db shard "+connectionInfo.getShardId()+" for attribute value "+attributeValue);
		return shardDbProvider;
	}

	public String getShardProperty() {
		return dbConnectionInfoMgr.getShardProperty();
	}

	public ShardDatabaseProvider getDatabaseProvider(Object attributeValue) throws CustomException {
		return activateDbShard(attributeValue);
	}

	public ShardDatabaseProvider getDefaultDatabaseProvider() throws CustomException {
		return activateDbShard(null);
	}
	/**
	 * Get a databaseProvider given a shardId from entity cache
	 * @param shardId
	 * @return
	 */
	public DatabaseProvider getDatabaseProvider(String shardId) throws CustomException{
		DatabaseProvider databaseProvider = null;
		DBConnectionInfo connectionInfo = dbConnectionInfoMgr.getDBConnectionInfo(shardId);
		if(connectionInfo != null){
			databaseProvider = dbProviderFactory.getInstance(connectionInfo);
		}else{
			logger.error("Exception thrown:"+shardId+" ShardId is invalid");
			throw new CustomException(shardId+" shardId is invalid");
		}
		return databaseProvider;
	}

}
