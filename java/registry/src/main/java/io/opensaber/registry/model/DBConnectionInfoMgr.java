package io.opensaber.registry.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("dbConnectionInfoMgr")
@ConfigurationProperties(prefix = "database")
@Validated
public class DBConnectionInfoMgr {

	/**
	 * The value names the unique property to be used by this registry for
	 * internal identification purposes.
	 */
	@NotEmpty
	private String uuidPropertyName;

	/**
	 * only one type of database provider as the target as of today.
	 */
	@NotEmpty
	private String provider;

	/**
	 * Only one property is allowed.
	 */
	private String shardProperty;

	/**
	 * Each DBConnectionInfo is a shard connection information.
	 */
	@Size(min = 1)
	private List<DBConnectionInfo> connectionInfo = new ArrayList<>();
	/**
	 * Instructs which advisor to pick up across each connectionInfo Only one
	 * advisor allowed
	 */
	private String shardAdvisorClassName;
	private Map<String, String> shardLabelIdMap = new HashMap<>();

	@PostConstruct
	public void init() {
		for (DBConnectionInfo connInfo : connectionInfo) {
			boolean shardIdExists = shardLabelIdMap.containsValue(connInfo.getShardId());
			String shardId = shardLabelIdMap.putIfAbsent(connInfo.getShardLabel(), connInfo.getShardId());
			if (shardId!=null) {
				throw new RuntimeException("Exception: Configured shards must have unique label. Offending label = " + connInfo.getShardLabel());
			}
			if (shardIdExists) {
				throw new RuntimeException("Exception: Configured shards must have unique id. Offending id = " + connInfo.getShardId());
			}
		}
	}

	public List<DBConnectionInfo> getConnectionInfo() {
		return connectionInfo;
	}

	/**
	 * To provide a connection info on based of a shard identifier(name)
	 *
	 * @param shardId
	 * @return
	 */
	public DBConnectionInfo getDBConnectionInfo(String shardId) {
		for (DBConnectionInfo con : connectionInfo) {
			if (con.getShardId().equalsIgnoreCase(shardId))
				return con;
		}
		return null;
	}

	public String getUuidPropertyName() {
		return uuidPropertyName;
	}

	public void setUuidPropertyName(String uuidPropertyName) {
		this.uuidPropertyName = uuidPropertyName;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public void setShardProperty(String shardProperty) {
		this.shardProperty = shardProperty;
	}

	public String getShardProperty() {
		return this.shardProperty;
	}

	public void setConnectionInfo(List<DBConnectionInfo> connectionInfo) {
		this.connectionInfo = connectionInfo;
	}

	public String getShardAdvisorClassName() {
		return shardAdvisorClassName;
	}

	public void setShardAdvisorClassName(String shardAdvisorClassName) {
		this.shardAdvisorClassName = shardAdvisorClassName;
	}

	public String getShardId(String shardLabel) {
		return shardLabelIdMap.getOrDefault(shardLabel, null);
	}
}
