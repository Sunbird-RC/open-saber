package io.opensaber.registry.transformation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import io.opensaber.registry.middleware.transform.ErrorCode;
import io.opensaber.registry.middleware.transform.ITransformer;
import io.opensaber.registry.middleware.transform.TransformationException;

@Component
public class TransformerFactory {

	private static final String MEDIATYPE_APPLICATION_JSONLD = "application/ld+json";
	private static final String EXCEPTION_MESSAGE = "Media type not suppoted";

	@Autowired
	private JsonTransform jsonTransform;

	@Autowired
	private LdTransform ldTransform;

	public ITransformer<Object> getInstance(MediaType type) throws TransformationException {
		ITransformer<Object> responseTransformer = null;

		switch (type.toString()) {

		case MEDIATYPE_APPLICATION_JSONLD:
			responseTransformer = ldTransform;
			break;

		case MediaType.APPLICATION_JSON_VALUE:
			responseTransformer = jsonTransform;
			break;

		case MediaType.ALL_VALUE:
			responseTransformer = jsonTransform;
			break;

		default:
			throw new TransformationException(EXCEPTION_MESSAGE, ErrorCode.UNSUPPOTERTED_TYPE);

		}
		return responseTransformer;
	}

}
