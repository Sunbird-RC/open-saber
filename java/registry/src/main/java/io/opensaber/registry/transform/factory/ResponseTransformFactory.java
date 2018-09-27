package io.opensaber.registry.transform.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;


import io.opensaber.registry.transformation.IResponseTransformer;
import io.opensaber.registry.transformation.JsonToLdTransformer;
import io.opensaber.registry.transformation.JsonldToLdTransformer;

@Component
public class ResponseTransformFactory {
	
	private static final String MEDIATYPE_APPLICATION_JSONLD = "application/ld+json";	
	private static final String EXCEPTION_MESSAGE = "Media type not suppoted";
	

	@Autowired
	private JsonToLdTransformer jsonTransformer;
	
	@Autowired
	private JsonldToLdTransformer jsonldTransformer;
	
	public IResponseTransformer<Object> getInstance(MediaType type){
		IResponseTransformer<Object> responseTransformer = null;

		switch(type.toString()){
					
		case MEDIATYPE_APPLICATION_JSONLD:
			responseTransformer = jsonldTransformer;
			break;
			
		case MediaType.APPLICATION_JSON_VALUE:
			responseTransformer = jsonTransformer;
			break;
		
		default:
			responseTransformer = jsonldTransformer;
			//throw new NotSupportedTypeException(EXCEPTION_MESSAGE);
		
		}		
		return responseTransformer;
	}

}
