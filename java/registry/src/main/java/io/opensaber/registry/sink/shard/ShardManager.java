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

	@Autowired
	private Shard shard;


	/**
	 * intiatiate a DBShard and ensure activating a databaseProvider. used for
	 * add end point.
	 * 
	 * @param attributeValue
	 * @throws IOException
	 */
	private void activateDbShard(Object attributeValue) throws CustomException {
		DBConnectionInfo connectionInfo = shardAdvisor.getShard(attributeValue);
	    DatabaseProvider databaseProvider = dbProviderFactory.getInstance(connectionInfo);
	    shard.setShardId(connectionInfo.getShardId());
	    shard.setDatabaseProvider(databaseProvider);
		logger.info("Activated shard "+connectionInfo.getShardId()+" for attribute value "+attributeValue);
	}

	public String getShardProperty() {
		return dbConnectionInfoMgr.getShardProperty();
	}
	public String getUUIDPropertyName(){
		return dbConnectionInfoMgr.getUuidPropertyName();
	}

	/**
	 * activates a shard (Default or others) and returns it.
	 * @param attributeValue
	 * @return
	 * @throws CustomException
	 */
	public Shard getShard(Object attributeValue) throws CustomException {

		if(attributeValue != null){
			activateDbShard(attributeValue);
		}else{
			activateDbShard(null);
		}
		return shard;
	}
	/**
	 * Default shard return first shard.
	 * Atleast one shard configuration is mandatory.
	 * @return
	 * @throws CustomException
	 */
	public Shard getDefaultShard() throws CustomException {
		activateDbShard(null);
		return shard;
	}

	/**
	 * activate a shard given a shardId from entity cache 
	 * use this for read operation
	 * @param shardId
	 * @return
	 * @throws CustomException 
	 */
	public void activateShard(String shardId) throws CustomException{
		if (shardId != null) {
			DBConnectionInfo connectionInfo = dbConnectionInfoMgr.getDBConnectionInfo(shardId);
			DatabaseProvider databaseProvider = dbProviderFactory.getInstance(connectionInfo);
			shard.setShardId(connectionInfo.getShardId());
			shard.setDatabaseProvider(databaseProvider);
		} else {
			logger.info("Default shard is activated");
			activateDbShard(null);

		}
	}

}
