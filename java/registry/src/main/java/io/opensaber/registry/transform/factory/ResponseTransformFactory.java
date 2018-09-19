package io.opensaber.registry.transform.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;


import io.opensaber.registry.transformation.IResponseTransformer;
import io.opensaber.registry.transformation.ResponseJsonTransformer;
import io.opensaber.registry.transformation.ResponseJsonldTransformer;

@Component
public class ResponseTransformFactory {
	
	private static final String MEDIATYPE_APPLICATION_JSONLD = "application/ld+json";	
	private static final String EXCEPTION_MESSAGE = "Media type not suppoted";
	

	@Autowired
	private ResponseJsonTransformer responseJsonTransformer;
	
	@Autowired
	private ResponseJsonldTransformer responseJsonldTransformer;
	
	public IResponseTransformer<String> getInstance(MediaType type){
		IResponseTransformer<String> responseTransformer = null;

		switch(type.toString()){
					
		case MEDIATYPE_APPLICATION_JSONLD:
			responseTransformer = responseJsonldTransformer;
			break;
			
		case MediaType.APPLICATION_JSON_VALUE:
			responseTransformer = responseJsonTransformer;
			break;
		
		default:
			responseTransformer = responseJsonldTransformer;
			//throw new NotSupportedTypeException(EXCEPTION_MESSAGE);
		
		}		
		return responseTransformer;
	}

}
