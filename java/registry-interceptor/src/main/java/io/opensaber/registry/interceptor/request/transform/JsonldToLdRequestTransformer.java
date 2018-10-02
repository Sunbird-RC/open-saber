package io.opensaber.registry.interceptor.request.transform;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opensaber.registry.middleware.transform.commoms.Data;
import io.opensaber.registry.middleware.transform.commoms.TransformationException;


public class JsonldToLdRequestTransformer implements IRequestTransformer<Object> {

	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException, IOException {
		JsonNode input = new ObjectMapper().readTree(data.getData().toString());
		return new Data<>(input);
	}

}
