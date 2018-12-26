package io.opensaber.registry.middleware.util;

import org.apache.jena.vocabulary.RDF;

public class Constants {

	public static final String REQUEST_ATTRIBUTE_NAME = "dataObject";
	public static final String RESPONSE_ATTRIBUTE = "responseModel";
	public static final String ATTRIBUTE_NAME = "dataObject";
	public static final String REQUEST_ATTRIBUTE = "requestModel";
	public static final String REQUEST_OBJECT = "requestObject";
	public static final String RDF_OBJECT = "rdfObject";
	public static final String CONTROLLER_INPUT = "controllerInput";
	public static final String SIGN_ENTITY = "entity";
	public static final String SIGN_VALUE = "value";

	public static final String LD_OBJECT = "ldObject";
	public static final String METHOD_ORIGIN = "methodOrigin";
	public static final String TOKEN_OBJECT = "x-authenticated-user-token";
	public static final String SHEX_CREATE_PROPERTY_NAME = "validation.create.file";
	public static final String SHEX_UPDATE_PROPERTY_NAME = "validation.update.file";
	public static final String FIELD_CONFIG_SCEHEMA_FILE = "config.schema.file";
	public static final String RDF_VALIDATION_MAPPER_OBJECT = "rdfValidationMapper";
	public static final String SIGNED_PROPERTY = "signedProperties";

	public static final String DATABASE_PROVIDER = "database.provider";
	public static final String NEO4J_DIRECTORY = "database.neo4j.database_directory";
	public static final String ORIENTDB_DIRECTORY = "orientdb.directory";

	public static final String TEST_ENVIRONMENT = "test";
	public static final String NONE_STR = "none";
	public static final String INTEGRATION_TEST_BASE_URL = "http://localhost:8080/";
	public static final String TARGET_NODE_IRI = "http://www.w3.org/ns/shacl#targetNode";
	public static final String CONTEXT_KEYWORD = "@context";

	public static final String JENA_LD_FORMAT = "JSON-LD";

	public static final String JSONLD_DATA_IS_MISSING = "JSON-LD data is missing!";
	public static final String DUPLICATE_RECORD_MESSAGE = "Cannot insert duplicate record";
	public static final String NO_ENTITY_AVAILABLE_MESSAGE = "No entity available";
	public static final String ENTITY_NOT_FOUND = "Entity does not exist";
	public static final String DELETE_UNSUPPORTED_OPERATION_ON_ENTITY = "Delete operation not supported";
	public static final String READ_ON_DELETE_ENTITY_NOT_SUPPORTED = "Read on deleted entity not supported";
	public static final String TOKEN_EXTRACTION_ERROR = "Unable to extract auth token";
	public static final String JSONLD_PARSE_ERROR = "Unable to parse JSON-LD";
	public static final String RDF_VALIDATION_MAPPING_ERROR = "Unable to map validations";
	public static final String CUSTOM_EXCEPTION_ERROR = "Something went wrong!! Please try again later";
	public static final String ADD_UPDATE_MULTIPLE_ENTITIES_MESSAGE = "Cannot add/update/view more than one entity";
	public static final String AUDIT_IS_DISABLED = "Audit is disabled";
	public static final String VALIDATION_CONFIGURATION_MISSING = "Configuration for validation file is missing";
	public static final String SCHEMA_CONFIGURATION_MISSING = "Configuration for schema file is missing";
	public static final String ENTITY_TYPE_NOT_PROVIDED = "Entity type is not provided in the input";
	public static final String ENTITY_ID_MISMATCH = "Entity id is wrongly provided in the input";
	public static final String SIGN_ERROR_MESSAGE = "Unable to get signature for data";
	public static final String VERIFY_SIGN_ERROR_MESSAGE = "Unable to verify signature for data";
	public static final String KEY_RETRIEVE_ERROR_MESSAGE = "Unable to retrieve key";
	public static final String SCHEMA_TYPE_INVALID = "Invalid schema type";
	public static final String INVALID_FRAME = "Domain must be defined in frame file"; 


	public static final String OPENSABER_REGISTRY_API_NAME = "opensaber-registry-api";
	public static final String SUNBIRD_ENCRYPTION_SERVICE_NAME = "sunbird.encryption.service";
	public static final String SUNBIRD_SIGNATURE_SERVICE_NAME = "sunbird.signature.service";
	public static final String OPENSABER_DATABASE_NAME = "opensaber.database";
	public static final String GRAPH_GLOBAL_CONFIG = "graph_global_config";
	public static final String PERSISTENT_GRAPH = "persisten_graph";
	public static final String STATUS_INACTIVE = "false";
	public static final String STATUS_ACTIVE = "true";
	public static final String STATUS_KEYWORD = "@status";
	public static final String AUDIT_KEYWORD = "@audit";
	public static final String CREATE_METHOD_ORIGIN = "add";
	public static final String READ_METHOD_ORIGIN = "read";
	public static final String UPDATE_METHOD_ORIGIN = "update";
	public static final String SEARCH_METHOD_ORIGIN = "search";
	public static final String FORWARD_SLASH = "/";
	public static final String RDF_URL_SYNTAX_TYPE = RDF.uri + "type";
	public static final String TYPE_STR_JSON_LD = "@type";

	// List of predicates introduced for digital signature.
	public static final String SIGNATURES = "signatures";
	public static final String SIGNATURE_OF = "signatureOf";
	public static final String SIGNATURE_FOR = "signatureFor";
	public static final String SIGN_CREATOR = "creator";
	public static final String SIGN_CREATED_TIMESTAMP = "created";
	public static final String SIGN_NONCE = "nonce";
	public static final String SIGN_TYPE = "type";
	public static final String SIGN_SIGNATURE_VALUE = "signatureValue";

	// List of request endpoints for post calls to validate request id
	public static final String REGISTRY_ADD_ENDPOINT = "/add";
	public static final String REGISTRY_UPDATE_ENDPOINT = "/update";
	public static final String REGISTRY_READ_ENDPOINT = "/read";
	public static final String REGISTRY_SEARCH_ENDPOINT = "/search";
	public static final String SIGNATURE_SIGN_ENDPOINT = "/utils/sign";
	public static final String SIGNATURE_VERIFY_ENDPOINT = "/utils/verify";

	//Error Messages
	public static final String NODE_TYPE_WRONG_MAPPED_ERROR_MSG = "Updation failed, entity type wrongly mapped with node";

	public enum GraphDatabaseProvider {
		NEO4J("NEO4J"), ORIENTDB("ORIENTDB"), SQLG("SQLG"), CASSANDRA("CASSANDRA"), TINKERGRAPH("TINKERGRAPH");

		private String name;

		private GraphDatabaseProvider(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public enum AuditProperties {
		createdAt, lastUpdatedAt, createdBy, lastUpdatedBy
	}

	public enum GraphParams {
		properties, userId, operationType, label, requestId, nodeId, removedRelations, addedRelations, ets, createdAt, transactionData, CREATE, UPDATE, DELETE
	}

	public enum Direction {	
		IN, OUT
	}

	public static class JsonldConstants {
		public static final String CONTEXT = "@context";
		public static final String ID = "@id";
		public static final String TYPE = "@type";
		public static final String VALUE = "@value";
		public static final String GRAPH = "@graph";
	}

}
