package io.opensaber.registry.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.stereotype.Component;

@Component
public class SchemaDefinations {
	private DefinitionNodes definitionNodes;
	private Map<String, SchemaDefination> schemaDefinationMap = new HashMap<>();

	public SchemaDefinations() {
		//TODO: load files if implicit to application
	}

	public SchemaDefinations(DefinitionNodes definationNodes) {
		this.definitionNodes = definationNodes;
		definitionNodes.getDefinationNodes().forEach(definatioNode -> {
			SchemaDefination schemaDefination = new SchemaDefination(definatioNode);
			schemaDefinationMap.putIfAbsent(schemaDefination.getTitle(), schemaDefination);
		});
	}

	public List<SchemaDefination> getAllSchemaDefinations() {
		List<SchemaDefination> schemaDefinations = new ArrayList<>();
		for(Entry<String, SchemaDefination> entry : schemaDefinationMap.entrySet()){
			schemaDefinations.add(entry.getValue());
		}
		return schemaDefinations;
	}

	public SchemaDefination getSchemaDefination(String title) {
		return schemaDefinationMap.getOrDefault(title, null);
	}

}
