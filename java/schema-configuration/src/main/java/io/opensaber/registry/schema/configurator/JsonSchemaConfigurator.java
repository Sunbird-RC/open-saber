package io.opensaber.registry.schema.configurator;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;


public class JsonSchemaConfigurator extends ASchemaConfigurator{

	public JsonSchemaConfigurator(String schemaFile) throws IOException {
		super(schemaFile);
	}


}
