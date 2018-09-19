package io.opensaber.registry.transformation;

import org.springframework.stereotype.Component;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

@Component
public class ResponseJsonldTransformer implements IResponseTransformer<String>{

	@Override
	public ResponseData<String> transform(Data<String> data) throws TransformationException {
		return new ResponseData<>(data.getData());
	}

}
