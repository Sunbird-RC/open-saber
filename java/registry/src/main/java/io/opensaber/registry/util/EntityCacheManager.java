package io.opensaber.registry.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.sink.DBProviderFactory;
import io.opensaber.registry.sink.DatabaseProvider;

@Component
public class EntityCacheManager {

	private static Logger logger = LoggerFactory.getLogger(EntityCacheManager.class);

	@Autowired
	private DBProviderFactory dbProviderFactory;
	private Set<String> defintionNames;
	private List<DBConnectionInfo> dbConnectionInfoList;
	private Map<String, List<String>> shardUUIDSMap;

	@Autowired
	public EntityCacheManager(DefinitionsManager definitionsManager, DBConnectionInfoMgr dbConnectionInfoMgr) {
		this.defintionNames = definitionsManager.getAllKnownDefinitions();
		this.dbConnectionInfoList = dbConnectionInfoMgr.getConnectionInfo();
		shardUUIDSMap = new HashMap<>();
	}

	public void loadShardUUIDS() {

		dbConnectionInfoList.forEach(dbConnectionInfo -> {
			DatabaseProvider dbProvider = dbProviderFactory.getInstance(dbConnectionInfo);
			List<String> uuids = new ArrayList<>();
			defintionNames.forEach(defintionName -> {
				uuids.addAll(TPGraphMain.getUUIDs(defintionName, dbProvider));
			});
			shardUUIDSMap.put(dbConnectionInfo.getShardId(), uuids);
		});

		logger.info("shard's UUIDS map size: " + shardUUIDSMap.size());
	}

	public Map<String, List<String>> getShardUUIDs() {
		return shardUUIDSMap;
	}

}
