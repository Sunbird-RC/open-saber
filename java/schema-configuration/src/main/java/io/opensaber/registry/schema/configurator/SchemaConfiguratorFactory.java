package io.opensaber.registry.schema.configurator;

import org.springframework.beans.factory.annotation.Autowired;


public class SchemaConfiguratorFactory {

	@Autowired
	private JsonSchemaConfigurator jsonSchemaConfigurator;

	@Autowired
	private ShexSchemaConfigurator shexSchemaConfigurator;

	public ISchemaConfigurator getInstance(SchemaType type) {

		ISchemaConfigurator schemaConfigurator = null;

		if (type == SchemaType.JSON) {
			schemaConfigurator = jsonSchemaConfigurator;
		} else if (type == SchemaType.SHEX) {
			schemaConfigurator = shexSchemaConfigurator;
		}

		return schemaConfigurator;
	}

}
