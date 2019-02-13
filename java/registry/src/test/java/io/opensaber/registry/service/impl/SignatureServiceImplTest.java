package io.opensaber.registry.service.impl;

import com.google.gson.Gson;
import io.opensaber.registry.exception.SignatureException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.service.SignatureService;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.collection.IsMapContaining;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Gson.class})
@ActiveProfiles(Constants.TEST_ENVIRONMENT)
public class SignatureServiceImplTest {

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();
	@Mock
	private RetryRestTemplate retryRestTemplate;
	@InjectMocks
	private SignatureServiceImpl signatureServiceImpl;

	@Before
	public void setUp(){
		MockitoAnnotations.initMocks(this);
	}

	/** Test case for sign api
	 * @throws Exception
	 */
	@Test
	public void test_sign_api() throws Exception {

        when(retryRestTemplate.postForEntity(nullable(String.class), any(Object.class))).thenAnswer(new Answer<ResponseEntity<String>>(){
            @Override
            public ResponseEntity<String>  answer(InvocationOnMock invocation) throws Throwable {
                String response = "success";
                return ResponseEntity.accepted().body(response);
            }
        });
        assertThat(signatureServiceImpl.sign(new Object()), is(notNullValue()));
	}

    /** Test case to throw restclient exception
     * @throws Exception
     */
    @Test
    public void test_sign_api_restclient_exception() throws Exception {
        expectedEx.expect(SignatureException.UnreachableException.class);
        when(retryRestTemplate.postForEntity(nullable(String.class), any(Object.class))).thenThrow(RestClientException.class);
        signatureServiceImpl.sign(new Object());
    }

	/** Test case for verify api with simple string as value
	 * @throws Exception
	 */
	@Test
	public void test_verify_sign_with_value_as_string() throws Exception {
        when(retryRestTemplate.postForEntity(nullable(String.class), any(Object.class))).thenAnswer(new Answer<ResponseEntity<String>>(){
            @Override
            public ResponseEntity<String>  answer(InvocationOnMock invocation) throws Throwable {
                String response = "success";
                return ResponseEntity.accepted().body(response);
            }
        });
        assertThat(signatureServiceImpl.verify(new Object()), is(notNullValue()));
	}

	/** Test case to throw restclient exception
	 * @throws Exception
	 */
	@Test
	public void test_verify_sign_with_restclient_exception() throws Exception {
        expectedEx.expect(SignatureException.UnreachableException.class);
        when(retryRestTemplate.postForEntity(nullable(String.class), any(Object.class))).thenThrow(RestClientException.class);
        signatureServiceImpl.verify(new Object());
	}

	/** Test case to get sign key for valid key-id
	 * @throws Exception
	 */
	@Test
	public void test_get_key_with_valid_keyId() throws Exception {
        when(retryRestTemplate.getForEntity(any(String.class))).thenAnswer(new Answer<ResponseEntity<String>>(){
            @Override
            public ResponseEntity<String>  answer(InvocationOnMock invocation) throws Throwable {
                String response = "success";
                return ResponseEntity.accepted().body(response);
            }
        });
        assertThat(signatureServiceImpl.getKey("2"), is(notNullValue()));
	}

	/** Test case to throw restclient exception
	 * @throws Exception
	 */
	@Test
	public void test_get_key_with_restclient_exception() throws Exception {
        expectedEx.expect(SignatureException.UnreachableException.class);
        when(retryRestTemplate.getForEntity(any(String.class))).thenThrow(RestClientException.class);
        signatureServiceImpl.getKey("100");
	}

    @Test
    public void test_encryption_isup() throws Exception {
        when(retryRestTemplate.getForEntity(nullable(String.class))).thenReturn(ResponseEntity.accepted().body("UP"));
        assertTrue(signatureServiceImpl.isServiceUp());
    }

    @Test(expected = SignatureException.UnreachableException.class)
    public void test_encryption_isup_throw_restclientexception() throws Exception {
        when(retryRestTemplate.getForEntity(nullable(String.class))).thenThrow(RestClientException.class);
        signatureServiceImpl.isServiceUp();
    }

}
