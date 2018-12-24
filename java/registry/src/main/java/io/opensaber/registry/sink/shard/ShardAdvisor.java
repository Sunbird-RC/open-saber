package io.opensaber.registry.sink.shard;

import io.opensaber.registry.model.DBConnectionInfoMgr;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ShardAdvisor {
	
	private DBConnectionInfoMgr dbConnectionInfoMgr;
	private Map<String, IShardAdvisor> advisors = new HashMap<String, IShardAdvisor>();
	private final static String DEFAULT_SHARD_ADVISOR = "DefaultShardAdvisor";
	private final static String SERIAL_NUMBER_SHARD_ADVISOR = "SerialNumberShardAdvisor";

	public ShardAdvisor(DBConnectionInfoMgr dbConnectionInfoMgr) {
		this.dbConnectionInfoMgr = dbConnectionInfoMgr;
		
		// Registers all the shardAdvisors by class name.		
		advisors.put(DEFAULT_SHARD_ADVISOR, new DefaultShardAdvisor(dbConnectionInfoMgr));
		advisors.put(SERIAL_NUMBER_SHARD_ADVISOR, new SerialNumberShardAdvisor(dbConnectionInfoMgr));
	}

	/**
	 * Return ShardAdvice registered with the property
	 *
	 * @return
	 * @throws IOException
	 */

	public IShardAdvisor getShardAdvisor() {
		IShardAdvisor advisory = advisors.getOrDefault(dbConnectionInfoMgr.getShardAdvisor(),
				new DefaultShardAdvisor(dbConnectionInfoMgr));
		return advisory;
	}

}
