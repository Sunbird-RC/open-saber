package io.opensaber.registry.interceptor.request.transform;



import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.transform.commons.TransformationException;


public class RequestJsonldTransformer implements IRequestTransformer<String> {

	@Override
	public ResponseData<String> transform(Data<String> data) throws TransformationException {
		return new ResponseData<>(data.getData());
	}

}
