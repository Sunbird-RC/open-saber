package io.opensaber.registry.util;

public class RecordIdentifier {

	private String shardLevel;
	private String uuid;

	public RecordIdentifier() {
	}

	public RecordIdentifier(String shardLevel, String uuid) {
		this.shardLevel = shardLevel;
		this.uuid = uuid;
	}

	public String getShardLevel() {
		return shardLevel;
	}

	public void setShardLevel(String shardLevel) {
		this.shardLevel = shardLevel;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

}
