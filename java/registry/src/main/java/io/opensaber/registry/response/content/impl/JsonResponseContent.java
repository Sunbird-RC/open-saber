package io.opensaber.registry.response.content.impl;

import java.io.IOException;

import org.eclipse.rdf4j.model.Model;
import org.springframework.stereotype.Component;

import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;
import io.opensaber.registry.response.content.ResponseContent;
import io.opensaber.registry.response.content.ResultContent;

@Component
public class JsonResponseContent implements ResponseContent {

	@Override
	public ResultContent getContent() throws IOException, MultipleEntityException, EntityCreationException{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setModel(Model entityModel) {
		// TODO Auto-generated method stub
		
	}

}
