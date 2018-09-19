package io.opensaber.registry.frame;

import java.io.IOException;

import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;

public interface FrameEntity {
	
	public void setModel(org.eclipse.rdf4j.model.Model entityModel);// TODO: Custom model
	public String getContent() throws IOException, MultipleEntityException, EntityCreationException;

}
