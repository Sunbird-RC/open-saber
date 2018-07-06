package io.opensaber.registry.dao.impl;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableList;
import io.opensaber.pojos.OpenSaberInstrumentation;
import com.google.gson.Gson;
import io.opensaber.registry.exception.AuditFailedException;
import io.opensaber.registry.model.AuditRecord;
import io.opensaber.registry.schema.config.SchemaConfigurator;
import io.opensaber.registry.service.EncryptionService;
import io.opensaber.registry.sink.DatabaseProvider;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import io.opensaber.registry.authorization.pojos.AuthInfo;
import io.opensaber.registry.dao.RegistryDao;
import io.opensaber.registry.exception.DuplicateRecordException;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.middleware.util.Constants;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

@Component
public class RegistryDaoImpl implements RegistryDao {

    public static final String META = "meta.";
    public static final String EMPTY_STRING = StringUtils.EMPTY;
    private static Logger logger = LoggerFactory.getLogger(RegistryDaoImpl.class);
    public static final String INTERNAL_PROPERTY_PREFIX = "@_";
    public static final String IMPOSSIBLE_LABEL = "-1";

	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Autowired
    private DatabaseProvider databaseProvider;

    @Autowired
    private EncryptionService encryptionService;

    @Value("${registry.context.base}")
    private String registryContext;

    @Autowired
    SchemaConfigurator schemaConfigurator;

    @Autowired
    ApplicationContext appContext;

    @Value("${audit.enabled}")
    private boolean auditEnabled;

    @Value("${authentication.enabled}")
    private boolean authenticationEnabled;

    @Autowired
    private OpenSaberInstrumentation watch;

    @Override
    public List getEntityList() {
        // TODO Auto-generated method stub
        return null;
    }

	@Override
	public String addEntity(Graph entity, String label, String rootNodeLabel, String property) throws DuplicateRecordException, RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException{
		logger.debug("Database Provider features: \n" + databaseProvider.getGraphStore().features());
		Graph graphFromStore = databaseProvider.getGraphStore();
		if (rootNodeLabel!=null && property!=null && !doesExist(rootNodeLabel, graphFromStore)) {
			throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
		} else if (doesExist(label, graphFromStore)) {
			throw new DuplicateRecordException(Constants.DUPLICATE_RECORD_MESSAGE);
		}

		TinkerGraph graph = (TinkerGraph) entity;
		GraphTraversalSource traversal = graph.traversal();
		if (graphFromStore.features().graph().supportsTransactions()) {
			org.apache.tinkerpop.gremlin.structure.Transaction tx = graphFromStore.tx();
			tx.onReadWrite(org.apache.tinkerpop.gremlin.structure.Transaction.READ_WRITE_BEHAVIOR.AUTO);
            watch.start("RegistryDaoImpl.addOrUpdateVerticesAndEdges");
            label = addOrUpdateVerticesAndEdges(graphFromStore, traversal, label, "create");
            watch.stop("RegistryDaoImpl.addOrUpdateVerticesAndEdges");
            if (rootNodeLabel!=null && property!=null){
				connectNodes(rootNodeLabel, label, property);
			}
            synchronized(this){
                tx.commit();
            }
            logger.debug("RegistryDaoImpl : Entity added for transactional DB with rootNodeLabel : {},	label	:	{},	property	: 	{}", rootNodeLabel, label, property);
		}else{
            watch.start("RegistryDaoImpl.addOrUpdateVerticesAndEdges");
			label = addOrUpdateVerticesAndEdges(graphFromStore, traversal, label, "create");
			watch.stop("RegistryDaoImpl.addOrUpdateVerticesAndEdges");
            logger.debug("RegistryDaoImpl : Entity added for non-transactional DB with rootNodeLabel : {},	label	:	{},	property	: 	{}", rootNodeLabel, label, property);
            if (rootNodeLabel!=null && property!=null){
				connectNodes(rootNodeLabel, label, property);
			}
		}
		logger.info("Successfully created entity with label " + label);
		// closeGraph(graphFromStore);
		return label;
	}
    private void connectNodes(String rootLabel, String label, String property) throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        Graph graphFromStore = databaseProvider.getGraphStore();

        if (!doesExist(rootLabel, graphFromStore)) {
            // closeGraph(graphFromStore);
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        }

        if (!doesExist(label, graphFromStore)) {
            // closeGraph(graphFromStore);
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        }
        connectRootToEntity(graphFromStore, rootLabel, label, property);

    }

    private void connectRootToEntity(Graph dbGraph, String rootLabel, String label, String property) throws NoSuchElementException, AuditFailedException {
        Iterator<Vertex> rootGts = getVerticesIterator(dbGraph,rootLabel);
        Iterator<Vertex> entityGts = getVerticesIterator(dbGraph,label);
        Vertex rootVertex = rootGts.next();
        Vertex entityVertex = entityGts.next();
        rootVertex.addEdge(property, entityVertex);
        if(auditEnabled){
            watch.start("RegistryDaoImpl.connectRootToEntity.auditRecord");
            AuditRecord record = appContext.getBean(AuditRecord.class);
            record
                    .subject(label(rootVertex))
                    .predicate(property)
                    .oldObject(null)
                    .newObject(label(entityVertex))
                    .record(databaseProvider);
            watch.stop("RegistryDaoImpl.connectRootToEntity.auditRecord");
        }
        logger.debug("RegistryDaoImpl : Audit record generated of connectRootToEntity for rootLabel : {}, label	:	{}, property :	{}", rootLabel, label, property);
    }

    private String label(Vertex vertex) {
        VertexProperty<Object> label = vertex.property(internalPropertyKey("label"));
        if(label.isPresent()){
            return String.valueOf(label.value());
        } else {
            return IMPOSSIBLE_LABEL;
        }
    }

    /**
	 * This method creates the root node of the entity if it already isn't present in the graph store
	 * or updates the properties of the root node or adds new properties if the properties are not already
	 * present in the node.
	 * @param dbGraph
	 * @param entitySource
	 * @param rootLabel
	 * @throws EncryptionException
	 * @throws NoSuchElementException 
	 */
	private String addOrUpdateVerticesAndEdges(Graph dbGraph,
                                               GraphTraversalSource entitySource, String rootLabel, String methodOrigin)
					throws NoSuchElementException, EncryptionException, AuditFailedException, RecordNotFoundException {

		GraphTraversal<Vertex, Vertex> gts = entitySource.clone().V().has(internalPropertyKey("label"),rootLabel);
		String label = rootLabel;
		while (gts.hasNext()) {
			Vertex v = gts.next();
			Iterator<Vertex> hasLabel = getVerticesIterator(dbGraph, label);
			ImmutableTable.Builder<Vertex,Vertex,Map<String,Object>> encDecPropertyBuilder = ImmutableTable.builder();
			if (hasLabel.hasNext()) {
				logger.info(String.format("Root node label {} already exists. Updating properties for the root node.", rootLabel));
				Vertex existingVertex = hasLabel.next();
				setAuditInfo(v, false);
				copyProperties(v, existingVertex, methodOrigin, encDecPropertyBuilder);
				addOrUpdateVertexAndEdge(v, existingVertex, dbGraph, methodOrigin, encDecPropertyBuilder);
			} else {
				if(methodOrigin.equalsIgnoreCase("update")){
					throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
				}
				logger.info(String.format("Creating entity with label {}", rootLabel));
                Vertex newVertex = dbGraph.addVertex("Entity");
                newVertex.property(internalPropertyKey("label"),generateNamespacedLabel(rootLabel));
                label = generateNamespacedLabel(newVertex.id().toString());
//                newVertex.property(internalPropertyKey("label"),label);
				setAuditInfo(v, true);
				copyProperties(v, newVertex,methodOrigin, encDecPropertyBuilder);
				addOrUpdateVertexAndEdge(v, newVertex, dbGraph,methodOrigin, encDecPropertyBuilder);
			}
			Table<Vertex,Vertex,Map<String,Object>>  encDecPropertyTable = encDecPropertyBuilder.build();
			long timestamp = System.currentTimeMillis();
			if(encDecPropertyTable.size() > 0){
				updateEncryptedDecryptedProperties(encDecPropertyTable, methodOrigin);
			}
			logger.info("Time taken to update encrypted properties:"+(System.currentTimeMillis() - timestamp));
		}
		return label;
	}

    /**
	 * This method takes the root node of an entity and then recursively creates or updates child vertices
	 * and edges.
	 * @param sourceVertex
	 * @param dbVertex
	 * @param dbGraph
	 * @throws EncryptionException 
	 * @throws NoSuchElementException 
	 */
	private void addOrUpdateVertexAndEdge(Vertex sourceVertex, Vertex dbVertex, Graph dbGraph, String methodOrigin, ImmutableTable.Builder<Vertex,Vertex,Map<String,Object>> encDecPropertyBuilder)
			throws NoSuchElementException, EncryptionException, AuditFailedException, RecordNotFoundException{
		Iterator<Edge> sourceEdges1 = sourceVertex.edges(Direction.OUT);
		Iterator<Edge> sourceEdges2 = sourceVertex.edges(Direction.OUT);
		Stack<Pair<Vertex, Vertex>> parsedVertices = new Stack<>();
		List<Edge> dbEdgesForVertex = ImmutableList.copyOf(dbVertex.edges(Direction.OUT));
        List<Edge> existingEdgeList = new ArrayList<Edge>();
        
		while(sourceEdges2.hasNext()) {
			Edge e = sourceEdges2.next();
			Vertex ver = e.inVertex();
			String edgeLabel = e.label();
			// get the first matching edge in the DB
			Optional<Edge> existingEdge =
                    getFirstMatchingEdgeAndVertex(dbEdgesForVertex, ver, edgeLabel);
			if(existingEdge.isPresent()){
				existingEdgeList.add(existingEdge.get());
			}
		}
		// existingEdgeList contains matching edges
		logger.info("RegistryDaoImpl : Matching list size:"+existingEdgeList.size());

		while(sourceEdges1.hasNext()) {
			Edge sourceEdge = sourceEdges1.next();
			Vertex sourceInVertex = sourceEdge.inVertex();
			String edgeLabel = sourceEdge.label();
			Iterator<Vertex> dbVertexIterator = getVerticesIterator(dbGraph,label(sourceInVertex));
            List<String> matchedIDs;
			if(!dbVertexIterator.hasNext()){
                matchedIDs = databaseProvider.getIDsFromLabel(label(sourceInVertex));
				if(!matchedIDs.isEmpty())
                    dbVertexIterator = getVerticesIterator(dbGraph, generateNamespacedLabel(matchedIDs.get(0)));
			}
			Optional<Edge> existingEdge =
					dbEdgesForVertex.stream().filter(dbEdge -> {
					    return dbEdge.label().equalsIgnoreCase(sourceEdge.label());
                    }).findFirst();
			Optional<Edge> existingEdgeConnectedToExistingVertex =
                    getFirstMatchingEdgeAndVertex(dbEdgesForVertex, sourceInVertex, edgeLabel);
			deleteSingleValuedVertexEdge(dbVertex, sourceEdge, existingEdge, existingEdgeList, methodOrigin);
			if (dbVertexIterator.hasNext()) {
				Vertex existingVertex = dbVertexIterator.next();
				setAuditInfo(sourceInVertex, false);
				logger.info(String.format("Vertex with label {} already exists. Updating properties for the vertex", label(existingVertex)));
				copyProperties(sourceInVertex, existingVertex,methodOrigin, encDecPropertyBuilder);
				if(!existingEdgeConnectedToExistingVertex.isPresent()){
					Edge edgeAdded = dbVertex.addEdge(edgeLabel, existingVertex);
					existingEdgeList.add(edgeAdded);
					if(auditEnabled){
                        watch.start("RegistryDaoImpl.addOrUpdateVertexAndEdge.auditRecord");
                        AuditRecord record = appContext.getBean(AuditRecord.class);
                        record
                            .subject(label(dbVertex))
                            .predicate(sourceEdge.label())
                            .oldObject(null)
                            .newObject(label(existingVertex))
                            .record(databaseProvider);
                            watch.stop("RegistryDaoImpl.addOrUpdateVertexAndEdge.auditRecord");
					}
					logger.debug("RegistryDaoImpl : Audit record created for update/insert(upsert) with label : {}  ", label(dbVertex));
				}
				parsedVertices.push(new Pair<>(sourceInVertex, existingVertex));
			} else {
			    // did not find matching vertex in the DB
				if(methodOrigin.equalsIgnoreCase("update") && isaBlankNode(label(sourceInVertex))){
					throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
				}
				String label = generateBlankNodeLabel(label(sourceInVertex));
				Vertex newDBVertex = dbGraph.addVertex("Entity");//dbGraph.addVertex(label);
                newDBVertex.property(internalPropertyKey("label"),label);
				setAuditInfo(sourceInVertex, true);
				logger.debug(String.format("RegistryDaoImpl : Adding vertex with label {} and adding properties", label(newDBVertex)));
				copyProperties(sourceInVertex, newDBVertex, methodOrigin, encDecPropertyBuilder);
				logger.debug(String.format("RegistryDaoImpl : Adding edge with label {} for the vertex label {}.", sourceEdge.label(), label(newDBVertex)));

				Edge edgeAdded = dbVertex.addEdge(edgeLabel, newDBVertex);
				existingEdgeList.add(edgeAdded);
				if(auditEnabled){
                    AuditRecord record = appContext.getBean(AuditRecord.class);
                    watch.start("RegistryDaoImpl.addOrUpdateVertexAndEdge.auditRecord");
                    record
                        .subject(label(dbVertex))
                        .predicate(sourceEdge.label())
                        .oldObject(null)
                        .newObject(label(newDBVertex))
                        .record(databaseProvider);
                    watch.stop("RegistryDaoImpl.addOrUpdateVertexAndEdge.auditRecord");
				}
				logger.info("Audit record created for update with label : {} ", label(dbVertex));
				parsedVertices.push(new Pair<>(sourceInVertex, newDBVertex));
			}
		}
		for(Pair<Vertex, Vertex> pv : parsedVertices) {
			addOrUpdateVertexAndEdge(pv.getValue0(), pv.getValue1(), dbGraph,methodOrigin, encDecPropertyBuilder);
		}
	}

    /**
     * This method checks if deletion of edge and node is required based on criteria and invokes deleteEdgeAndNode method
     * @param dbSourceVertex
     * @param e
     * @param edgeAlreadyExists
     * @param edgeVertexMatchList
     * @param methodOrigin
     * @throws AuditFailedException
     */
    private void deleteSingleValuedVertexEdge(Vertex dbSourceVertex, Edge e, Optional<Edge> edgeAlreadyExists, List<Edge> edgeVertexMatchList, String methodOrigin)
            throws AuditFailedException{
        boolean isSingleValued = schemaConfigurator.isSingleValued(e.label());
        if((edgeAlreadyExists.isPresent() && methodOrigin.equalsIgnoreCase("update")) || isSingleValued){
            Iterator<Edge> edgeIter = dbSourceVertex.edges(Direction.OUT, e.label());
            while(edgeIter.hasNext()){
                Edge edge = edgeIter.next();
                Optional<Edge> existingEdgeVertex =
                        getFirstMatchingEdgeAndVertex(edgeVertexMatchList, edge.inVertex(), edge.label());
                if(!existingEdgeVertex.isPresent()){
                    deleteEdgeAndNode(dbSourceVertex, edge, null);
                }
            }
        }
    }

    /**
     * This method deletes the edge and node if the node is an orphan node and if not, deletes only the edge
     *
     * @param v
     * @param dbEdgeToBeRemoved
     * @param dbVertexToBeDeleted
     * @throws AuditFailedException
     */
    private void deleteEdgeAndNode(Vertex v, Edge dbEdgeToBeRemoved, Vertex dbVertexToBeDeleted) throws AuditFailedException {
        logger.info("Deleting edge and node of label : {}", dbEdgeToBeRemoved.label());
        if (dbVertexToBeDeleted == null) {
            dbVertexToBeDeleted = dbEdgeToBeRemoved.inVertex();
        }
        Iterator<Edge> inEdgeIter = dbVertexToBeDeleted.edges(Direction.IN);
        Iterator<Edge> outEdgeIter = dbVertexToBeDeleted.edges(Direction.OUT);
        String edgeLabel = dbEdgeToBeRemoved.label();
        String vertexLabel = label(dbVertexToBeDeleted);
        if ((inEdgeIter.hasNext() && IteratorUtils.count(inEdgeIter) > 1) || outEdgeIter.hasNext()) {
            logger.debug("RegistryDaoImpl : Deleting edge only for edge-label: {}", dbEdgeToBeRemoved.label());
            dbEdgeToBeRemoved.remove();
        } else {
            logger.debug("RegistryDaoImpl : Deleting edge and node for edge-label: {} and vertex-label : {}", dbEdgeToBeRemoved.label(),label(dbVertexToBeDeleted));
            dbVertexToBeDeleted.remove();
            dbEdgeToBeRemoved.remove();
        }
        if(auditEnabled) {
            watch.start("RegistryDaoImpl.deleteEdgeAndNode.auditRecord");
            AuditRecord record = appContext.getBean(AuditRecord.class);
            String tailOfdbVertex = label(v).substring(label(v).lastIndexOf("/") + 1).trim();
            String auditVertexlabel = registryContext + tailOfdbVertex;
            record
                    .subject(auditVertexlabel)
                    .predicate(edgeLabel)
                    .oldObject(vertexLabel)
                    .newObject(null)
                    .record(databaseProvider);
            watch.stop("RegistryDaoImpl.deleteEdgeAndNode.auditRecord");
        }

        logger.debug("RegistryDaoImpl : Audit record created for deletion of vertex : {}", dbVertexToBeDeleted);

    }

    /**
     * Blank nodes are no longer supported. If the input data has a blank node, which is identified
     * by the node's label which starts with :_, then a random UUID is used as the label for the blank node.
     * @param label
     * @return
     */
    private String generateBlankNodeLabel(String label) {
        if(isaBlankNode(label)){
            label = String.format("%s%s", registryContext, generateRandomUUID());
        }
        return label;
    }

    private boolean isIRI(String label) {
        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
        if (urlValidator.isValid(label)) {
            return true;
        }
        return false;
    }

    public static String generateRandomUUID() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean updateEntity(Graph entityForUpdate, String rootNodeLabel, String methodOrigin)
            throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        Graph graphFromStore = databaseProvider.getGraphStore();
        TinkerGraph graphForUpdate = (TinkerGraph) entityForUpdate;
        GraphTraversalSource traversal = graphForUpdate.traversal();
        Iterator<Vertex> hasRootLabel = getVerticesIterator(graphFromStore, rootNodeLabel);
        if (!hasRootLabel.hasNext()) {
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        } else {
            if (graphFromStore.features().graph().supportsTransactions()) {
                org.apache.tinkerpop.gremlin.structure.Transaction tx = graphFromStore.tx();
                tx.onReadWrite(org.apache.tinkerpop.gremlin.structure.Transaction.READ_WRITE_BEHAVIOR.AUTO);
                watch.start("RegistryDaoImpl.updateEntity");
                addOrUpdateVerticesAndEdges(graphFromStore, traversal, rootNodeLabel, methodOrigin);
                tx.commit();
                watch.stop("RegistryDaoImpl.updateEntity");
                logger.debug("RegistryDaoImpl : Entity Updated for transactional DB with rootNodeLabel : {}", rootNodeLabel);
            }else{
                watch.start("RegistryDaoImpl.updateEntity");
                addOrUpdateVerticesAndEdges(graphFromStore, traversal, rootNodeLabel, methodOrigin);
                watch.stop("RegistryDaoImpl.updateEntity");
                logger.debug("RegistryDaoImpl : Entity Updated for non-transactional DB with rootNodeLabel : {}", rootNodeLabel);
            }
        }
        return false;
    }

    @Override
    public Graph getEntityById(String label) throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        Graph graphFromStore = databaseProvider.getGraphStore();
        Iterator<Vertex> hasLabel = getVerticesIterator(graphFromStore,label);
        ImmutableTable.Builder<Vertex,Vertex,Map<String,Object>> encDecPropertyBuilder = ImmutableTable.<Vertex,Vertex,Map<String,Object>> builder();
        Graph parsedGraph = TinkerGraph.open();
        if (!hasLabel.hasNext()) {
            logger.info("Record not found  for label : {}", label);
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        } else {
            logger.info("Record exists for label : {}", label);
            Vertex subject = hasLabel.next();
            Vertex newSubject = parsedGraph.addVertex(generateNamespacedLabel(String.valueOf(subject.id())));
            copyProperties(subject, newSubject,"read", encDecPropertyBuilder);
            watch.start("RegistryDaoImpl.getEntityById.extractGraphFromVertex");
            extractGraphFromVertex(parsedGraph,newSubject,subject, encDecPropertyBuilder);
            watch.stop("RegistryDaoImpl.getEntityById.extractGraphFromVertex");
            Table<Vertex,Vertex,Map<String,Object>>  encDecPropertyTable = encDecPropertyBuilder.build();
            long timestamp = System.currentTimeMillis();
            if(encDecPropertyTable.size()>0){
                watch.start("RegistryDaoImpl.getEntityById.updateEncryptedDecryptedProperties");
                updateEncryptedDecryptedProperties(encDecPropertyTable, "read");
                watch.stop("RegistryDaoImpl.getEntityById.updateEncryptedDecryptedProperties");
            }
        }
        return parsedGraph;
    }

    private void copyProperties(Vertex subject, Vertex newSubject, String methodOrigin, ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder)
            throws NoSuchElementException, EncryptionException, AuditFailedException {
        HashMap<String, HashMap<String, String>> propertyMetaPropertyMap = new HashMap<String, HashMap<String, String>>();
        Iterator<VertexProperty<Object>> iter = subject.properties();
        Map<String, Object> propertyMap = new HashMap<String, Object>();

        while (iter.hasNext()) {
            VertexProperty<Object> property = iter.next();
            if (!isInternalProperty(property)) {
                String tailOfPropertyKey = property.key().substring(property.key().lastIndexOf("/") + 1).trim();
                boolean existingEncyptedPropertyKey = tailOfPropertyKey
                        .substring(0, Math.min(tailOfPropertyKey.length(), 9)).equalsIgnoreCase("encrypted");
                if ((methodOrigin.equalsIgnoreCase("create") || methodOrigin.equalsIgnoreCase("update")) && existingEncyptedPropertyKey) {
                    property.remove();
                }
                if ((methodOrigin.equalsIgnoreCase("create") || methodOrigin.equalsIgnoreCase("update")) && schemaConfigurator.isPrivate(property.key()) && !existingEncyptedPropertyKey) {
                    propertyMap.put(property.key(), property.value());
                } else if (methodOrigin.equalsIgnoreCase("read") && schemaConfigurator.isEncrypted(tailOfPropertyKey)) {
                    propertyMap.put(property.key(), property.value());
                    String decryptedKey = property.key().replace(tailOfPropertyKey, tailOfPropertyKey.substring(9));
                    setProperty(newSubject, decryptedKey, EMPTY_STRING, methodOrigin);
                } else if (isaMetaProperty(property.key())) {
                    buildPropertyMetaMap(propertyMetaPropertyMap, property);
                } else {
                    if (!(methodOrigin.equalsIgnoreCase("read")
                            && property.key().contains("@audit"))) {
                        setProperty(newSubject, property.key(), property.value(), methodOrigin);
                        setMetaProperty(subject, newSubject, property, methodOrigin);
                    }
                }
            }
        }
        setMetaPropertyFromMap(newSubject, propertyMetaPropertyMap);
        if (propertyMap.size() > 0) {
            encDecPropertyBuilder.put(subject, newSubject, propertyMap);
        }
    }

    private boolean isaMetaProperty(String key) {
        return key.startsWith(META);
    }

    private void setProperty(Vertex v, String key, Object newValue, String methodOrigin) throws AuditFailedException {
        if (!(methodOrigin.equalsIgnoreCase("read") && isAuditField(key))) {
            VertexProperty vp = v.property(key);
            Object oldValue = vp.isPresent() ? vp.value() : null;
            if(oldValue!=null && !methodOrigin.equalsIgnoreCase("update") && !schemaConfigurator.isSingleValued(key)){
                List valueList = new ArrayList();
                if(oldValue instanceof List){
                    valueList = (List)oldValue;
                } else{
                    String valueStr = (String)oldValue;
                    valueList.add(valueStr);
                }

                if(newValue instanceof List){
                    valueList.addAll((List)newValue);
                } else{
                    valueList.add(newValue);
                }
                newValue = valueList;
                // TODO fix multi values
                // newValue = processVertexProperty(valueList);
            }
            // v.property(key, processVertexProperty(newValue));
            if(newValue instanceof List){
                if(databaseProvider.isMultiValuedLiteralPropertySupported()){
                    v.property(key, newValue);
                } else {
                    Gson gson = new Gson();
                    String json = gson.toJson(newValue);
                    v.property(key, json);
                }
            } else {
                v.property(key, newValue);
            }
            if (!isAuditField(key) && auditEnabled) {
                if (!isaMetaProperty(key) && !Objects.equals(oldValue, newValue)) {
                    GraphTraversal<Vertex, Vertex> configTraversal =
                            v.graph().traversal().clone().V().has(T.label, Constants.GRAPH_GLOBAL_CONFIG);
                    if (configTraversal.hasNext()
                            && configTraversal.next().property(Constants.PERSISTENT_GRAPH).value().equals(true)) {

                        AuditRecord record = appContext.getBean(AuditRecord.class);
                        watch.start("RegistryDaoImpl.setProperty.auditRecord");
                        record
                                .subject(label(v))
                                .predicate(key)
                                .oldObject(oldValue)
                                .newObject(newValue)
                                .record(databaseProvider);
                        watch.stop("RegistryDaoImpl.setProperty.auditRecord");
                        logger.info("Audit record created for {}  !", label(v));
                    } else {
                        logger.debug("not auditing in the Application!");
                    }
                } else {
                    logger.debug("No change found for auditing !");
                }
            }
        }
    }

    private boolean isAuditField(String fieldValue) {
        return fieldValue.endsWith(Constants.AuditProperties.createdBy.name())
                || fieldValue.endsWith(Constants.AuditProperties.createdAt.name())
                || fieldValue.endsWith(Constants.AuditProperties.lastUpdatedBy.name())
                || fieldValue.endsWith(Constants.AuditProperties.lastUpdatedAt.name());
    }

    private Object processVertexProperty(Object propertyValue) {
        if (propertyValue instanceof List) {
            List<Object> temp = ((List) propertyValue);
            String[] strings = temp.toArray(new String[0]);
            return strings;
        } else {
            return propertyValue;
        }
    }

    private void setMetaPropertyFromMap(Vertex newSubject, HashMap<String, HashMap<String, String>> propertyMetaPropertyMap) {
        Iterator propertyIter = propertyMetaPropertyMap.entrySet().iterator();
        while(propertyIter.hasNext()){
            Map.Entry pair = (Map.Entry)propertyIter.next();
            logger.info("PROPERTY <- " + pair.getKey());
            HashMap<String,String> _mpmap = (HashMap<String, String>) pair.getValue();
            Iterator _mpmapIter = _mpmap.entrySet().iterator();
            while(_mpmapIter.hasNext()) {
                Map.Entry _pair = (Map.Entry)_mpmapIter.next();
                logger.info("META PROPERTY <- " + _pair.getKey() + "|" + _pair.getValue() + "|" + newSubject.property(pair.getKey().toString()).isPresent());
                newSubject.property(pair.getKey().toString()).property(_pair.getKey().toString(),_pair.getValue().toString());
            }
        }
    }

    private void setMetaProperty(Vertex subject, Vertex newSubject, VertexProperty<Object> property, String methodOrigin) throws AuditFailedException {
        if (subject.graph().features().vertex().supportsMetaProperties()) {
            Iterator<Property<Object>> metaPropertyIter = property.properties();
            while (metaPropertyIter.hasNext()) {
                Property<Object> metaProperty = metaPropertyIter.next();
                if (newSubject.graph().features().vertex().supportsMetaProperties()) {
                    newSubject.property(property.key()).property(metaProperty.key(), metaProperty.value());
                } else {
                    String metaKey = getMetaKey(property, metaProperty);
                    setProperty(newSubject, metaKey, metaProperty.value(), methodOrigin);
                }
            }
        }
    }

    private String getMetaKey(VertexProperty<Object> property, Property<Object> metaProperty) {
        return META + property.key() + "." + metaProperty.key();
    }

    private void buildPropertyMetaMap(HashMap<String, HashMap<String, String>> propertyMetaPropertyMap, VertexProperty<Object> property) {
        HashMap<String, String> metaPropertyMap;
        logger.debug("RegistryDaoImpl : Meta Property: " + property);
        Pattern pattern = Pattern.compile("meta\\.(.*)\\.(.*)");
        Matcher match = pattern.matcher(property.key().toString());
        if (match.find()) {
            String _property = match.group(1);
            String _meta_property = match.group(2);
            logger.debug("RegistryDaoImpl : Matched meta property " + match.group(1) + " " + match.group(2));
            if (propertyMetaPropertyMap.containsKey(property.key())) {
                logger.debug("RegistryDaoImpl : Found in propertyMetaPropertyMap");
                metaPropertyMap = propertyMetaPropertyMap.get(property.key());
            } else {
                logger.debug("RegistryDaoImpl : Creating metaPropertyMap in propertyMetaPropertyMap");
                metaPropertyMap = new HashMap<>();
                propertyMetaPropertyMap.put(_property, metaPropertyMap);
            }
            metaPropertyMap.put(_meta_property, property.value().toString());
        }
    }

    private boolean isaBlankNode(String label) {
        return !isIRI(label);
    }

    private String generateNamespacedLabel(String label) {
        if(label.startsWith("http"))
            return label;
	    return String.format("%s%s", registryContext, label);
    }

    private boolean isInternalProperty(VertexProperty<Object> property) {
	    boolean internalProperty = false;
	    if(property.key().startsWith(INTERNAL_PROPERTY_PREFIX))
	        internalProperty = true;
	    return internalProperty;
    }

	private void extractGraphFromVertex(Graph parsedGraph,Vertex parsedGraphSubject,Vertex s, ImmutableTable.Builder<Vertex,Vertex,Map<String,Object>> encDecPropertyBuilder)
			throws NoSuchElementException, EncryptionException, AuditFailedException {
		Iterator<Edge> edgeIter = s.edges(Direction.OUT);
		Edge edge;
		Stack<Vertex> vStack = new Stack<Vertex>();
		Stack<Vertex> parsedVStack = new Stack<Vertex>();
		while(edgeIter.hasNext()){
			edge = edgeIter.next();
			Vertex o = edge.inVertex();
			Vertex newo = parsedGraph.addVertex(label(o));
//            newo.property(internalPropertyKey("id"),o.label());
			copyProperties(o, newo,"read", encDecPropertyBuilder);
			parsedGraphSubject.addEdge(edge.label(), newo);
			vStack.push(o);
			parsedVStack.push(newo);
		}
		Iterator<Vertex> vIterator = vStack.iterator();
		Iterator<Vertex> parsedVIterator = parsedVStack.iterator();
		while(vIterator.hasNext()){
			s = vIterator.next();
			parsedGraphSubject = parsedVIterator.next();
			extractGraphFromVertex(parsedGraph,parsedGraphSubject,s, encDecPropertyBuilder);
		}
	}

    private void updateEncryptedDecryptedProperties(Table<Vertex, Vertex, Map<String, Object>> encDecPropertyTable, String methodOrigin) throws EncryptionException, AuditFailedException {
        Map<String, Object> propertyMap = new HashMap<>();
        encDecPropertyTable.values().forEach(map -> propertyMap.putAll(map));
        Map<String, Object> listPropertyMap = new HashMap<String, Object>();
        Map<String, Object> encDecListPropertyMap = new HashMap<>();
        propertyMap.forEach((k, v) -> {
            if (v instanceof List) {
                listPropertyMap.put(k, v);
            }
        });
        logger.debug("Private property count : {}", listPropertyMap.size());
        listPropertyMap.forEach((k, v) -> propertyMap.remove(k));
        Map<String, Object> encDecMap = new HashMap<>();

        if (methodOrigin.equalsIgnoreCase("create") || methodOrigin.equalsIgnoreCase("update")) {
            encDecMap = encryptionService.encrypt(propertyMap);
        } else {
            encDecMap = encryptionService.decrypt(propertyMap);
        }

        if (listPropertyMap.size() > 0) {
            encDecListPropertyMap = updateEncDecListMap(listPropertyMap, methodOrigin);
            encDecMap.putAll(encDecListPropertyMap);
        }

        setEncDecMap(encDecMap, encDecPropertyTable);
        setEncryptedDecryptedProperty(encDecPropertyTable, methodOrigin);

    }

    private Map<String,Object> updateEncDecListMap(Map<String, Object> listPropertyMap, String methodOrigin) throws EncryptionException{
        Map<String,Object> encDecListPropertyMap = new HashMap<String,Object>();
        for(Map.Entry<String, Object> entry: listPropertyMap.entrySet()){
            String k = entry.getKey();
            Object v = entry.getValue();
            List values = (List)v;
            List encValues = new ArrayList();
            for(Object listV : values){
                String encDecValue = null;
                if(methodOrigin.equalsIgnoreCase("create") || methodOrigin.equalsIgnoreCase("update")){
                    encDecValue = encryptionService.encrypt(listV);
                }else{
                    encDecValue = encryptionService.decrypt(listV);
                }
                encValues.add(encDecValue);
            }
            encDecListPropertyMap.put(k, encValues);
        }
        return encDecListPropertyMap;
    }

    private void setEncDecMap(Map<String,Object> encryptedMap, Table<Vertex,Vertex,Map<String,Object>> encDecPropertyTable){
        for(Map.Entry<String, Object> entry : encryptedMap.entrySet()){
            encDecPropertyTable.values().forEach(map -> { if(map.containsKey(entry.getKey())){
                map.put(entry.getKey(), entry.getValue());
            }
            });
        }
    }

    private void setEncryptedDecryptedProperty(Table<Vertex,Vertex,Map<String,Object>> encDecPropertyTable, String methodOrigin) throws AuditFailedException{
        for(Table.Cell<Vertex,Vertex,Map<String,Object>> cell: encDecPropertyTable.cellSet()){
            Vertex subject = cell.getRowKey();
            Vertex newSubject = cell.getColumnKey();
            for(Map.Entry<String, Object> entry : cell.getValue().entrySet()){
                Object entryValue = entry.getValue();
                String entryKey = entry.getKey();
                String tailOfPropertyKey = entryKey.substring(entryKey.lastIndexOf("/") + 1).trim();
                String newKey = null;
                if (methodOrigin.equalsIgnoreCase("create") || methodOrigin.equalsIgnoreCase("update")){
                    newKey = entryKey.replace(tailOfPropertyKey, "encrypted" + tailOfPropertyKey);
                    setProperty(newSubject, newKey, entryValue, methodOrigin);
                    VertexProperty property = subject.property(entryKey);
                    setMetaProperty(subject, newSubject, property, methodOrigin);
                } else if(methodOrigin.equalsIgnoreCase("read")){
                    newKey = entryKey.replace(tailOfPropertyKey, tailOfPropertyKey.substring(9));
                    Iterator<Property<Object>> propIter = newSubject.property(newKey).properties();
                    setProperty(newSubject, newKey, entryValue, methodOrigin);
                    while(propIter.hasNext()){
                        Property<Object> propertyObj = propIter.next();
                        newSubject.property(newKey).property(propertyObj.key(), propertyObj.value());
                    }
                }

            }
        }
    }

    public void setAuditInfo(Vertex v, boolean isNew){
        if(authenticationEnabled){
            String userId = ((AuthInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getSub();
            long timestamp = new Date().getTime();
            if(isNew){
                v.property(registryContext+Constants.AuditProperties.createdBy.name(),userId);
                v.property(registryContext+Constants.AuditProperties.createdAt.name(),timestamp);
            }
            v.property(registryContext+Constants.AuditProperties.lastUpdatedBy.name(),userId);
            v.property(registryContext+Constants.AuditProperties.lastUpdatedAt.name(),timestamp);
        }
    }

    private String internalPropertyKey(String key) {
        return INTERNAL_PROPERTY_PREFIX+key;
    }

    private Iterator<Vertex> getVerticesIterator(Graph dbGraph, String label) {
        if(isaBlankNode(label)){
            label = generateNamespacedLabel(label);
        }
        String longLabel = extractID(label);
        List<String> matchedIDs = databaseProvider.getIDsFromLabel(label);
        if(!matchedIDs.isEmpty()){
            if(matchedIDs.contains(longLabel)){
                dbGraph.vertices(longLabel);
            } else {
                return getVerticesIterator(dbGraph, generateNamespacedLabel(matchedIDs.get(0)));
            }
        }

        return dbGraph.vertices(longLabel);
    }

    private String extractID(String URI) {
        String longLabel = IMPOSSIBLE_LABEL;
        Pattern r = Pattern.compile(".*\\/(\\d+)");
        Matcher m = r.matcher(URI);
        if (m.find( )) {
            longLabel = m.group(1);
        }
        return longLabel;
    }

    private boolean doesExist(String rootNodeLabel, Graph dbGraph) {
        if(isaBlankNode(rootNodeLabel))
            return false;
        try {
            Iterator iter = getVerticesIterator(dbGraph,rootNodeLabel);
            return iter.hasNext();
        } catch(Exception e){
            return false;
        }
    }

    private Optional<Edge> getFirstMatchingEdgeAndVertex(List<Edge> dbEdgesForVertex, Vertex ver, String edgeLabel) {
        return dbEdgesForVertex.stream().filter(ed -> {
            return ed.label().equalsIgnoreCase(edgeLabel) && label(ed.inVertex()).equalsIgnoreCase(label(ver));
        }).findFirst();
    }

}

