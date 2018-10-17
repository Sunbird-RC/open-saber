package io.opensaber.registry.service;

import es.weso.schema.Schema;
import io.opensaber.pojos.ValidationResponse;
import io.opensaber.registry.exception.RDFValidationException;
import io.opensaber.registry.middleware.MiddlewareHaltException;
import io.opensaber.registry.middleware.Validator;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.RDFUtil;
import io.opensaber.validators.shex.shaclex.ShaclexValidator;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RDFValidator {

	private static final String RDF_DATA_IS_MISSING = "RDF Data is missing!";
	private static final String RDF_DATA_IS_INVALID = "Data validation failed!";
	private static final String RDF_VALIDATION_MAPPING_IS_INVALID = "RDF validation mapping is invalid!";
	private static final String RDF_VALIDATION_MAPPING_MISSING = "RDF validation mapping is missing!";
    private static final String SCHEMA_IS_NULL = "Schema for validation is missing";
	private static final String INVALID_REQUEST_PATH = "Request URL is invalid";
	private static final String ADD_REQUEST_PATH = "/add";

	private static final String VALIDATION_IS_MISSING = "Validation is missing";
	private static final String VALIDATION_MISSING_FOR_TYPE = "Validation missing for type";
	private static final String SX_SHAPE_IRI = "http://shex.io/ns/shex#Shape";
	private static final String SHAPE_EXPRESSION_IRI = "http://shex.io/ns/shex#expression";
	private static final String SHAPE_EXPRESSIONS_IRI = "http://shex.io/ns/shex#expressions";
	private static final String SHAPE_VALUES_IRI = "http://shex.io/ns/shex#values";
	private static final String SHAPE_VALUE_EXPR_IRI = "http://shex.io/ns/shex#valueExpr";
	private static final String JSON_LD_FORMAT = "JSON-LD";
	
	private Map<String,String> shapeTypeMap;
	private Schema schemaForCreate;
	private Schema schemaForUpdate;
	private Schema schema;

	public RDFValidator(Schema schema) {
		this.schema =  schema;
		this.shapeTypeMap = getShapeMap(RDF.type, SX_SHAPE_IRI);
	}

	public RDFValidator(Schema schemaForCreate, Schema schemaForUpdate) {
		this.schemaForCreate = schemaForCreate;
		this.schemaForUpdate = schemaForUpdate;
		this.shapeTypeMap = getShapeMap(RDF.type, SX_SHAPE_IRI);
	}
	
	public Map<String,String> getShapeTypeMap(){
		return shapeTypeMap;
	}

	public ValidationResponse validateRDFWithSchema(Model rdf, String methodOrigin) throws IOException, RDFValidationException {
		if (rdf == null) {
			throw new RDFValidationException(RDF_DATA_IS_MISSING);
		}else if (!(rdf instanceof Model)) {
			throw new RDFValidationException(RDF_DATA_IS_INVALID);
		}else if (methodOrigin == null) {
			throw new RDFValidationException(INVALID_REQUEST_PATH);
		}else if (schemaForCreate == null || schemaForUpdate == null) {
			throw new RDFValidationException(SCHEMA_IS_NULL);
		}else if(shapeTypeMap == null){
			throw new RDFValidationException(this.getClass().getName()+VALIDATION_IS_MISSING);
		} else {
			Schema schema = null;
			Model validationRdf = generateShapeModel(rdf);
			mergeModels( rdf,  validationRdf);
			ValidationResponse validationResponse = null;
			if(Constants.CREATE_METHOD_ORIGIN.equals(methodOrigin)){
				schema = schemaForCreate;
			} else {
				schema = schemaForUpdate;
			}
			//schema =  this.schema;
			Validator validator = new ShaclexValidator(schema, validationRdf);
			validationResponse = validator.validate();
			return validationResponse;
		}
	}

	private void mergeModels(Model RDF, Model validationRDF){
		if(validationRDF!=null){
			validationRDF.add(RDF.listStatements());
		}
	}
	
	
	private Model generateShapeModel(Model inputRdf) throws RDFValidationException {
		Model model = ModelFactory.createDefaultModel();
		List<Resource> labelNodes = RDFUtil.getRootLabels(inputRdf);
		if (labelNodes.size() != 1) {
			throw new RDFValidationException(this.getClass().getName() + RDF_DATA_IS_INVALID);
		}
		Resource target = labelNodes.get(0);
		List<String> typeList = RDFUtil.getTypeForSubject(inputRdf, target);
		if (typeList.size() != 1) {
			throw new RDFValidationException(this.getClass().getName() + RDF_DATA_IS_INVALID);
		}
		String targetType = typeList.get(0);
		String shapeName = shapeTypeMap.get(targetType);
		if (shapeName == null) {
			throw new RDFValidationException(this.getClass().getName() + VALIDATION_MISSING_FOR_TYPE);
		}

		Resource subjectResource = ResourceFactory.createResource(shapeName);
		Property predicate = ResourceFactory.createProperty(Constants.TARGET_NODE_IRI);
		model.add(subjectResource, predicate, target);
		return model;
	}

	/**
	 * This method generates a shapemap which contains mappings between each entity type and the corresponding
	 * shape that the validations should target. Here we first filter out all the shape resources from the validationConfig.
	 * Then we iterate through the list of shape resources and do a bunch of filtering from the validationConfig
	 * based on a few predicates to finally arrive at the type for which the shape is targeted.
	 * @param predicate
	 * @param object
	 * @param validationConfig is the rdf model format of the Schema file used for validations
	 * @return
	 */
	private Map<String,String> getShapeMap(Property predicate, String object){
		Map<String,String> shapeTypeMap = new HashMap<String, String>();
		Model validationConfig = getValidationConfigModel();
		List<Resource> shapeList = RDFUtil.getListOfSubjects(predicate, object, validationConfig);
		for(Resource shape: shapeList){
			RDFNode node = getObjectAfterFilter(shape, SHAPE_EXPRESSION_IRI, validationConfig);
			RDFNode firstNode = getObjectAfterFilter(node, SHAPE_EXPRESSIONS_IRI, validationConfig);
			RDFNode secondNode = getObjectAfterFilter(firstNode, RDF.first.getURI(), validationConfig);
			RDFNode thirdNode = getObjectAfterFilter(secondNode, SHAPE_VALUES_IRI, validationConfig);
			if(thirdNode == null){
				thirdNode = getObjectAfterFilter(secondNode, SHAPE_VALUE_EXPR_IRI, validationConfig);
			}
			RDFNode fourthNode = getObjectAfterFilter(thirdNode, SHAPE_VALUES_IRI, validationConfig);
			RDFNode typeNode = getObjectAfterFilter(fourthNode, RDF.first.getURI(), validationConfig);
			if(typeNode!=null){
				shapeTypeMap.put(typeNode.toString(), shape.toString());
				addOtherTypesForShape(fourthNode, validationConfig, shapeTypeMap, shape);
			}
		}
		return shapeTypeMap;
	}
	
	/**
	 * This method is created to include multiple types for a shape in the shapeMap.
	 * @param subjectOfTypeNode
	 * @param validationConfig
	 * @param shapeMap
	 * @param shape
	 */
	private void addOtherTypesForShape(RDFNode subjectOfTypeNode, Model validationConfig, Map<String,String> shapeTypeMap, Resource shape){
		RDFNode node = getObjectAfterFilter(subjectOfTypeNode, RDF.rest.getURI(), validationConfig);
		if(!node.equals(RDF.nil)){
			RDFNode typeNode = getObjectAfterFilter(node, RDF.first.getURI(), validationConfig);
			shapeTypeMap.put(typeNode.toString(), shape.toString());
			addOtherTypesForShape(node, validationConfig, shapeTypeMap, shape);
		}
	}
	
	private RDFNode getObjectAfterFilter(RDFNode node, String predicate, Model validationConfig){
			Property property = ResourceFactory.createProperty(predicate);
			List<RDFNode> nodeList = RDFUtil.getListOfObjectNodes((Resource)node, property,validationConfig);
			if(nodeList.size() != 0){
				return nodeList.get(0);
			}
			return null;
	}
	
	private Model getValidationConfigModel(){
		return RDFUtil.getRdfModelBasedOnFormat(schemaForUpdate.serialize(JSON_LD_FORMAT).right().get(), JSON_LD_FORMAT);
	}

}
