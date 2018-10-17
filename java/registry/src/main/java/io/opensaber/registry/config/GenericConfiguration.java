package io.opensaber.registry.config;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import es.weso.schema.Schema;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.pojos.Response;
import io.opensaber.registry.authorization.AuthorizationFilter;
import io.opensaber.registry.authorization.KeyCloakServiceImpl;
import io.opensaber.registry.exception.CustomException;
import io.opensaber.registry.exception.CustomExceptionHandler;
import io.opensaber.registry.frame.FrameEntity;
import io.opensaber.registry.frame.FrameEntityImpl;
import io.opensaber.registry.interceptor.*;
import io.opensaber.registry.interceptor.request.transform.JsonToLdRequestTransformer;
import io.opensaber.registry.interceptor.request.transform.JsonldToLdRequestTransformer;
import io.opensaber.registry.interceptor.request.transform.RequestTransformFactory;
import io.opensaber.registry.middleware.Middleware;
import io.opensaber.registry.middleware.impl.JSONLDConverter;
import io.opensaber.registry.middleware.impl.RDFConverter;
import io.opensaber.registry.middleware.impl.RDFValidationMapper;
import io.opensaber.registry.middleware.impl.SignaturePresenceValidator;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.model.AuditRecord;
import io.opensaber.registry.schema.config.SchemaConfigurator;
import io.opensaber.registry.service.RDFValidator;
import io.opensaber.registry.sink.*;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class GenericConfiguration implements WebMvcConfigurer {

	private static Logger logger = LoggerFactory.getLogger(GenericConfiguration.class);

	@Autowired
	private Environment environment;
	
	@Value("${encryption.service.connection.timeout}")
	private int connectionTimeout;

	@Value("${encryption.service.read.timeout}")
	private int readTimeout;
	
	@Value("${encryption.service.connection.request.timeout}")
	private int connectionRequestTimeout;

	@Value("${authentication.enabled}")
	private boolean authenticationEnabled;

	@Value("${signature.enabled}")
	private boolean signatureEnabled;

	@Value("${perf.monitoring.enabled}")
	private boolean performanceMonitoringEnabled;
	
	@Value("${registry.system.base}")
	private String registrySystemBase;
	
	@Value("${registry.context.base}")
	private String registryContextBase;
	
	@Value("${signature.schema.config.name}")
	private String signatureSchemaConfigName;
	


	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(Include.NON_NULL);
		return objectMapper;
	}

	@Bean
	public OpenSaberInstrumentation instrumentationStopWatch() {
		return new OpenSaberInstrumentation(performanceMonitoringEnabled);
	}

	@Bean
	public Gson gson(){
		return new Gson();
	}

	@Bean
	public Middleware jsonldConverter(){
		return new JSONLDConverter();
	}

	@Bean
	public Middleware rdfConverter(){
		return new RDFConverter();
	}
	
	@Bean
	public RequestTransformFactory requestTransformFactory(){
		return new RequestTransformFactory();
	}
	
	@Bean 
	public FrameEntity frameEntity(){
		return new FrameEntityImpl();
	}
	
	@Bean
	public JsonToLdRequestTransformer jsonToLdRequestTransformer(){
		return new JsonToLdRequestTransformer(frameEntity().getContent());
	}
	@Bean
	public JsonldToLdRequestTransformer jsonldToLdRequestTransformer(){
		return new JsonldToLdRequestTransformer();
	}

    @Bean
    public AuthorizationInterceptor authorizationInterceptor() {
        return new AuthorizationInterceptor(authorizationFilter(), gson());
    }

	@Bean
	public RDFConversionInterceptor rdfConversionInterceptor() {
		return new RDFConversionInterceptor(rdfConverter(), gson());
	}

	@Bean
	public RDFValidationMappingInterceptor rdfValidationMappingInterceptor() {
		return new RDFValidationMappingInterceptor(rdfValidationMapper(), gson());
	}

	@Bean
	public SignaturePresenceValidationInterceptor signaturePresenceValidationInterceptor() {
		return new SignaturePresenceValidationInterceptor(signaturePresenceValidator(), gson());
	}

	/**
	 * This methos creates bean for RequestIdValidationInterceptor
	 * @return RequestIdValidationInterceptor
	 */
	@Bean
	public RequestIdValidationInterceptor requestIdValidationInterceptor() {
		return new RequestIdValidationInterceptor(requestIdMap(), gson());
	}

	@Bean
	public Middleware authorizationFilter(){
		return new AuthorizationFilter(new KeyCloakServiceImpl());
	}
	
	@Bean
	public SchemaConfigurator schemaConfiguration() throws IOException, CustomException {
		String fieldConfigFileName = environment.getProperty(Constants.FIELD_CONFIG_SCEHEMA_FILE);
		String validationConfigFileForCreate = environment.getProperty(Constants.SHEX_CREATE_PROPERTY_NAME);
		String validationConfigFileForUpdate = environment.getProperty(Constants.SHEX_UPDATE_PROPERTY_NAME);
		if (fieldConfigFileName == null) {
			throw new CustomException(Constants.SCHEMA_CONFIGURATION_MISSING);
		}
		if (validationConfigFileForCreate == null || validationConfigFileForUpdate == null) {
			throw new CustomException(Constants.VALIDATION_CONFIGURATION_MISSING);
		}

		OpenSaberInstrumentation watch = instrumentationStopWatch();
		watch.start("SchemaConfigurator.initialization");
		SchemaConfigurator schemaConfigurator = new SchemaConfigurator(fieldConfigFileName, validationConfigFileForCreate, validationConfigFileForUpdate, registrySystemBase);
		watch.stop("SchemaConfigurator.initialization");
		return schemaConfigurator ;
	}

	@Bean
	public RDFValidator rdfValidator() {
		Schema schemaForCreate = null;
		Schema schemaForUpdate = null;
		try {
			schemaForCreate = schemaConfiguration().getSchemaForCreate();
			schemaForUpdate = schemaConfiguration().getSchemaForUpdate();
		} catch (Exception e) {
			logger.error("Unable to retrieve schema for validations");
		}
		return new RDFValidator(schemaForCreate, schemaForUpdate);
	}
	
	@Bean
	public Middleware signaturePresenceValidator() {
		Schema schemaForCreate = null;
		Model schemaConfig = null;
		try {
			schemaForCreate = schemaConfiguration().getSchemaForCreate();
			schemaConfig = schemaConfiguration().getSchemaConfig();
		} catch (Exception e) {
			logger.error("Unable to retrieve schema for signature validations");
		}
		return new SignaturePresenceValidator(schemaForCreate, registryContextBase, registrySystemBase, signatureSchemaConfigName, ((RDFValidator)rdfValidator()).getShapeTypeMap(), schemaConfig);
	}


	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public AuditRecord auditRecord() {
		return new AuditRecord();
	}

    @Bean
    public RestTemplate restTemaplteProvider() throws IOException {
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(connectionTimeout);
        requestFactory.setConnectionRequestTimeout(connectionRequestTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return new RestTemplate(requestFactory);
    }

	
	@Bean
	public DatabaseProvider databaseProvider() {
		String dbProvider = environment.getProperty(Constants.DATABASE_PROVIDER);
		DatabaseProvider provider;
		if (dbProvider.equalsIgnoreCase(Constants.GraphDatabaseProvider.ORIENTDB.getName())) {
			provider = new OrientDBGraphProvider(environment);
			provider.initializeGlobalGraphConfiguration();
		} else if (dbProvider.equalsIgnoreCase(Constants.GraphDatabaseProvider.NEO4J.getName())) {
			provider = new Neo4jGraphProvider(environment);
			provider.initializeGlobalGraphConfiguration();
		} else if (dbProvider.equalsIgnoreCase(Constants.GraphDatabaseProvider.SQLG.getName())) {
			provider = new SqlgProvider(environment);
			provider.initializeGlobalGraphConfiguration();
		} else if (dbProvider.equalsIgnoreCase(Constants.GraphDatabaseProvider.TINKERGRAPH.getName())) {
			provider = new TinkerGraphProvider(environment);
			provider.initializeGlobalGraphConfiguration();
		} else if (dbProvider.equalsIgnoreCase(Constants.GraphDatabaseProvider.CASSANDRA.getName())) {
			provider = new JanusGraphStorage(environment);
			provider.initializeGlobalGraphConfiguration();
		} else {
			throw new RuntimeException("No Database Provider is configured. Please configure a Database Provider");
		}

		return provider;
	}

	@Bean
	public UrlValidator urlValidator(){
		return new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
	}

	@Bean
	public Middleware rdfValidationMapper() {
		Model validationConfig = null;
		try{
			validationConfig = schemaConfiguration().getValidationConfig();
		}catch(Exception e){
			logger.error("Unable to get validation configuration");
		}
		return new RDFValidationMapper(validationConfig);
	}

	/**
	 * This method create a Map of request endpoints with request id
	 * @return Map
	 */
	@Bean
	public Map<String, String> requestIdMap(){
		Map<String, String> requestIdMap = new HashMap<>();
		requestIdMap.put(Constants.REGISTRY_ADD_ENDPOINT,Response.API_ID.CREATE.getId());
		requestIdMap.put(Constants.REGISTRY_SEARCH_ENDPOINT,Response.API_ID.SEARCH.getId());
		requestIdMap.put(Constants.REGISTRY_UPDATE_ENDPOINT,Response.API_ID.UPDATE.getId());
		requestIdMap.put(Constants.SIGNATURE_SIGN_ENDPOINT,Response.API_ID.SIGN.getId());
		requestIdMap.put(Constants.SIGNATURE_VERIFY_ENDPOINT,Response.API_ID.VERIFY.getId());
		return requestIdMap;
	}

	/**
	 * This method will process all the interceptors for each request
	 * @param registry
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		int orderIdx = 1;
		Map<String, String> requestMap =  requestIdMap();
		registry.addInterceptor(requestIdValidationInterceptor()).
				addPathPatterns(new ArrayList(requestMap.keySet())).order(orderIdx++);
		if(authenticationEnabled) {
            registry.addInterceptor(authorizationInterceptor())
                    .addPathPatterns("/**").excludePathPatterns("/health", "/error").order(orderIdx++);
	    }

	    registry.addInterceptor(rdfConversionInterceptor())
				.addPathPatterns("/add", "/update", "/search").order(orderIdx++);
		/*registry.addInterceptor(rdfValidationInterceptor())
				.addPathPatterns("/add", "/update").order(orderIdx++);*/

		if (signatureEnabled) {
			registry.addInterceptor(signaturePresenceValidationInterceptor())
					.addPathPatterns("/add", "/update").order(orderIdx++);
		}
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		try {
			registry.addResourceHandler("/resources/**")
					.addResourceLocations("classpath:vocab/1.0/")
					.setCachePeriod(3600)
					.resourceChain(true)
					.addResolver(new PathResourceResolver());
		} catch (Exception e) {
			throw e;
		}

	}

	@Bean
    public HandlerExceptionResolver customExceptionHandler () {
        return new CustomExceptionHandler(gson());
    }
}
