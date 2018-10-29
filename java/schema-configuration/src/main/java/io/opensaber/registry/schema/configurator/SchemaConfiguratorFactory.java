package io.opensaber.registry.schema.configurator;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;

import io.opensaber.registry.middleware.util.Constants;

public class SchemaConfiguratorFactory {

	@Autowired
	private JsonSchemaConfigurator jsonSchemaConfigurator;

	@Autowired
	private ShexSchemaConfigurator shexSchemaConfigurator;

	public ISchemaConfigurator getInstance(SchemaType type) throws IOException {

		ISchemaConfigurator schemaConfigurator = null;

		if (type == SchemaType.JSON) {
			schemaConfigurator = jsonSchemaConfigurator;
		} else if (type == SchemaType.SHEX) {
			schemaConfigurator = shexSchemaConfigurator;
		} else {
			throw new IOException(Constants.SCHEMA_TYPE_INVALID);
		}

		return schemaConfigurator;
	}

}
