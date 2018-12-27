package io.opensaber.registry.util;

public class RecordIdentifier {

	private final static String SEPARATOR = "-";
	private final static String REGEX_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private String shardLabel;
	private String uuid;

	public RecordIdentifier(String shardLabel, String uuid) {
		this.shardLabel = shardLabel;
		this.uuid = uuid;
	}

	public String getShardLabel() {
		return shardLabel;
	}

	public String getUuid() {
		return uuid;
	}

	/**
	 * Returns spring representation of RecordIdentifier Format of
	 * representation is: shard "-" uuid
	 */
	@Override
	public String toString() {
		String label = "";
		if (this.getShardLabel() != null && this.getUuid() != null) {
			label = this.getShardLabel() + SEPARATOR + this.getUuid();
		} else if (this.getUuid() != null && isUUIDValid(this.getUuid())) {
			label = this.getUuid();
		}
		return label;
	}

	/**
	 * Creates RecordIdentifier object from a string representation
	 * 
	 * @param label
	 * @return
	 */
	public static RecordIdentifier parse(String label) {
		RecordIdentifier recordId = null;
		String uuid = label.substring(label.indexOf(SEPARATOR) + 1, label.length());

		if (isUUIDValid(label)) {
			recordId = new RecordIdentifier(null, label);
		} else if (isUUIDValid(uuid)) {
			String shardLabel = label.substring(0, label.indexOf(SEPARATOR));
			recordId = new RecordIdentifier(shardLabel, uuid);
		}

		return recordId;
	}

	private static boolean isUUIDValid(String uuid) {
		return uuid.matches(REGEX_UUID);
	}

}
