package io.opensaber.registry.response.content;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.opensaber.registry.response.content.impl.JsonResponseContent;
import io.opensaber.registry.response.content.impl.JsonldResponseContent;

@Component
public class ResponseContentFactory {
	
	private static final String APPLICATION_JSONLD = "application/ld+json";
	private static final String APPLICATION_JSON = "application/json";
	
	@Autowired
	JsonldResponseContent jsonldResponseContent;
	
	@Autowired
	JsonResponseContent jsonResponseContent;
	
	/**
	 * Factory to return implementation based on MediaType 
	 * @param type
	 * @return
	 */
	public ResponseContent getResponseContent(String type){
		
		ResponseContent responseContent = null;
		switch(type.toLowerCase()){
		
		case APPLICATION_JSONLD :
			responseContent = jsonldResponseContent;
			break;

		case APPLICATION_JSON :
			responseContent = jsonResponseContent;
			break;

		default :
			// default is kept to original
			responseContent = jsonldResponseContent;
			break;
		}

		return responseContent;

	}

}
