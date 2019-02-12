package io.opensaber.registry.service.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.opensaber.registry.exception.SignatureException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.service.SignatureService;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.collection.IsMapContaining;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { SignatureServiceImpl.class, Environment.class, Gson.class, RetryRestTemplate.class,
		SignatureServiceImplTest.ContextConfiguration.class})
@ActiveProfiles(Constants.TEST_ENVIRONMENT)
public class SignatureServiceImplTest {



	Type type = new TypeToken<Map<String, String>>() {
	}.getType();
	@Rule
	public ExpectedException expectedEx = ExpectedException.none();
	@Autowired
	SignatureService signatureService;

	@TestConfiguration
	static class ContextConfiguration {
		@Value("${service.connection.timeout}")
		private int connectionTimeout;
		@Value("${service.connection.request.timeout}")
		private int connectionRequestTimeout;
		@Value("${service.read.timeout}")
		private int readTimeout;
		@Bean
		public RestTemplate createRestTemplate() {
			HttpClient httpClient = HttpClientBuilder.create().build();
			HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
			requestFactory.setConnectTimeout(connectionTimeout);
			requestFactory.setConnectionRequestTimeout(connectionRequestTimeout);
			requestFactory.setReadTimeout(readTimeout);
			return new RestTemplate(requestFactory);
		}
	}

	@Test
	public void test_sign_with_value_as_string() throws Exception {

		Map inputMap = createSimpleValue();
		Map<String, Object> resMap = (Map<String, Object>)signatureService.sign(inputMap);

		assertThat(resMap.size(),is(3));
		assertThat(resMap, IsMapContaining.hasKey("signatureValue"));
		assertThat(resMap, IsMapContaining.hasKey("keyId"));
	}

	@Test
	public void test_sign_with_entity_as_map() throws Exception {
		Map<String, Object> inputSignMap = new HashMap<>();
		inputSignMap = createSimpleEntity();
		Map<String, Object> resMap = (Map<String, Object>)signatureService.sign(inputSignMap);

		assertThat(resMap.size(),is(3));
		assertThat(resMap, IsMapContaining.hasKey("signatureValue"));
		assertThat(resMap, IsMapContaining.hasKey("keyId"));
	}

	@Test
	public void test_verify_sign_with_value_as_string() throws Exception {

		Map<String, Object> signInput =  createSimpleValue();
		Map<String, Object> resMap = (Map<String, Object>)signatureService.sign(signInput);
		Map<String, Object> verifyMap = createVerifyMap(resMap,signInput);
		Map<String, Object> verifyInput = new HashMap();
		verifyInput.put("entity",verifyMap);
		assertTrue((boolean) signatureService.verify(verifyInput));
	}

	@Test
	public void test_verify_sign_with_entity_as_map() throws Exception {

		Map<String, Object> signInput =  createSimpleEntity();
		Map<String, Object> resMap = (Map<String, Object>)signatureService.sign(signInput);
		Map<String, Object> verifyMap = createVerifyMap(resMap,signInput);
		Map<String, Object> verifyInput = new HashMap();
		verifyInput.put("entity",verifyMap);
		assertTrue((boolean) signatureService.verify(verifyInput));
	}

	@Test
	public void test_verify_sign_with_different_claim_data() throws Exception {

		Map<String, Object> signInput =  createSimpleValue();
		Map<String, Object> resMap = (Map<String, Object>)signatureService.sign(signInput);
		Map<String, Object> verifyMap = createVerifyMap(resMap,signInput);
		verifyMap.put("claim","aaaaaaaaaa");
		Map<String, Object> verifyInput = new HashMap();
		verifyInput.put("entity",verifyMap);
		assertFalse((boolean) signatureService.verify(verifyInput));
	}

	@Test
	public void test_sign_with_value_as_array() throws Exception {

		byte[] array = new byte[7];
		new Random().nextBytes(array);
		//String generatedString = new String(array, Charset.forName("UTF-8"));
		String[] strArray = {"String1","Sting2"};
		Map<String, Object> map = new HashMap<>();
		map.put("value", strArray);
		List arrayObj = (List) signatureService.sign(map);
		assertEquals(arrayObj.size(),strArray.length);
	}

	@Test
	public void test_sign_with_empty_value() throws Exception {
		expectedEx.expect(SignatureException.UnreachableException.class);
		Map<String,Object> map = new HashMap<>();
		map.put("value", "");
		String response = signatureService.sign(map).toString();
	}

	private Map<String,Object> createVerifyMap(Map<String,Object> resMap, Map<String,Object> signInput) {
		Map<String, Object> verifyInput =  new HashMap();
		Map<String, Object> claimMap = new HashMap<>();
		if(signInput.containsKey("value")){
			claimMap.put("claim",signInput.get("value"));
		} else {
			claimMap.put("claim",signInput.get("entity"));
		}
		verifyInput.putAll(claimMap);
		verifyInput.put("signatureValue",resMap.get("signatureValue"));
		verifyInput.put("keyId",resMap.get("keyId"));
		return verifyInput;
	}

	public Map createSimpleValue(){
		byte[] array = new byte[7];
		new Random().nextBytes(array);
		String generatedString = new String(array, Charset.forName("UTF-8"));
		Map<String, Object> map = new HashMap<>();
		map.put("value", generatedString);
		return map;
	}

	public Map createSimpleEntity(){
		Map<String, Object> map = new HashMap<>();
		Map<String, Object> inputSignMap = new HashMap<>();
		map.put("a", 1);
		map.put("b", "sampleString");
		inputSignMap.put("entity",map);
		return inputSignMap;
	}

}
