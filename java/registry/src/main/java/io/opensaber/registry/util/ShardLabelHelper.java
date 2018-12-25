package io.opensaber.registry.util;

public class ShardLabelHelper {
	private final static String SEPARATOR = "-";
	private final static String REGEX_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	/**
	 * Forms label with shard info
	 * 
	 * @param shardLabel
	 * @param recordId
	 * @return
	 */
	public static String getLabel(String shardLabel, String recordId) {
		return shardLabel + SEPARATOR + recordId;
	}

	/**
	 * Extract shard info
	 * 
	 * @param label
	 * @return
	 */
	public static String getShardName(String label) {
		return label.substring(0, label.indexOf(SEPARATOR));
	}

	/**
	 * verifies label of this shard type
	 * 
	 * @param label
	 * @param recordId
	 * @return
	 */
	public static boolean isShardLabel(String label) {
		String uuid = label.substring(label.indexOf(SEPARATOR) + 1, label.length());
		return uuid.matches(REGEX_UUID);
	}

}
