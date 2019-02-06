package io.opensaber.registry.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;

import io.opensaber.registry.exception.SignatureException;
import io.opensaber.registry.service.SignatureService;

@Component
public class SignatureServiceImpl implements SignatureService {

	private static Logger logger = LoggerFactory.getLogger(SignatureService.class);
	@Value("${signature.healthCheckURL}")
	private String healthCheckURL;
	@Value("${signature.signURL}")
	private String signURL;
	@Value("${signature.verifyURL}")
	private String verifyURL;
	@Value("${signature.keysURL}")
	private String keysURL;
	@Autowired
	private RestTemplate restTemplate;

	/** This method checks signature service is available or not
	 * @return - true or false
	 * @throws SignatureException.UnreachableException
	 */
	@Override
	@Retryable(value ={SignatureException.UnreachableException.class }, maxAttemptsExpression = "#{${signature.retry.maxAttempts}}",
			backoff = @Backoff(delayExpression = "#{${signature.retry.backoff.delay}}"))
	public boolean isServiceUp() throws SignatureException.UnreachableException {
		boolean isSignServiceUp = false;
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(healthCheckURL, String.class);
			if (response.getBody().equalsIgnoreCase("UP")) {
				isSignServiceUp = true;
				logger.debug("Signature service running !");
			}
		} catch (RestClientException ex) {
			logger.error("RestClientException when checking the health of the Sunbird encryption service: ", ex);
			throw new SignatureException().new UnreachableException(ex.getMessage());
		}
		return isSignServiceUp;
	}

	/** This method calls signature service for signing the object
	 * @param propertyValue - contains input need to be signed
	 * @return - signed data with key
	 * @throws SignatureException.UnreachableException
	 * @throws SignatureException.CreationException
	 */
	@Override
	@Retryable(value ={SignatureException.UnreachableException.class }, maxAttemptsExpression = "#{${signature.retry.maxAttempts}}",
			backoff = @Backoff(delayExpression = "#{${signature.retry.backoff.delay}}"))
	public Object sign(Object propertyValue)
			throws SignatureException.UnreachableException, SignatureException.CreationException {
		logger.debug("sign method starts with value {}",propertyValue);
		ResponseEntity<String> response = null;
		Object result = null;
		try {
			response = restTemplate.postForEntity(signURL, propertyValue, String.class);
			result = new Gson().fromJson(response.getBody(), Object.class);
		} catch (RestClientException ex) {
			logger.error("RestClientException when signing: ", ex);
			throw new SignatureException().new UnreachableException(ex.getMessage());
		} catch (Exception e) {
			logger.error("RestClientException when signing: ", e);
			throw new SignatureException().new CreationException(e.getMessage());
		}
		logger.debug("sign method ends with value {}",result);
		return result;
	}

	/** This method verifies the sign value with request input object
	 * @param propertyValue - contains input along with signed value
	 * @return true/false
	 * @throws SignatureException.UnreachableException
	 * @throws SignatureException.VerificationException
	 */
	@Override
	@Retryable(value ={ SignatureException.UnreachableException.class }, maxAttemptsExpression = "#{${signature.retry.maxAttempts}}",
			backoff = @Backoff(delayExpression = "#{${signature.retry.backoff.delay}}"))
	public Object verify(Object propertyValue)
			throws SignatureException.UnreachableException, SignatureException.VerificationException {
		logger.debug("verify method starts with value {}",propertyValue);
		ResponseEntity<String> response = null;
		Object result = null;
		try {
			response = restTemplate.postForEntity(verifyURL, propertyValue, String.class);
			result = new Gson().fromJson(response.getBody(), Object.class);
		} catch (RestClientException ex) {
			logger.error("RestClientException when verifying: ", ex);
			throw new SignatureException().new UnreachableException(ex.getMessage());
		} catch (Exception e) {
			logger.error("RestClientException when verifying: ", e);
			throw new SignatureException().new VerificationException(e.getMessage());
		}
		logger.debug("verify method ends with value {}",result);
		return result;
	}

	/** This medhod gives public key based on keyId
	 * @param keyId
	 * @return public key
	 * @throws SignatureException.UnreachableException
	 * @throws SignatureException.KeyNotFoundException
	 */
	@Override
	@Retryable(value ={ SignatureException.UnreachableException.class }, maxAttemptsExpression = "#{${signature.retry.maxAttempts}}",
			backoff = @Backoff(delayExpression = "#{${signature.retry.backoff.delay}}"))
	public String getKey(String keyId)
			throws SignatureException.UnreachableException, SignatureException.KeyNotFoundException {
		logger.debug("getKey method starts with value {}",keyId);
		ResponseEntity<String> response = null;
		String result = null;
		try {
			response = restTemplate.getForEntity(keysURL + "/" + keyId, String.class);
			result = response.getBody();
		} catch (RestClientException ex) {
			logger.error("RestClientException when verifying: ", ex);
			throw new SignatureException().new UnreachableException(ex.getMessage());
		} catch (Exception e) {
			logger.error("RestClientException when verifying: ", e);
			throw new SignatureException().new KeyNotFoundException(e.getMessage());
		}
		logger.debug("getKey method ends with value {}",result);
		return result;
	}
}
