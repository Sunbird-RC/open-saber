package io.opensaber.registry.interceptor.request.transform;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class RequestTransformFactory {
	
	private static final String MEDIATYPE_APPLICATION_JSONLD = "application/ld+json";
	private static final String MEDIATYPE_APPLICATION_JSON = "application/json";
	private static final String EXCEPTION_MESSAGE = "Media type not suppoted";
	

	@Autowired
	private RequestJsonTransformer resquestJsonTransformer;
	
	@Autowired
	private RequestJsonldTransformer requestJsonldTransformer;
	
	public IRequestTransformer<String> getInstance(String type){
		IRequestTransformer<String> responseTransformer = null;

		switch(type.toLowerCase()){
					
		case MEDIATYPE_APPLICATION_JSONLD:
			responseTransformer = requestJsonldTransformer;
			break;
			
		case MEDIATYPE_APPLICATION_JSON:
			responseTransformer = resquestJsonTransformer;
			break;
		
		default:
			responseTransformer = requestJsonldTransformer;
			//throw new NotSupportedTypeException(EXCEPTION_MESSAGE);
		
		}		
		return responseTransformer;
	}

}
