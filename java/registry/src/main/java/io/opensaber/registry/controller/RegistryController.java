package io.opensaber.registry.controller;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.opensaber.registry.schema.configurator.ISchemaConfigurator;
import io.opensaber.registry.service.EncryptionService;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.util.TPGraphMain;
import org.apache.jena.rdf.model.Model;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.opensaber.pojos.*;
import io.opensaber.registry.exception.*;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.Constants.Direction;
import io.opensaber.registry.middleware.util.Constants.JsonldConstants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.service.RegistryAuditService;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.service.SearchService;
import io.opensaber.registry.transform.*;

@RestController
public class RegistryController {

	private static Logger logger = LoggerFactory.getLogger(RegistryController.class);
	@Autowired
	Transformer transformer;
	@Autowired
	private ConfigurationHelper configurationHelper;
	@Autowired
	private RegistryService registryService;
	@Autowired
	private RegistryAuditService registryAuditService;
	@Autowired
	private SearchService searchService;
	@Value("${registry.context.base}")
	private String registryContext;
	@Autowired
	private APIMessage apiMessage;
	@Autowired
	private DatabaseProvider databaseProvider;
	@Autowired
	private ISchemaConfigurator schemaConfigurator;
	@Autowired
	private EncryptionService encryptionService;
	private Gson gson = new Gson();
	private Type mapType = new TypeToken<Map<String, Object>>() {
	}.getType();
	@Value("${audit.enabled}")
	private boolean auditEnabled;
	@Autowired
	private OpenSaberInstrumentation watch;
	private List<String> keyToPurge = new java.util.ArrayList<>();
    @Qualifier("parentVertex")
    @Autowired
    private Vertex parentVertex;

	@RequestMapping(value = "/add2", method = RequestMethod.POST)
	public ResponseEntity<Response> add(@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "prop", required = false) String property) {

		Model rdf = (Model) apiMessage.getLocalMap(Constants.CONTROLLER_INPUT);
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.CREATE, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();

		try {
			watch.start("RegistryController.addToExistingEntity");
			String dataObject = apiMessage.getLocalMap(Constants.LD_OBJECT).toString();
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
	 * Note: Only one mime type is supported at a time. Picks up the first mime
	 * type from the header.
	 * 
	 * @return
	 */
	@RequestMapping(value = "/read2", method = RequestMethod.POST)
	public ResponseEntity<Response> readEntity(@RequestHeader HttpHeaders header) {

		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.READ, "OK", responseParams);
		String dataObject = apiMessage.getRequest().getRequestMapAsString();
		JSONParser parser = new JSONParser();
		try {
			JSONObject json = (JSONObject) parser.parse(dataObject);
			String entityId = registryContext + json.get("id").toString();
			boolean includeSign = Boolean.parseBoolean(json.getOrDefault("includeSignatures", false).toString());

			watch.start("RegistryController.readEntity");
			String content = registryService.getEntityFramedById(entityId, includeSign);
			logger.info("RegistryController: Framed content " + content);

			Configuration config = configurationHelper.getConfiguration(header.getAccept().iterator().next().toString(),
					Direction.OUT);
			Data<Object> data = new Data<Object>(content);
			ITransformer<Object> responseTransformer = transformer.getInstance(config);
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
	 * Note: Only one mime type is supported at a time. Pick up the first mime
	 * type from the header.
	 * 
	 * @return
	 */
	@RequestMapping(value = "/search", method = RequestMethod.POST)
	public ResponseEntity<Response> searchEntity(@RequestHeader HttpHeaders header) {

		Model rdf = (Model) apiMessage.getLocalMap(Constants.RDF_OBJECT);
		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.SEARCH, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();

		try {
			watch.start("RegistryController.searchEntity");
			String jenaJson = searchService.searchFramed(rdf);
			Data<Object> data = new Data<>(jenaJson);
			Configuration config = configurationHelper.getConfiguration(header.getAccept().iterator().next().toString(),
					Direction.OUT);

			ITransformer<Object> responseTransformer = transformer.getInstance(config);
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
	public ResponseEntity<Response> update() {
		Model rdf = (Model) apiMessage.getLocalMap(Constants.RDF_OBJECT);
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


	@RequestMapping(value = "/add", method = RequestMethod.POST)
	public ResponseEntity<Response> addTP2Graph(@RequestParam(value = "id", required = false) String id,
										@RequestParam(value = "prop", required = false) String property) {

		ResponseParams responseParams = new ResponseParams();
		Response response = new Response(Response.API_ID.CREATE, "OK", responseParams);
		Map<String, Object> result = new HashMap<>();
		String jsonString = apiMessage.getRequest().getRequestMapAsString();
		List<String> privateProperties = schemaConfigurator.getAllPrivateProperties();
		//String jsonString = "{\"Teacher\":{\"signatures\":{\"@type\":\"sc:GraphSignature2012\",\"signatureFor\":\"http://localhost:8080/serialNum\",\"creator\":\"https://example.com/i/pat/keys/5\",\"created\":\"2017-09-23T20:21:34Z\",\"nonce\":\"2bbgh3dgjg2302d-d2b3gi423d42\",\"signatureValue\":\"eyiOiJKJ0eXA...OEjgFWFXk\"},\"serialNum\":6,\"teacherCode\":\"12234\",\"nationalIdentifier\":\"1234567890123456\",\"teacherName\":\"FromRajeshLaptop\",\"gender\":\"GenderTypeCode-MALE\",\"birthDate\":\"1990-12-06\",\"socialCategory\":\"SocialCategoryTypeCode-GENERAL\",\"highestAcademicQualification\":\"AcademicQualificationTypeCode-PHD\",\"highestTeacherQualification\":\"TeacherQualificationTypeCode-MED\",\"yearOfJoiningService\":\"2014\",\"teachingRole\":{\"@type\":\"TeachingRole\",\"teacherType\":\"TeacherTypeCode-HEAD\",\"appointmentType\":\"TeacherAppointmentTypeCode-REGULAR\",\"classesTaught\":\"ClassTypeCode-SECONDARYANDHIGHERSECONDARY\",\"appointedForSubjects\":\"SubjectCode-ENGLISH\",\"mainSubjectsTaught\":\"SubjectCode-SOCIALSTUDIES\",\"appointmentYear\":\"2015\"},\"inServiceTeacherTrainingFromBRC\":{\"@type\":\"InServiceTeacherTrainingFromBlockResourceCentre\",\"daysOfInServiceTeacherTraining\":\"10\"},\"inServiceTeacherTrainingFromCRC\":{\"@type\":\"InServiceTeacherTrainingFromClusterResourceCentre\",\"daysOfInServiceTeacherTraining\":\"2\"},\"inServiceTeacherTrainingFromDIET\":{\"@type\":\"InServiceTeacherTrainingFromDIET\",\"daysOfInServiceTeacherTraining\":\"5.5\"},\"inServiceTeacherTrainingFromOthers\":{\"@type\":\"InServiceTeacherTrainingFromOthers\",\"daysOfInServiceTeacherTraining\":\"3.5\"},\"nonTeachingAssignmentsForAcademicCalendar\":{\"@type\":\"NonTeachingAssignmentsForAcademicCalendar\",\"daysOfNonTeachingAssignments\":\"6\"},\"basicProficiencyLevel\":{\"@type\":\"BasicProficiencyLevel\",\"proficiencySubject\":\"SubjectCode-MATH\",\"proficiencyAcademicQualification\":\"AcademicQualificationTypeCode-PHD\"},\"disabilityType\":\"DisabilityCode-NA\",\"trainedForChildrenSpecialNeeds\":\"YesNoCode-YES\",\"trainedinUseOfComputer\":\"YesNoCode-YES\"}}}";
		TPGraphMain tpGraph = new TPGraphMain(databaseProvider, parentVertex, privateProperties, encryptionService);

		try {
			watch.start("RegistryController.addToExistingEntity");
			JsonNode rootNode = tpGraph.createEncryptedJson(jsonString);
			tpGraph.createTPGraph(rootNode);
			result.put("entity", "");
			response.setResult(result);
			responseParams.setStatus(Response.Status.SUCCESSFUL);
			watch.stop("RegistryController.addToExistingEntity");
			logger.debug("RegistryController : Entity with label {} added !", "");
		} catch (Exception e) {
			logger.error("Exception in controller while adding entity !", e);
			response.setResult(result);
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value = "/read", method = RequestMethod.POST)
	public ResponseEntity<Response> readGraph2Json(@RequestHeader HttpHeaders header) throws ParseException,
			IOException, Exception {
		String dataObject = apiMessage.getRequest().getRequestMapAsString();
		JSONParser parser = new JSONParser();
		JSONObject json = (JSONObject) parser.parse(dataObject);
		String osIdVal =  json.get("id").toString();
		ResponseParams responseParams = new ResponseParams();
		List<String> privateProperties = schemaConfigurator.getAllPrivateProperties();
		TPGraphMain tpGraph = new TPGraphMain(databaseProvider, parentVertex, privateProperties, encryptionService);
		Response response = new Response(Response.API_ID.READ, "OK", responseParams);
		response.setResult(tpGraph.readGraph2Json(osIdVal));

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
