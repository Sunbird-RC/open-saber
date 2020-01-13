package io.opensaber.registry.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opensaber.registry.exception.audit.AuditException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { ObjectMapper.class })
public class AuditHelperTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void getSearchQueryNodeForAuditTest() throws IOException, AuditException {
		String inputJson = "{ \"entityType\": [\"Teacher\"], \"id\":\"09d9c84a-0696-400f-8e5a-65fb30333ce5\", \"action\":\"ADD\", \"startDate\":1578393274000, \"limit\": 3, \"offset\": 0 }";

		JsonNode jsonNode = null;
		jsonNode = objectMapper.readTree(inputJson);
		JsonNode result = AuditHelper.getSearchQueryNodeForAudit(jsonNode, "osid");
		assertEquals("ADD", result.get("filters").get("action").get("eq").asText());
	}
}
