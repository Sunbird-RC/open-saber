package io.opensaber.registry.helper;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import io.opensaber.registry.exception.CustomException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.service.ISearchService;

@RunWith(SpringRunner.class)
@ActiveProfiles(Constants.TEST_ENVIRONMENT)
@SpringBootTest(classes = { ObjectMapper.class })
public class RegistryHelperTest {

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@InjectMocks
	private RegistryHelper registerHelper;

	@Autowired
	private ObjectMapper objectMapper;

	@Mock
	private ISearchService searchService;

	@Before
	public void initialize() throws IOException {

	}

	@Test
	public void getAuditLogTest() throws Exception {

		// Data creation
		String inputJson = "{ \"entityType\": [\"Teacher\"], \"id\":\"09d9c84a-0696-400f-8e5a-65fb30333ce5\", \"action\":\"ADD\", \"startDate\":1578393274000, \"limit\": 3, \"offset\": 0 }";

		String result = "{ \"Teacher_Audit\": [{ \"auditId\": \"66fecb96-b87c-44b5-a930-3de96503aa13\", \"recordId\": \"09d9c84a-0696-400f-8e5a-65fb30333ce5\","
				+ " \"timeStamp\": \"2019-12-23 16:56:50.905\", \"date\": 1578566074000, \"@type\": \"Teacher_Audit\", \"action\": \"ADD\", "
				+ "\"auditJson\": [ \"op\", \"path\" ], \"osid\": \"1-d28fd315-bc28-4db0-b7f8-130ff164ba01\", \"userId\": \"35448199-0a7b-4491-a796-b053b9fcfd29\","
				+ " \"transactionId\": [ 870924631 ] }] }";

		JsonNode jsonNode = null;
		JsonNode resultNode = null;
		jsonNode = objectMapper.readTree(inputJson);
		resultNode = objectMapper.readTree(result);

		Mockito.when(searchService.search(ArgumentMatchers.any())).thenReturn(resultNode);
		JsonNode node = registerHelper.getAuditLog(jsonNode);
		Assert.assertEquals(jsonNode.get("id"), node.get("Teacher_Audit").get(0).get("recordId"));
	}

	@Test
	public void getAuditLog_entitytype_missing() throws Exception {
		String inputJson = "{ \"id\":\"09d9c84a-0696-400f-8e5a-65fb30333ce5\", \"action\":\"ADD\", \"startDate\":1578393274000, \"limit\": 3, \"offset\": 0 }";
		JsonNode jsonNode = objectMapper.readTree(inputJson);
		exception.expect(NullPointerException.class);
		exception.expectMessage("entityType cannot be null");
		registerHelper.getAuditLog(jsonNode);

	}

	@Test
	public void getAuditLog_entitytype_not_an_array() throws Exception {
		String inputJson = "{\"entityType\": \"Teacher\", \"id\":\"09d9c84a-0696-400f-8e5a-65fb30333ce5\", \"action\":\"ADD\", \"startDate\":1578393274000, \"limit\": 3, \"offset\": 0 }";
		JsonNode jsonNode = objectMapper.readTree(inputJson);
		exception.expect(CustomException.class);
		exception.expectMessage("entityType should be an array");
		registerHelper.getAuditLog(jsonNode);

	}

	@Test
	public void getAuditLog_entitytype_empty_array() throws Exception {
		String inputJson = "{\"entityType\": [], \"id\":\"09d9c84a-0696-400f-8e5a-65fb30333ce5\", \"action\":\"ADD\", \"startDate\":1578393274000, \"limit\": 3, \"offset\": 0 }";
		JsonNode jsonNode = objectMapper.readTree(inputJson);
		exception.expect(CustomException.class);
		exception.expectMessage("entityType should not be an empty array");
		registerHelper.getAuditLog(jsonNode);

	}



}