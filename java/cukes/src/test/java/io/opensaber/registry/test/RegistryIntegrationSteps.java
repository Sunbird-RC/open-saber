package io.opensaber.registry.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.node.ObjectNode;

import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;



/**
 * 
 * @author jyotsna
 *
 */
public class RegistryIntegrationSteps extends RegistryTestBase{
	
	private static final String VALID_JSONLD= "school.jsonld";
	private static final String INVALID_LABEL_JSONLD = "invalid-label.jsonld";
	private static final String ADD_ENTITY = "addEntity";
	private static final String CONTEXT_CONSTANT = "sample:";
	
	private RestTemplate restTemplate;
	private String baseUrl;
	private static String duplicateLabel;
	
	
	@Before
	public void initializeData(){
		restTemplate = new RestTemplate();
		baseUrl = generateBaseUrl();
	}
	
	
	@Given("^First input data and base url are valid")
	public void jsonldData(){
		setJsonld(VALID_JSONLD);
		assertNotNull(jsonld);
		assertNotNull(restTemplate);
		assertNotNull(baseUrl);
	}
	
	@When("^Inserting first valid record into the registry")
	public void addEntity(){
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String label = generateRandomId();
		duplicateLabel = label;
		setJsonldWithNewRootLabel(CONTEXT_CONSTANT+label);
		HttpEntity entity = new HttpEntity(jsonld,headers);
		ResponseEntity response = restTemplate.postForEntity(baseUrl+ADD_ENTITY,
				entity,ObjectNode.class);
		ObjectNode obj = (ObjectNode)response.getBody();
		assertNotNull(obj);
		assertEquals(obj.get(JsonKeys.RESPONSE).asText(), JsonKeys.SUCCESS);
	}
	
	@Then("^Response for first valid record is (.*)")
	public void verifyResponse(String response){
		assertNotNull(response);
		assertTrue(response.contains(JsonKeys.SUCCESS));
	}

	@Given("^Valid duplicate data")
	public void jsonldDuplicateData(){
		setJsonld(VALID_JSONLD);
		assertNotNull(jsonld);
		assertNotNull(restTemplate);
		assertNotNull(baseUrl);
	}
	
	@When("^Inserting a duplicate record into the registry")
	public void addDuplicateEntity(){
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		setJsonldWithNewRootLabel(CONTEXT_CONSTANT+duplicateLabel);
		HttpEntity entity = new HttpEntity(jsonld,headers);
		ResponseEntity response = restTemplate.postForEntity(baseUrl+ADD_ENTITY,
				entity,ObjectNode.class);
		ObjectNode obj = (ObjectNode)response.getBody();
		assertNotNull(obj);
		assertEquals(obj.get(JsonKeys.RESPONSE).asText(), Constants.DUPLICATE_RECORD_MESSAGE);
	}
	
	@Then("^Response for duplicate record is (.*)")
	public void verifyFailureResponse(String response){
		assertNotNull(response);
		assertTrue(response.contains(Constants.DUPLICATE_RECORD_MESSAGE));
	}
	
	@Given("^Second input data and base url are valid")
	public void newJsonldData(){
		setJsonld(VALID_JSONLD);
		assertNotNull(jsonld);
		assertNotNull(restTemplate);
		assertNotNull(baseUrl);
	}
	
	@When("^Inserting second valid record into the registry")
	public void addNewEntity(){
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String label = generateRandomId();
		setJsonldWithNewRootLabel(CONTEXT_CONSTANT + label);
		HttpEntity entity = new HttpEntity(jsonld,headers);
		ResponseEntity response = restTemplate.postForEntity(baseUrl+ADD_ENTITY,
				entity,ObjectNode.class);
		ObjectNode obj = (ObjectNode)response.getBody();
		assertNotNull(obj);
		assertEquals(obj.get(JsonKeys.RESPONSE).asText(), JsonKeys.SUCCESS);
	}
	
	@Then("^Response for second valid record is (.*)")
	public void verifyResponse2(String response){
		assertNotNull(response);
		assertTrue(response.contains(JsonKeys.SUCCESS));
	}
	
	@Given("^Base url is valid but input data has invalid type")
	public void invalidJsonldData(){
		setJsonld(INVALID_LABEL_JSONLD);
		assertNotNull(jsonld);
		assertNotNull(restTemplate);
		assertNotNull(baseUrl);
	}
	
	@When("^Inserting record with invalid type into the registry")
	public void addInvalidEntity(){
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		/*String label = generateRandomId();
		setJsonldWithNewRootLabel(label);*/
		HttpEntity entity = new HttpEntity(jsonld,headers);
		ResponseEntity response = restTemplate.postForEntity(baseUrl+ADD_ENTITY,
				entity,ObjectNode.class);
		ObjectNode obj = (ObjectNode)response.getBody();
		assertNotNull(obj);
		assertEquals(obj.get(JsonKeys.RESPONSE).asText(), Constants.INVALID_TYPE_MESSAGE);
	}
	
	@Then("^Response for invalid record is (.*)")
	public void verifyFailureResponse2(String response){
		assertNotNull(response);
		assertTrue(response.contains(Constants.INVALID_TYPE_MESSAGE));
	}
}
