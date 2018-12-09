package io.opensaber.registry.shard.advisory;

import io.opensaber.registry.model.DBConnectionInfo;

public interface IShardAdvisory {
	
	public DBConnectionInfo connectionInfo(String subject);
	

}
