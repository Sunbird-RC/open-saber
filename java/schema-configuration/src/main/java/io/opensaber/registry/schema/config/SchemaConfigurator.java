package io.opensaber.registry.schema.config;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.ext.com.google.common.io.ByteStreams;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.RDFUtil;

public class SchemaConfigurator {

	private static final String FORMAT = "JSON-LD";
	private static Logger logger = LoggerFactory.getLogger(SchemaConfigurator.class);
	private SchemaLoader schemaLoader;
	private String registrySystemBase;
	private Model schemaConfig;

	public SchemaConfigurator(String schemaFile, String registrySystemBase, SchemaLoader schemaLoader)
			throws IOException {

		this.registrySystemBase = registrySystemBase;
		loadSchemaConfigModel(schemaFile);
		this.schemaLoader = schemaLoader;
	}

	private void loadSchemaConfigModel(String schemaFile) throws IOException {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(schemaFile);
		if (is == null) {
			throw new IOException(Constants.SCHEMA_CONFIGURATION_MISSING);
		}
		String contents = new String(ByteStreams.toByteArray(is));
		schemaConfig = RDFUtil.getRdfModelBasedOnFormat(contents, FORMAT);
	}

	public boolean isPrivate(String propertyName) {
		Property property = ResourceFactory.createProperty(registrySystemBase + Constants.PRIVACY_PROPERTY);
		RDFNode rdfNode = ResourceFactory.createResource(propertyName);
		StmtIterator iter = schemaConfig.listStatements(null, property, rdfNode);
		return iter.hasNext();
	}

	public NodeIterator getAllPrivateProperties() {
		Property property = ResourceFactory.createProperty(registrySystemBase + Constants.PRIVACY_PROPERTY);
		return schemaConfig.listObjectsOfProperty(property);
	}

	public boolean isEncrypted(String tailPropertyKey) {
		if (tailPropertyKey != null) {
			return tailPropertyKey.substring(0, Math.min(tailPropertyKey.length(), 9)).equalsIgnoreCase("encrypted");
		} else
			return false;
	}

	public boolean isSingleValued(String property) {
		logger.debug("Property being verified for single-valued, multi-valued:" + property);
		Property predicate = ResourceFactory.createProperty("http://shex.io/ns/shex#predicate");
		RDFNode rdfNode = ResourceFactory.createResource(property);
		ResIterator resIter = schemaLoader.getValidationConfig().listSubjectsWithProperty(predicate, rdfNode);

		while (resIter.hasNext()) {
			Resource subject = resIter.next();
			Long minValue = getValueConstraint("http://shex.io/ns/shex#min", subject);
			Long maxValue = getValueConstraint("http://shex.io/ns/shex#max", subject);
			if (minValue == null || maxValue == null) {
				logger.debug("Single-valued");
				return true;
			}
			if (minValue > 1) {
				logger.debug("Multi-valued");
				return false;
			} else if (maxValue > 1) {
				logger.debug("Multi-valued");
				return false;
			} else {
				logger.debug("Single-valued");
				return true;
			}
		}
		logger.debug("Property not matching any condition:" + property);
		return true;
	}

	private Long getValueConstraint(String constraint, Resource subject) {
		Property predicate = ResourceFactory.createProperty(constraint);
		NodeIterator nodeIter = schemaLoader.getValidationConfig().listObjectsOfProperty(subject, predicate);

		while (nodeIter.hasNext()) {
			RDFNode node = nodeIter.next();
			if (node.isLiteral()) {
				Literal literal = node.asLiteral();
				return literal.getLong();
			} else if (node.isURIResource()) {
				return 2L;
			}
		}
		return null;
	}

	public Model getSchemaConfig() {
		return schemaConfig;
	}

}
