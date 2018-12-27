package io.opensaber.registry.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecordIdentifierTest {
	
	@Test
	public void testToString(){
		RecordIdentifier rid = new RecordIdentifier("shardId", "5701a670-644f-406e-902b-684b507bb89f");
		assertTrue(rid.toString().equalsIgnoreCase("shardId-5701a670-644f-406e-902b-684b507bb89f"));
	}
	
	@Test
	public void testToStringWithNoShardId(){
		RecordIdentifier rid = new RecordIdentifier(null, "5701a670-644f-406e-902b-684b507bb89f");
		assertTrue(rid.toString().equalsIgnoreCase("5701a670-644f-406e-902b-684b507bb89f"));
	}

	@Test
	public void testParse() {
		String label = "ShardIdentifier-5701a670-644f-406e-902b-684b507bb89f";
		RecordIdentifier resultRecordId = RecordIdentifier.parse(label);
		System.out.println("resultRecordId. " + resultRecordId);
		assertTrue(resultRecordId.getShardLabel().equalsIgnoreCase("ShardIdentifier"));
		assertTrue(resultRecordId.getUuid().equalsIgnoreCase("5701a670-644f-406e-902b-684b507bb89f"));
	}

	@Test
	public void testParseForInvalidRecordId() {
		String label = "ShardIdentifier-0000x000-0000-00xx-000X-000x00xx";
		RecordIdentifier resultRecordId = RecordIdentifier.parse(label);
		assertFalse(resultRecordId != null);
	}

	@Test
	public void testParseForNoShardId() {
		String label = "5701a670-644f-406e-902b-684b507bb89f";
		RecordIdentifier resultRecordId = RecordIdentifier.parse(label);
		assertTrue(resultRecordId.getUuid().equalsIgnoreCase("5701a670-644f-406e-902b-684b507bb89f"));
		assertTrue(resultRecordId.getShardLabel() == null);
	}

}
