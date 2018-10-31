package io.opensaber.registry.schema.configurator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.ext.com.google.common.io.ByteStreams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.Constants.JsonldConstants;
import io.opensaber.registry.middleware.util.JSONUtil;

public class ShexSchemaConfigurator extends ASchemaConfigurator {

	public ShexSchemaConfigurator(String schemaFile) throws IOException {
		super(schemaFile);
	}

}
