package io.opensaber.validators.json.jsonschema;

import java.io.IOException;
import java.io.InputStream;

import io.opensaber.pojos.APIMessage;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import io.opensaber.pojos.ValidationResponse;
import io.opensaber.registry.middleware.MiddlewareHaltException;
import io.opensaber.validators.IValidate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.context.support.ServletContextResource;

import javax.servlet.ServletContext;

public class JsonValidationServiceImpl implements IValidate {

	private Schema getEntitySchema(ServletContext serverContext) throws MiddlewareHaltException{
		// TODO: This can move to be a member when we want to validate.
		// Stubbing for now.
		Schema schema;
		try {
			InputStream schemaStream = new ServletContextResource(serverContext, "public/_schemas/Teacher.json").getInputStream();
			//JsonValidationServiceImpl.class.getResourceAsStream("public/_schemas/Teacher.json");
			JSONObject rawSchema = new JSONObject(new JSONTokener(schemaStream));
			SchemaLoader schemaLoader = SchemaLoader.builder().schemaJson(rawSchema).draftV7Support()
					.resolutionScope("http://localhost:8080/_schemas/").build();
			schema = schemaLoader.load().build();
		} catch (IOException ioe) {
			throw new MiddlewareHaltException("can't validate");
		}
		return schema;
	}

	@Override
	public boolean validate(APIMessage apiMessage) throws MiddlewareHaltException {
	    Schema schema = getEntitySchema(apiMessage.getRequestWrapper().getServletContext());
		String inputStr = apiMessage.getRequest().getRequestMapAsString();
		schema.validate(inputStr);
		return true;

	}
}
