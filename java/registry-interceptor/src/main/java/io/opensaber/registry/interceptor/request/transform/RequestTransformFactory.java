package io.opensaber.registry.interceptor.request.transform;

import org.springframework.beans.factory.annotation.Autowired;

import io.opensaber.registry.middleware.transform.commoms.ErrorCode;
import io.opensaber.registry.middleware.transform.commoms.TransformationException;


public class RequestTransformFactory {

	private static final String MEDIATYPE_APPLICATION_JSONLD = "application/ld+json";
	private static final String MEDIATYPE_APPLICATION_JSON = "application/json";
	private static final String EXCEPTION_MESSAGE = "Media type not suppoted";

	@Autowired
	private JsonToLdRequestTransformer jsonToLdRequestTransformer;

	@Autowired
	private JsonldToLdRequestTransformer jsonldToLdRequestTransformer;

	public IRequestTransformer<Object> getInstance(String type) throws TransformationException {
		IRequestTransformer<Object> responseTransformer = null;

		switch (type.toLowerCase()) {

		case MEDIATYPE_APPLICATION_JSONLD:
			responseTransformer = jsonldToLdRequestTransformer;
			break;

		case MEDIATYPE_APPLICATION_JSON:
			responseTransformer = jsonToLdRequestTransformer;
			break;

		default:
			throw new TransformationException(EXCEPTION_MESSAGE, ErrorCode.UNSUPPOTERTED_TYPE);

		}
		return responseTransformer;
	}

}
