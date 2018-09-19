package io.opensaber.registry.frame;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.jena.ext.com.google.common.io.ByteStreams;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.WriterDatasetRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.opensaber.converters.JenaRDF4J;
import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;
import io.opensaber.registry.frame.FrameEntity;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.RDFUtil;

@Component
public class FrameEntityImpl implements FrameEntity {

	private static Logger logger = LoggerFactory.getLogger(FrameEntityImpl.class);

	
	@Value("${frame.file}")
	private String frameFile; 
	
	private org.eclipse.rdf4j.model.Model entityModel;	
	private Gson gson = new Gson();
	private Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
	
	@Override
	public String getContent() throws IOException, MultipleEntityException, EntityCreationException {
		String jenaJson = frameEntity2Json(entityModel);
	    logger.info("JsonldResponseContent: Framed Jena JSON- "+jenaJson);

/*		ResultContent rs = new ResultContent();
		if(jenaJson.isEmpty() || jenaJson == null)
			rs.setResult(new HashMap<String,Object>());
		else
			rs.setResult(gson.fromJson(jenaJson, mapType));
		return rs;*/
	    
	    return jenaJson;
	}
	
    @Override
	public void setModel(org.eclipse.rdf4j.model.Model entityModel){
		this.entityModel = entityModel;
	}
    
    
	/**
	 * Helper method to convert the RDF4j model to JSONLD
	 * @param entityModel
	 * @return
	 * @throws IOException
	 * @throws MultipleEntityException
	 * @throws EntityCreationException
	 */	
	private String frameEntity2Json(org.eclipse.rdf4j.model.Model entityModel) throws IOException, MultipleEntityException, EntityCreationException {
		Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);
		String jenaJSON = "";
		if(!jenaEntityModel.isEmpty()){			
			DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();			
			JsonLDWriteContext ctx = prepareJsonLDWriteContext(jenaEntityModel);
			StringWriter writer = getWriter(g, ctx);
			jenaJSON = writer.toString();			
		}
		return jenaJSON;
	}
	
	/**
	 * TODO: for fetchAudit, needs to be generic
	 * @param jenaEntityModel
	 * @return
	 * @throws IOException
	 * @throws EntityCreationException
	 * @throws MultipleEntityException
	 */
	private JsonLDWriteContext prepareJsonLDWriteContext(Model jenaEntityModel) throws IOException, EntityCreationException, MultipleEntityException{
		JsonLDWriteContext ctx = new JsonLDWriteContext();			
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
		String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
		
		List<Resource> rootLabels = RDFUtil.getRootLabels(jenaEntityModel);
		String rootLabelType = null;
		switch(rootLabels.size()){
        	case 0: 
        		throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
        	default: 
        		List<String> rootLabelTypes = RDFUtil.getTypeForSubject(jenaEntityModel, rootLabels.iterator().next());       		
        		rootLabelType = rootLabelTypes.get(0);
		}
		// TODO: to check for auditframe file
		if(fileString.contains("<@type>"))
			fileString = fileString.replace("<@type>", rootLabelType);			
	    
		logger.info("JsonldResponseContent: Frame file - "+fileString);
		ctx.setFrame(fileString);
		return ctx;
	}
	
	private StringWriter getWriter(DatasetGraph g, JsonLDWriteContext ctx){
		WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT);
		PrefixMap pm = RiotLib.prefixMap(g);
		String base = null;
		StringWriter sWriter = new StringWriter();
		w.write(sWriter, g, pm, base, ctx);
		return sWriter;
	}

	
	
	
	
	
	
	
	
	
/*	private String getType(Model entity) throws EntityCreationException, MultipleEntityException{
		List<Resource> rootLabels = RDFUtil.getRootLabels(entity);
		if (rootLabels.size() == 0) {
			throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
		} else {
			return getTypeForRootLabel(entity, rootLabels.get(0));
		}
	}
	
	private String getTypeForRootLabel(Model entity, Resource root) throws EntityCreationException, MultipleEntityException{
		List<String> rootLabelType = RDFUtil.getTypeForSubject(entity, root);
		if (rootLabelType.size() == 0) {
			throw new EntityCreationException(Constants.NO_ENTITY_AVAILABLE_MESSAGE);
		} else if (rootLabelType.size() > 1) {
			throw new MultipleEntityException(Constants.ADD_UPDATE_MULTIPLE_ENTITIES_MESSAGE);
		} else {
			return rootLabelType.get(0);
		}
	}*/
	
/*	private String entity2JenaJson(org.eclipse.rdf4j.model.Model entityModel) throws IOException, MultipleEntityException, EntityCreationException {
		Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);		
		Resource root = getRootNode(jenaEntityModel);
		String rootLabelType = getTypeForRootLabel(jenaEntityModel, root);
		DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();
		JsonLDWriteContext ctx = new JsonLDWriteContext();
		//InputStream is = this.getClass().getClassLoader().getResourceAsStream("frame.json");
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
		return jenaJSON;
	}
	
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
	
		private String frameAnyEntity(org.eclipse.rdf4j.model.Model entityModel) throws IOException, MultipleEntityException, EntityCreationException {
		Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);
		String jenaJSON = "";
		if(!jenaEntityModel.isEmpty()){
			
			//logger.debug("RegistryServiceImpl : jenaEntityModel for framing: {} \n root : {}, \n rootLabelType: {}",jenaEntityModel,rootLabelType);
			DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph(););
			
			JsonLDWriteContext ctx = new JsonLDWriteContext();			
			InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
			String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
			String rootLabelType = getType(jenaEntityModel);
			if(fileString.contains("<@type>"))
				fileString = fileString.replace("<@type>", rootLabelType);			
			ctx.setFrame(fileString);
			
			WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT);
			PrefixMap pm = RiotLib.prefixMap(g);
			String base = null;
			StringWriter sWriterJena = new StringWriter();
			w.write(sWriterJena, g, pm, base, ctx);
			jenaJSON = sWriterJena.toString();
			
			
		}
		//logger.debug("RegistryServiceImpl : jenaJSON for framing : {}", jenaJSON);
		return jenaJSON;
	}
	*/

}
