package io.opensaber.registry.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import io.opensaber.pojos.ComponentHealthInfo;
import io.opensaber.pojos.HealthCheckResponse;
import io.opensaber.registry.exception.*;
import io.opensaber.registry.frame.FrameEntity;
import io.opensaber.registry.sink.DatabaseProvider;
import org.apache.jena.ext.com.google.common.io.ByteStreams;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.WriterDatasetRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.opensaber.converters.JenaRDF4J;
import io.opensaber.registry.dao.RegistryDao;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.RDFUtil;
import io.opensaber.registry.service.EncryptionService;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.util.GraphDBFactory;
import io.opensaber.utils.converters.RDF2Graph;


@Component
public class RegistryServiceImpl implements RegistryService {

	private static Logger logger = LoggerFactory.getLogger(RegistryServiceImpl.class);

	@Autowired
	private RegistryDao registryDao;

	@Autowired
	DatabaseProvider databaseProvider;
	
	@Autowired
	EncryptionService encryptionService;
	
	@Value("${frame.file}")
	private String frameFile;
	
	@Value("${audit.frame.file}")
	private String auditFrameFile;
	
	@Autowired
	private FrameEntity frameEntity;

	@Override
	public List getEntityList(){
		return registryDao.getEntityList();
	}

	@Override
	public String addEntity(Model rdfModel, String subject, String property) throws DuplicateRecordException, EntityCreationException,
	EncryptionException, AuditFailedException, MultipleEntityException, RecordNotFoundException {
		try {
			Resource root = getRootNode(rdfModel);
			String label = getRootLabel(root);
			Graph graph = generateGraphFromRDF(rdfModel);

			// Append _: to the root node label to create the entity as Apache Jena removes the _: for the root node label
			// if it is a blank node
			return registryDao.addEntity(graph, label, subject, property);

		} catch (EntityCreationException | EncryptionException | AuditFailedException | DuplicateRecordException | MultipleEntityException ex) {
			throw ex;
		} catch (Exception ex) {
			logger.error("Exception when creating entity: ", ex);
			throw ex;
		}
	}

	@Override
	public boolean updateEntity(Model entity) throws RecordNotFoundException, EntityCreationException, EncryptionException, AuditFailedException, MultipleEntityException {
		Resource root = getRootNode(entity);
		String label = getRootLabel(root);
		Graph graph = generateGraphFromRDF(entity);
		logger.debug("Service layer graph :", graph);
		return registryDao.updateEntity(graph, label, "update");
	}


	@Override
	public org.eclipse.rdf4j.model.Model getEntityById(String label) throws RecordNotFoundException, EncryptionException, AuditFailedException {
		Graph graph = registryDao.getEntityById(label);
		org.eclipse.rdf4j.model.Model model = RDF2Graph.convertGraph2RDFModel(graph, label);
		logger.debug("RegistryServiceImpl : rdf4j model :", model);
		return model;
	}

	/*@Override
	public boolean deleteEntity(Model rdfModel) throws AuditFailedException, RecordNotFoundException{
		StmtIterator iterator = rdfModel.listStatements();
		Graph graph = GraphDBFactory.getEmptyGraph();
		while (iterator.hasNext()) {
			Statement rdfStatement = iterator.nextStatement();
			org.eclipse.rdf4j.model.Statement rdf4jStatement = JenaRDF4J.asrdf4jStatement(rdfStatement);
			graph = RDF2Graph.convertRDFStatement2Graph(rdf4jStatement, graph);
		}

		return registryDao.deleteEntity(graph, "");
	}*/

	public HealthCheckResponse health() throws Exception {
		HealthCheckResponse healthCheck;
		boolean encryptionServiceStatusUp = encryptionService.isEncryptionServiceUp();
		boolean databaseServiceup = databaseProvider.isDatabaseServiceUp();
		boolean overallHealthStatus = encryptionServiceStatusUp && databaseServiceup;

		ComponentHealthInfo encryptionHealthInfo = new ComponentHealthInfo(Constants.SUNBIRD_ENCRYPTION_SERVICE_NAME, encryptionServiceStatusUp);
		ComponentHealthInfo databaseServiceInfo = new ComponentHealthInfo(Constants.OPENSABER_DATABASE_NAME, databaseServiceup);
		List<ComponentHealthInfo> checks = new ArrayList<>();
		checks.add(encryptionHealthInfo);
		checks.add(databaseServiceInfo);
		healthCheck = new HealthCheckResponse(Constants.OPENSABER_REGISTRY_API_NAME, overallHealthStatus, checks);
		logger.info("Heath Check :  encryptionHealthInfo  {} \n\t  databaseServiceInfo {} ", checks.get(0), checks.get(1));
		return healthCheck;
	}

	/*
	 * (non-Javadoc) frameEntity as generic, moved to factory impl for jsonld.
	 * @see io.opensaber.registry.service.RegistryService#frameEntity(org.eclipse.rdf4j.model.Model)
	 */
/*	@Override
	public String frameEntity(org.eclipse.rdf4j.model.Model entityModel) throws IOException, MultipleEntityException, EntityCreationException {
		Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);		
		Resource root = getRootNode(jenaEntityModel);
		String rootLabelType = getTypeForRootLabel(jenaEntityModel, root);
		logger.debug("RegistryServiceImpl : jenaEntityModel for framing: {} \n root : {}, \n rootLabelType: {}",jenaEntityModel,root,rootLabelType);
		DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();
		JsonLDWriteContext ctx = new JsonLDWriteContext();
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
		String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
		fileString = fileString.replace("<@type>", rootLabelType);
		ctx.setFrame(fileString);
		WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT);
		PrefixMap pm = RiotLib.prefixMap(g);
		String base = null;
		StringWriter sWriterJena = new StringWriter();
		w.write(sWriterJena, g, pm, base, ctx);
		String jenaJSON = sWriterJena.toString();
		logger.debug("RegistryServiceImpl : jenaJSON for framing : {}", jenaJSON);
		return jenaJSON;
	}
	
	@Override
	public String frameSearchEntity(org.eclipse.rdf4j.model.Model entityModel) throws IOException, MultipleEntityException, EntityCreationException {
		Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);
		String jenaJSON = "";
		if(!jenaEntityModel.isEmpty()){
			String rootLabelType = getTypeForSearch(jenaEntityModel);
			logger.debug("RegistryServiceImpl : jenaEntityModel for framing: {} \n root : {}, \n rootLabelType: {}",jenaEntityModel,rootLabelType);
			DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();
			JsonLDWriteContext ctx = new JsonLDWriteContext();
			InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
			String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
			fileString = fileString.replace("<@type>", rootLabelType);
			ctx.setFrame(fileString);
			WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT);
			PrefixMap pm = RiotLib.prefixMap(g);
			String base = null;
			StringWriter sWriterJena = new StringWriter();
			w.write(sWriterJena, g, pm, base, ctx);
			jenaJSON = sWriterJena.toString();
		}
		logger.debug("RegistryServiceImpl : jenaJSON for framing : {}", jenaJSON);
		return jenaJSON;
	}
	
	@Override
	public String frameAuditEntity(org.eclipse.rdf4j.model.Model entityModel) throws IOException {
		Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);
		logger.debug("RegistryServiceImpl : jenaEntityModel for audit-framing: {} ",jenaEntityModel);
		DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();
		JsonLDWriteContext ctx = new JsonLDWriteContext();
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(auditFrameFile);
		String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
		ctx.setFrame(fileString);
		WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT);
		PrefixMap pm = RiotLib.prefixMap(g);
		String base = null;
		StringWriter sWriterJena = new StringWriter();
		w.write(sWriterJena, g, pm, base, ctx);
		String jenaJSON = sWriterJena.toString();
		logger.debug("RegistryServiceImpl : jenaJSON for audit-framing: {}", jenaJSON);
		return jenaJSON;
	}*/
	
	@Override
	public org.eclipse.rdf4j.model.Model getAuditNode(String id) throws IOException, NoSuchElementException, RecordNotFoundException,
			EncryptionException, AuditFailedException {
		String label = id + "-AUDIT";
		Graph graph = registryDao.getEntityById(label);
		org.eclipse.rdf4j.model.Model model = RDF2Graph.convertGraph2RDFModel(graph, label);
		logger.debug("RegistryServiceImpl : Audit Model : " + model);
		return model;
	}

	@Override
	public boolean deleteEntityById(String id) throws AuditFailedException, RecordNotFoundException {
		boolean isDeleted = registryDao.deleteEntityById(id);
		if(!isDeleted){
			throw new UnsupportedOperationException(Constants.DELETE_UNSUPPORTED_OPERATION_ON_ENTITY);
		}
		return isDeleted;
	}

	private Graph generateGraphFromRDF(Model entity) throws EntityCreationException, MultipleEntityException{
		Graph graph = GraphDBFactory.getEmptyGraph();
		StmtIterator iterator = entity.listStatements();
		while (iterator.hasNext()) {
			Statement rdfStatement = iterator.nextStatement();
			org.eclipse.rdf4j.model.Statement rdf4jStatement = JenaRDF4J.asrdf4jStatement(rdfStatement);
			graph = RDF2Graph.convertRDFStatement2Graph(rdf4jStatement, graph);
		}
		return graph;
	}
	
	private String getRootLabel(Resource subject) {
		String label = subject.toString();
		if (subject.isAnon() && subject.getURI() == null) {
			label = String.format("_:%s", label);
		}
		return label;
	}
	/*
	 * to mode out in utils.
	 */
	private Resource getRootNode(Model entity) throws EntityCreationException, MultipleEntityException{
		List<Resource> rootLabels = RDFUtil.getRootLabels(entity);
		if (rootLabels.size() == 0) {
			throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
		} else if (rootLabels.size() > 1) {
			throw new MultipleEntityException(Constants.ADD_UPDATE_MULTIPLE_ENTITIES_MESSAGE);
		} else {
			return rootLabels.get(0);
		}
	}
/*	private String getTypeForRootLabel(Model entity, Resource root) throws EntityCreationException, MultipleEntityException{
		List<String> rootLabelType = RDFUtil.getTypeForSubject(entity, root);
		if (rootLabelType.size() == 0) {
			throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
		} else if (rootLabelType.size() > 1) {
			throw new MultipleEntityException(Constants.ADD_UPDATE_MULTIPLE_ENTITIES_MESSAGE);
		} else {
			return rootLabelType.get(0);
		}
	}
	
	
	private String getTypeForSearch(Model entity) throws EntityCreationException, MultipleEntityException{
		List<Resource> rootLabels = RDFUtil.getRootLabels(entity);
		if (rootLabels.size() == 0) {
			throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
		} else {
			return getTypeForRootLabel(entity, rootLabels.get(0));
		}
	}*/

	@Override
	public String getEntityFramedById(String id)
			throws RecordNotFoundException, EncryptionException, AuditFailedException, IOException, MultipleEntityException, EntityCreationException {
		Graph graph = registryDao.getEntityById(id);
		org.eclipse.rdf4j.model.Model model = RDF2Graph.convertGraph2RDFModel(graph, id);
		logger.debug("RegistryServiceImpl : rdf4j model :", model);
		frameEntity.setModel(model);
		return frameEntity.getContent();
	}

	@Override
	public String getAuditNodeFramed(String id) throws IOException, NoSuchElementException, RecordNotFoundException,
			EncryptionException, AuditFailedException, MultipleEntityException, EntityCreationException {
		String label = id + "-AUDIT";
		Graph graph = registryDao.getEntityById(label);
		org.eclipse.rdf4j.model.Model model = RDF2Graph.convertGraph2RDFModel(graph, label);
		logger.debug("RegistryServiceImpl : Audit Model : " + model);
		frameEntity.setModel(model);
		return frameEntity.getContent();
	}
}
