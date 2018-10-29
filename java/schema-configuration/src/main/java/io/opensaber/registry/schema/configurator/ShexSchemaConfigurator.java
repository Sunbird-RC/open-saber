package io.opensaber.registry.schema.configurator;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.ext.com.google.common.io.ByteStreams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.middleware.util.Constants;

public class ShexSchemaConfigurator implements ISchemaConfigurator {

	private final ObjectMapper mapper = new ObjectMapper();
	private ObjectNode schemaConfigurationNode;

	// TODO: schemaFile location to be passed.
	public ShexSchemaConfigurator(String schemaFile) throws IOException {
		loadSchemaConfig(schemaFile);

	}

	@Override
	public boolean isPrivate(String propertyName) {
		return false;
	}

	@Override
	public boolean isEncrypted(String tailPropertyKey) {
		if (tailPropertyKey != null) {
			return tailPropertyKey.substring(0, Math.min(tailPropertyKey.length(), 9)).equalsIgnoreCase("encrypted");
		} else
			return false;
	}

	private void loadSchemaConfig(String schemaFile) throws IOException {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(schemaFile);
		if (is == null) {
			throw new IOException(Constants.SCHEMA_CONFIGURATION_MISSING);
		}
		String contents = new String(ByteStreams.toByteArray(is));
		schemaConfigurationNode = (ObjectNode) mapper.readTree(contents);
	}

}
