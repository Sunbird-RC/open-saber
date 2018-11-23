package io.opensaber.registry.interceptor.request.transform;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opensaber.registry.middleware.transform.Data;
import io.opensaber.registry.middleware.transform.ITransformer;
import io.opensaber.registry.middleware.transform.TransformationException;
import io.opensaber.registry.middleware.util.CommunicationType;

public class JsonldToLdRequestTransformer implements ITransformer<Object> {

	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException, IOException {
		JsonNode input = new ObjectMapper().readTree(data.getData().toString());
		return new Data<>(input);
	}

	@Override
	public void setPurgeData(List<String> keyToPruge) {

	}

	@Override
	public Data<Object> transform(Data<Object> data, CommunicationType communicationType)
			throws TransformationException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
