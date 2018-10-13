package io.opensaber.registry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.opensaber.pojos.*;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.model.SignRequest;
import io.opensaber.registry.model.VerifyRequest;
import io.opensaber.registry.service.SignatureService;
import io.opensaber.registry.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.Map;

@RestController
public class RegistryUtilsController {

    private static final String ID_REGEX = "\"@id\"\\s*:\\s*\"[a-z]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\",";

    private static Logger logger = LoggerFactory.getLogger(RegistryUtilsController.class);

    private Gson gson = new Gson();
    private Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private OpenSaberInstrumentation watch;

    @Value("${registry.context.base}")
    private String registryContext;

    @Value("${frame.file}")
    private String frameFile;

    

    @RequestMapping(value = "/utils/sign", method = RequestMethod.POST)
    public ResponseEntity<Response> generateSignature(@RequestAttribute Request requestModel) {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.SIGN, "OK", responseParams);
        
        try {
        	ObjectMapper mapper = new ObjectMapper();

        	logger.info("print request for sign "+requestModel.getRequestMap().
            		get(Constants.REQUEST_ATTRIBUTE_NAME).toString());
            SignRequest signRequest = mapper.readValue(requestModel.getRequestMap().
            		get(Constants.REQUEST_ATTRIBUTE_NAME).toString(), SignRequest.class);
            //String json = mapper.writeValueAsString(signRequest);
            Object signResponse = signatureService.sign(signRequest);
            response.setResult(signResponse);
            responseParams.setErrmsg("");
            responseParams.setStatus(Response.Status.SUCCESSFUL);
 
        } catch (Exception e) {
            logger.error("Error in generating signature", e);
            response.setResult(null);
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg(Constants.SIGN_ERROR_MESSAGE);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/utils/verify", method = RequestMethod.POST)
    public ResponseEntity<Response> verifySignature(@RequestAttribute Request request) {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.VERIFY, "OK", responseParams);

        try {
        	
        	ObjectMapper mapper = new ObjectMapper();
        	VerifyRequest verifyRequest = mapper.readValue(request.getRequestMap().
            		get(Constants.REQUEST_ATTRIBUTE_NAME).toString(), VerifyRequest.class);
            Object verifyResponse = signatureService.verify(verifyRequest);
            response.setResult(verifyResponse);
            responseParams.setErrmsg("");
            responseParams.setStatus(Response.Status.SUCCESSFUL);
              
        } catch (Exception e) {
            logger.error("Error in verifying signature", e);
            response.setResult(null);
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg(Constants.VERIFY_SIGN_ERROR_MESSAGE);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/utils/sign/health", method = RequestMethod.GET)
    public ResponseEntity<Response> health() {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.HEALTH, "OK", responseParams);

        try {
            boolean healthCheckResult = signatureService.isServiceUp();
            HealthCheckResponse healthCheck = new HealthCheckResponse(Constants.SUNBIRD_SIGNATURE_SERVICE_NAME, healthCheckResult, null);
            response.setResult(JSONUtil.convertObjectJsonMap(healthCheck));
            responseParams.setErrmsg("");
            responseParams.setStatus(Response.Status.SUCCESSFUL);
            logger.debug("Application heath checked : ", healthCheckResult);
        } catch (Exception e) {
            logger.error("Error in health checking!", e);
            HealthCheckResponse healthCheckResult =
                    new HealthCheckResponse(Constants.SUNBIRD_SIGNATURE_SERVICE_NAME, false, null);
            response.setResult(JSONUtil.convertObjectJsonMap(healthCheckResult));
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Error during health check");
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}
