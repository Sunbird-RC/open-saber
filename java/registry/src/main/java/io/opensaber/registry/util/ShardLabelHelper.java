package io.opensaber.registry.util;

public class ShardLabelHelper {
	private final static String SEPARATOR = "-";
	/**
	 * Forms label with shard info
	 * @param shardLabel
	 * @param recordId
	 * @return
	 */
	public static String getLabel(String shardLabel, String recordId) {
        return shardLabel + SEPARATOR + recordId;
    }
	/**
	 * Extract shard info
	 * @param label
	 * @return
	 */
    public static String getShardName(String label) {
        return label.substring(0, label.indexOf(SEPARATOR));
    }
    /**
     * verifies label of this shard type
     * @param label
     * @param recordId
     * @return
     */
    public static boolean isShardLabel(String label) {
        return label.contains(SEPARATOR);
    }

}
