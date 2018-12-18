package io.opensaber.registry.sink.shard;

import io.opensaber.registry.sink.DatabaseProvider;

public class ShardDatabaseProvider {
	
	private String shardId;
	private DatabaseProvider databaseProvider;
	
	public ShardDatabaseProvider(String shardId, DatabaseProvider databaseProvider){
		this.shardId = shardId;
		this.databaseProvider = databaseProvider;
	}
	public String getShardId() {
		return shardId;
	}

	public DatabaseProvider getDatabaseProvider() {
		return databaseProvider;
	}

}
