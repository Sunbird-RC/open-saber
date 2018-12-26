package io.opensaber.registry.util;

public class ShardLabelHelper {
	private final static String SEPARATOR = "-";
	private final static String REGEX_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	/**
	 * Constructs label for response 
	 * @param recordId
	 * @return
	 */
	public static String getLabel(RecordIdentifier recordId) {
		String label = "";
		if (recordId.getShardLevel() != null && recordId.getUuid() != null) {
			label = recordId.getShardLevel() + SEPARATOR + recordId.getUuid();
		} else if (recordId.getUuid() != null && isUUIDValid(recordId.getUuid())) {
			label = recordId.getUuid();
		}
		return label;
	}

	private static boolean isUUIDValid(String uuid) {
		return uuid.matches(REGEX_UUID);
	}

	public static RecordIdentifier getRecordIdentifier(String label) {
		RecordIdentifier recordId = null;
		String shardLabel = label.substring(0, label.indexOf(SEPARATOR));
		String uuid = label.substring(label.indexOf(SEPARATOR) + 1, label.length());
		if (isUUIDValid(uuid)) {
			recordId = new RecordIdentifier(shardLabel, uuid);
		}
		return recordId;
	}

}
