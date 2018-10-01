package io.opensaber.registry.service;

import java.io.IOException;

import org.apache.jena.rdf.model.Model;

import io.opensaber.registry.exception.AuditFailedException;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.exception.TypeNotProvidedException;


public interface SearchService {
	
	public org.eclipse.rdf4j.model.Model search(Model model) throws AuditFailedException, 
	EncryptionException, RecordNotFoundException, TypeNotProvidedException;
	
	//Added method to support framing component
	public String searchFramed(Model model) throws AuditFailedException, EncryptionException, RecordNotFoundException, 
	TypeNotProvidedException, IOException, MultipleEntityException, EntityCreationException;

}
