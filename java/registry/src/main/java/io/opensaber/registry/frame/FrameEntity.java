package io.opensaber.registry.frame;

import java.io.IOException;

import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;

public interface FrameEntity {

	public void setModel(org.eclipse.rdf4j.model.Model entityModel);

	public String getContent() throws IOException, MultipleEntityException, EntityCreationException;

}
