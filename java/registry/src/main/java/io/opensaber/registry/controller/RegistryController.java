package io.opensaber.registry.controller;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.opensaber.pojos.*;
import io.opensaber.registry.exception.*;
import io.opensaber.registry.middleware.transform.Data;
import io.opensaber.registry.middleware.transform.ITransformer;
import io.opensaber.registry.middleware.transform.TransformationException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.Constants.JsonldConstants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.service.RegistryAuditService;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.service.SearchService;
import io.opensaber.registry.transformation.ResponseTransformFactory;

@RestController
public class RegistryController {

	private static final String ID_REGEX = "\"@id\"\\s*:\\s*\"_:[a-z][0-9]+\",";
	private static Logger logger = LoggerFactory.getLogger(RegistryController.class);
	@Autowired
	ResponseTransformFactory responseTransformFactory;
	@Autowired
	private RegistryService registryService;
	@Autowired
	private RegistryAuditService registryAuditService;
	@Autowired
	private SearchService searchService;
	@Value("${registry.context.base}")
	private String registryContext;
	private Gson gson = new Gson();
	private Type mapType = new TypeToken<Map<String, Object>>() {
	}.getType();
	@Value("${audit.enabled}")
	private boolean auditEnabled;
	@Value("${signature.domain}")
	private String signatureDomain;
	@Value("${frame.file}")
	private String frameFile;
	@Autowired
	private OpenSaberInstrumentation watch;
	private List<String> keyToPurge = new java.util.ArrayList<>();

	@RequestMapping(value = "/add", method = RequestMethod.POST)
	public ResponseEntity<Response> add(@RequestAttribute Request requestModel,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "prop", required = false) String property) {

		Model rdf = (Model) requestModel.getRequestMap().get("rdf");
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.CREATE, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();

		try {
			watch.start("RegistryController.addToExistingEntity");
			String dataObject = requestModel.getRequestMap().get("dataObject").toString();
			String label = registryService.addEntity(rdf, dataObject, id, property);
			result.put("entity", label);
			response.setResult(result);
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.addToExistingEntity");
			logger.debug("RegistryController : Entity with label {} added !", label);
		} catch (DuplicateRecordException | EntityCreationException e) {
			logger.error("DuplicateRecordException|EntityCreationException in controller while adding entity !", e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			logger.error("Exception in controller while adding entity !", e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	/**
	 * 
	 * @param id
	 * @param accept,
	 *            only one mime type is supported at a time. Pick up the first mime
	 *            type from the header.
	 * @return
	 */
	@RequestMapping(value = "/read", method = RequestMethod.POST)
	public ResponseEntity<Response> readEntity(@RequestAttribute Request requestModel,
			@RequestHeader HttpHeaders header) {

		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.READ, "OK", responseParams);

		String dataObject = (String) requestModel.getRequestMap().get(Constants.REQUEST_ATTRIBUTE_NAME);
		JSONParser parser = new JSONParser();
		try {
			JSONObject json = (JSONObject) parser.parse(dataObject);
			String entityId = registryContext + json.get("id").toString();
			boolean includeSign = Boolean.parseBoolean(json.get("includeSignatures").toString());

			watch.start("RegistryController.readEntity");
			String content = registryService.getEntityFramedById(entityId, includeSign);
			logger.info("RegistryController: Json string " + content);

			Data<Object> data = new Data<Object>(content);
			ITransformer<Object> responseTransformer = responseTransformFactory
					.getInstance(header.getAccept().iterator().next());
			responseTransformer.setPurgeData(getKeysToPurge());
			Data<Object> responseContent = responseTransformer.transform(data);
			response.setResult(responseContent.getData());
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.readEntity");
			logger.debug("RegistryController: entity for {} read !", entityId);
		} catch (ParseException | RecordNotFoundException | UnsupportedOperationException | TransformationException e) {

			logger.error("RegistryController: Exception while reading entity !", e);
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

	/**
	 * 
	 * @param id
	 * @param accept,
	 *            only one mime type is supported at a time. Pick up the first mime
	 *            type from the header.
	 * @return
	 */
	@RequestMapping(value = "/search", method = RequestMethod.POST)
	public ResponseEntity<Response> searchEntity(@RequestAttribute Request requestModel,
			@RequestHeader HttpHeaders header) {

		Model rdf = (Model) requestModel.getRequestMap().get("rdf");
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.SEARCH, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();

		try {
			watch.start("RegistryController.searchEntity");
			String jenaJson = searchService.searchFramed(rdf);
			Data<Object> data = new Data<>(jenaJson);
			ITransformer<Object> responseTransformer = responseTransformFactory
					.getInstance(header.getAccept().iterator().next());
			responseTransformer.setPurgeData(getKeysToPurge());
			Data<Object> resultContent = responseTransformer.transform(data);
			response.setResult(resultContent.getData());
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.searchEntity");
		} catch (AuditFailedException | RecordNotFoundException | TypeNotProvidedException
				| TransformationException e) {
			logger.error(
					"AuditFailedException | RecordNotFoundException | TypeNotProvidedException in controller while adding entity !",
					e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			logger.error("Exception in controller while searching entities !", e);
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
			logger.error(
					"RegistryController: RecordNotFoundException|EntityCreationException while updating entity (without id)!",
					e);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());

		} catch (Exception e) {
			logger.error("RegistryController: Exception while updating entity (without id)!", e);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
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
			HealthCheckResponse healthCheckResult = new HealthCheckResponse(Constants.OPENSABER_REGISTRY_API_NAME,
					false, null);
			response.setResult(JSONUtil.convertObjectJsonMap(healthCheckResult));
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Error during health check");
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@ResponseBody
	@RequestMapping(value = "/fetchAudit/{id}", method = RequestMethod.GET)
	public ResponseEntity<Response> fetchAudit(@PathVariable("id") String id) {
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.AUDIT, "OK", responseParams);

		if (auditEnabled) {
			String entityId = registryContext + id;

			try {
				watch.start("RegistryController.fetchAudit");
				org.eclipse.rdf4j.model.Model auditModel = registryAuditService.getAuditNode(entityId);
				logger.debug("Audit Record model :" + auditModel);
				String jenaJSON = registryAuditService.frameAuditEntity(auditModel);
				response.setResult(gson.fromJson(jenaJSON, mapType));
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

	@RequestMapping(value = "/delete/{id}", method = RequestMethod.DELETE)
	public ResponseEntity<Response> deleteEntity(@PathVariable("id") String id) {
		String entityId = registryContext + id;
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.DELETE, "OK", responseParams);
		try {
			registryService.deleteEntityById(entityId);
			responseParams.setErrmsg("");
			responseParams.setStatus(Response.Status.SUCCESSFUL);
		} catch (UnsupportedOperationException e) {
			logger.error("Controller: UnsupportedOperationException while deleting entity !", e);
			response.setResult(null);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (RecordNotFoundException e) {
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

	/*
	 * To set the keys(like @type to be trim of a json
	 */
	private List<String> getKeysToPurge() {
		keyToPurge.add(JsonldConstants.TYPE);
		return keyToPurge;

	}
}
