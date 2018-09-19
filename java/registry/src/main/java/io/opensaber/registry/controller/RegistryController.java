package io.opensaber.registry.controller;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import io.opensaber.pojos.*;
import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.util.JSONUtil;

import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.opensaber.registry.exception.AuditFailedException;
import io.opensaber.registry.exception.DuplicateRecordException;
import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.exception.TypeNotProvidedException;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.service.SearchService;
import io.opensaber.registry.transform.factory.ResponseTransformFactory;
import io.opensaber.registry.transformation.IResponseTransformer;


@RestController
public class RegistryController {

	private static Logger logger = LoggerFactory.getLogger(RegistryController.class);

	@Autowired
	private RegistryService registryService;
	
	@Autowired
	private SearchService searchService;

	@Value("${registry.context.base}")
	private String registryContext;

	private Gson gson = new Gson();
	private Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
	
	@Value("${audit.enabled}")
	private boolean auditEnabled;
	
	@Autowired
	ResponseTransformFactory responseTransformFactory;

	@Autowired
	private OpenSaberInstrumentation watch;
	
	@RequestMapping(value = "/add", method = RequestMethod.POST)
	public ResponseEntity<Response> add(@RequestAttribute Request requestModel, 
			@RequestParam(value="id", required = false) String id, @RequestParam(value="prop", required = false) String property) {
		
		Model rdf = (Model) requestModel.getRequestMap().get("rdf");
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.CREATE, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();
		
		try {
			watch.start("RegistryController.addToExistingEntity");
			String label = registryService.addEntity(rdf, id, property);
			result.put("entity", label);
			response.setResult(result);
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.addToExistingEntity");
			logger.info("RegistryController : Entity with label {} added !", label);
		} catch (DuplicateRecordException | EntityCreationException e) {
			logger.error("DuplicateRecordException|EntityCreationException in controller while adding entity !",e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			logger.error("Exception in controller while adding entity !",e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value = "/read/{id}", method = RequestMethod.GET)
	public ResponseEntity<Response> readEntity(@PathVariable("id") String id,
			@RequestHeader(value="Accept") MediaType accept) {

		String entityId = registryContext + id;
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.READ, "OK", responseParams);

		logger.info("RegistryController: entity acceptType ", accept);
		
		try {
			watch.start("RegistryController.readEntity");		
			/*
			 * registryService returns the String as Jena Json.
			 * Jena Json, Passed to tranformation layer 
			 */
			String content = registryService.getEntityFramedById(entityId);
			logger.info("RegistryController: Json string", content);
			Data<String> data = new Data<String>(content);
			//transformation for content.
			IResponseTransformer<String> responseTransformer = responseTransformFactory.getInstance(accept);
			ResponseData<String> resultContent = responseTransformer.transform(data);
			
			response.setContent(resultContent.getResponseData());	
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			
			watch.stop("RegistryController.readEntity");
			//logger.info("RegistryController: entity for {} read !", response.getResult().toString());

		} catch (RecordNotFoundException e) {
			logger.error("RegistryController: RecordNotFoundException while reading entity !", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (UnsupportedOperationException e) {
			logger.error("RegistryController: UnsupportedOperationException while reading entity !", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			logger.error("RegistryController: Exception while reading entity!", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Ding! You encountered an error!");
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/search", method = RequestMethod.POST)
	public ResponseEntity<Response> searchEntity(@RequestAttribute Request requestModel, @RequestHeader(value="Accept") MediaType accept) {

		Model rdf = (Model) requestModel.getRequestMap().get("rdf");
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.SEARCH, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();
	

		try {
			watch.start("RegistryController.searchEntity");
			/*	org.eclipse.rdf4j.model.Model entityModel = searchService.search(rdf);
			logger.debug("FETCHED: " + entityModel);
			String jenaJSON = registryService.frameSearchEntity(entityModel);
			if(jenaJSON.isEmpty()){
				response.setResult(new HashMap<String,Object>());
			}else{
				response.setResult(gson.fromJson(jenaJSON, mapType));
			}*/

			String jenaJson = searchService.searchFramed(rdf);
			Data<String> data = new Data<String>(jenaJson);
			//transformation for content.
			IResponseTransformer<String> responseTransformer = responseTransformFactory.getInstance(accept);
			ResponseData<String> resultContent = responseTransformer.transform(data);
			
			response.setContent(resultContent.getResponseData());	
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.searchEntity");
		} catch (AuditFailedException | RecordNotFoundException | TypeNotProvidedException e) {
			logger.error("AuditFailedException | RecordNotFoundException | TypeNotProvidedException in controller while adding entity !",e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			logger.error("Exception in controller while searching entities !",e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@ResponseBody
	@RequestMapping(value = "/update", method = RequestMethod.POST)
	public ResponseEntity<Response> update(@RequestAttribute Request requestModel) {
		Model rdf = (Model) requestModel.getRequestMap().get("rdf");
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.UPDATE, "OK", responseParams);

		try {
			watch.start("RegistryController.update");
			registryService.updateEntity(rdf);
			responseParams.setErrmsg("");
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.update");
			logger.debug("RegistryController: entity updated !");
		} catch (RecordNotFoundException | EntityCreationException e) {
			logger.error("RegistryController: RecordNotFoundException|EntityCreationException while updating entity (without id)!", e);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());

		} catch (Exception e) {
			logger.error("RegistryController: Exception while updating entity (without id)!", e);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Error occurred when updating Entity");
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value = "/health", method = RequestMethod.GET)
	public ResponseEntity<Response> health() {

		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.HEALTH, "OK", responseParams);

		try {
			HealthCheckResponse healthCheckResult = registryService.health();
			response.setResult(JSONUtil.convertObjectJsonMap(healthCheckResult));
			responseParams.setErrmsg("");
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			logger.debug("Application heath checked : ", healthCheckResult.toString());
		} catch (Exception e) {
			logger.error("Error in health checking!", e);
            HealthCheckResponse healthCheckResult =
                    new HealthCheckResponse(Constants.OPENSABER_REGISTRY_API_NAME, false, null);
            response.setResult(JSONUtil.convertObjectJsonMap(healthCheckResult));
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Error during health check");
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@ResponseBody
	@RequestMapping(value = "/fetchAudit/{id}", method = RequestMethod.GET)
	public ResponseEntity<Response> fetchAudit(@PathVariable("id") String id, @RequestHeader(value="Accept") MediaType accept) {
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.AUDIT, "OK", responseParams);
		
		if (auditEnabled) {
			String entityId = registryContext + id;

			try {
				watch.start("RegistryController.fetchAudit");
				//org.eclipse.rdf4j.model.Model auditModel = registryService.getAuditNode(entityId);
				//logger.debug("Audit Record model :" + auditModel);
			
				String jenaJson = registryService.getAuditNodeFramed(entityId);
				Data<String> data = new Data<String>(jenaJson);
				//transformation for content.
				IResponseTransformer<String> responseTransformer = responseTransformFactory.getInstance(accept);
				ResponseData<String> resultContent = responseTransformer.transform(data);
				
				response.setContent(resultContent.getResponseData());
				responseParams.setStatus(Response.Status.SUCCESSFUL);
				watch.stop("RegistryController.fetchAudit");
				logger.debug("Controller: audit records fetched !");
			} catch (RecordNotFoundException e) {
				logger.error("Controller: RecordNotFoundException while fetching audit !", e);
				response.setResult(null);
				responseParams.setStatus(Response.Status.UNSUCCESSFUL);
				responseParams.setErrmsg(e.getMessage());
			} catch (Exception e) {
				logger.error("Controller: Exception while fetching audit !", e);
				response.setResult(null);
				responseParams.setStatus(Response.Status.UNSUCCESSFUL);
				responseParams.setErrmsg("Meh ! You encountered an error!");
			}
		} else {
			logger.info("Controller: Audit is disabled");
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(Constants.AUDIT_IS_DISABLED);
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value="/delete/{id}", method = RequestMethod.DELETE)
	public ResponseEntity<Response> deleteEntity(@PathVariable("id") String id){
		String entityId = registryContext + id;
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.DELETE, "OK", responseParams);
		try{
			registryService.deleteEntityById(entityId);
			responseParams.setErrmsg("");
			responseParams.setStatus(Response.Status.SUCCESSFUL);
		} catch (UnsupportedOperationException e) {
			logger.error("Controller: UnsupportedOperationException while deleting entity !", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (RecordNotFoundException e){
			logger.error("Controller: RecordNotFoundException while deleting entity !", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			logger.error("Controller: Exception while deleting entity !", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Meh ! You encountered an error!");
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}
