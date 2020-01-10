package io.opensaber.registry.util;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.opensaber.registry.middleware.util.Constants;

@Component("definitionsManager")
public class DefinitionsManager {
	private static Logger logger = LoggerFactory.getLogger(DefinitionsManager.class);

	private Map<String, Definition> definitionMap = new HashMap<>();

	private OSResourceLoader osResourceLoader;

	@Autowired
	private ResourceLoader resourceLoader;

	@Value("${audit.frame.store}")
	private String auditStore;

	@Value("${audit.enabled}")
	private boolean auditEnabled;

	@Value("${audit.frame.file}")
	private String auditframeFile;
	
	/**
	 * Loads the definitions from the _schemas folder
	 */
	@PostConstruct
	public void loadDefinition() throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		osResourceLoader = new OSResourceLoader(resourceLoader);
		osResourceLoader.loadResource(Constants.RESOURCE_LOCATION);

		if (auditEnabled && Constants.DATABSE_STORE.equals(auditStore)) {
			Map<String, String> data = loadAuditSchema();
			osResourceLoader.getNameContent().putAll(data);
		}

		for (Entry<String, String> entry : osResourceLoader.getNameContent().entrySet()) {
			JsonNode jsonNode = mapper.readTree(entry.getValue());
			Definition definition = new Definition(jsonNode);
			logger.info("loading resource:" + entry.getKey() + " with private field size:"
					+ definition.getOsSchemaConfiguration().getPrivateFields().size() + " & signed fields size:"
					+ definition.getOsSchemaConfiguration().getSignedFields().size());
			definitionMap.putIfAbsent(definition.getTitle(), definition);
		}
		logger.info("loaded schema resource(s): " + definitionMap.size());
	}

	private Map<String, String> loadAuditSchema() throws IOException, FileNotFoundException {
		Stream<Path> walk = Files.walk(Paths.get(Constants.AUDIT_SCHEMA_RESOURCE_LOCATION));
		List<String> result = walk.map(x -> x.toString()).filter(f -> f.endsWith(".json")).collect(Collectors.toList());
		Map<String, String> data = new HashMap<String, String>();
		for (String r : result) {
			JsonParser parser = new JsonParser();
			Object obj = parser.parse(new FileReader(r));
			JsonObject jsonObj = (JsonObject) obj;
			data.put(r.split("/")[1], jsonObj.toString());
		}
		return data;
	}

	/**
	 * Returns the title for all definitions loaded
	 * 
	 * @return
	 */
	public Set<String> getAllKnownDefinitions() {
		return definitionMap.keySet();
	}

	/**
	 * Returns all definitions that are loaded
	 * 
	 * @return
	 */
	public List<Definition> getAllDefinitions() {
		List<Definition> definitions = new ArrayList<>();
		for (Entry<String, Definition> entry : definitionMap.entrySet()) {
			definitions.add(entry.getValue());
		}
		return definitions;
	}

	/**
	 * Provide a definition by given title which is already loaded
	 * 
	 * @param title
	 * @return
	 */
	public Definition getDefinition(String title) {
		return definitionMap.getOrDefault(title, null);
	}

}
