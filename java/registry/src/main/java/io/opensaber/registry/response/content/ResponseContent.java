package io.opensaber.registry.response.content;

import java.io.IOException;

import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;

public interface ResponseContent {
	
	public void setModel(org.eclipse.rdf4j.model.Model entityModel);// TODO: Custom model
	public ResultContent getContent() throws IOException, MultipleEntityException, EntityCreationException;

}
