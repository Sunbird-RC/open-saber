package io.opensaber.registry.service.impl;

import java.io.IOException;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.dao.SearchDao;
import io.opensaber.registry.exception.AuditFailedException;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.exception.TypeNotProvidedException;
import io.opensaber.registry.frame.FrameEntity;
import io.opensaber.registry.service.SearchService;
import io.opensaber.utils.converters.RDF2Graph;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.SearchUtil;

@Component
public class SearchServiceImpl implements SearchService{

	@Autowired
	private SearchDao searchDao;
	
	@Autowired
	FrameEntity frameEntity;

	@Override
	public org.eclipse.rdf4j.model.Model search(Model model) throws AuditFailedException, 
	EncryptionException, RecordNotFoundException, TypeNotProvidedException{
		SearchQuery searchQuery = SearchUtil.constructSearchQuery(model);
		if(searchQuery.getType() == null || searchQuery.getTypeIRI() == null){
			throw new TypeNotProvidedException(Constants.ENTITY_TYPE_NOT_PROVIDED);
		}
		Map<String,Graph> graphMap = searchDao.search(searchQuery);
		org.eclipse.rdf4j.model.Model result = new LinkedHashModel();
		graphMap.forEach((label,graph) -> {
			org.eclipse.rdf4j.model.Model rdf4jModel = RDF2Graph.convertGraph2RDFModel(graph, label);
			rdf4jModel.forEach(aStmt -> {
				result.add(aStmt);
			});
		});
		return result;

	}

	@Override
	public String searchFramed(Model model)
			throws AuditFailedException, EncryptionException, RecordNotFoundException, TypeNotProvidedException, IOException, MultipleEntityException, EntityCreationException {
		SearchQuery searchQuery = SearchUtil.constructSearchQuery(model);
		if(searchQuery.getType() == null || searchQuery.getTypeIRI() == null){
			throw new TypeNotProvidedException(Constants.ENTITY_TYPE_NOT_PROVIDED);
		}
		Map<String,Graph> graphMap = searchDao.search(searchQuery);
		org.eclipse.rdf4j.model.Model resultModel = new LinkedHashModel();
		graphMap.forEach((label,graph) -> {
			org.eclipse.rdf4j.model.Model rdf4jModel = RDF2Graph.convertGraph2RDFModel(graph, label);
			rdf4jModel.forEach(aStmt -> {
				resultModel.add(aStmt);
			});
		});
		frameEntity.setModel(resultModel);
		return frameEntity.getContent();
	}

}
