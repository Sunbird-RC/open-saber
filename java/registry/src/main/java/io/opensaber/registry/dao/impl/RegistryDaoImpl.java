package io.opensaber.registry.dao.impl;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import io.opensaber.pojos.OpenSaberInstrumentation;
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
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Autowired
    private DatabaseProvider databaseProvider;

    @Autowired
    private EncryptionService encryptionService;

    @Value("${registry.context.base}")
    private String registryContext;

    @Value("${encryption.enabled}")
    private boolean encryptionEnabled;

    @Autowired
    SchemaConfigurator schemaConfigurator;

    @Autowired
    ApplicationContext appContext;

    @Value("${audit.enabled}")
    private boolean auditEnabled;

    @Value("${authentication.enabled}")
    private boolean authenticationEnabled;

    @Value("${database.persistence.enabled}")
    private boolean persistenceEnabled;

    @Autowired
    private OpenSaberInstrumentation watch;

    @Autowired
    private UrlValidator urlValidator;

    private Graph graphFromStore = null;
    private GraphTraversalSource dbGraphTraversalSource = null;
    private boolean isTransactionSupported = false;


    @Override
    public List getEntityList() {
        // TODO Auto-generated method stub
        return null;
    }

    private void init() {
        if (null == graphFromStore) {
            graphFromStore = databaseProvider.getGraphStore();
            dbGraphTraversalSource = graphFromStore.traversal();
            isTransactionSupported = graphFromStore.features().graph().supportsTransactions();
        }
    }


    @Override
    public String addEntity(Graph entity, String label, String rootNodeLabel, String property) throws DuplicateRecordException, RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        if (!persistenceEnabled) {
            return "R-Label";
        }
        init();

        if (rootNodeLabel != null && property != null && !dbGraphTraversalSource.V().hasLabel(rootNodeLabel).hasNext()) {
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        } else if (dbGraphTraversalSource.V().hasLabel(label).hasNext()) {
            throw new DuplicateRecordException(Constants.DUPLICATE_RECORD_MESSAGE);
        }

        TinkerGraph graph = (TinkerGraph) entity;
        GraphTraversalSource traversal = graph.traversal();
        Transaction tx = startTransaction();

        watch.start("RegistryDaoImpl.addOrUpdateVerticesAndEdges");
        label = addOrUpdateVerticesAndEdges(dbGraphTraversalSource, traversal, label, "create");
        watch.stop("RegistryDaoImpl.addOrUpdateVerticesAndEdges");
        connectRootToEntity(dbGraphTraversalSource, rootNodeLabel, label, property);

        commitTransaction(tx);
        logger.info("Successfully created entity with label " + label);
        return label;
    }

    /**
     * Starts a transaction if database supports it.
     * @return
     */
    private Transaction startTransaction() {
        Transaction tx = null;
        if (isTransactionSupported) {
            tx = graphFromStore.tx();
            tx.onReadWrite(Transaction.READ_WRITE_BEHAVIOR.AUTO);
        }
        return tx;
    }

    /**
     * Commits the changes on a transaction.
     * @param tx - pass null for a no-op
     */
    private void commitTransaction(Transaction tx) {
        if (isTransactionSupported && tx != null) {
            tx.commit();
        }
    }


    /**
     * This method is used to connect the new child entity to the root. Adds an edge with the property
     * between the root and entity.
     * @param dbTraversalSource
     * @param rootLabel
     * @param label
     * @param property
     * @throws RecordNotFoundException
     * @throws NoSuchElementException
     * @throws EncryptionException
     * @throws AuditFailedException
     */
    private void connectRootToEntity(GraphTraversalSource dbTraversalSource, String rootLabel, String label, String property) throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        if (rootLabel == null || property == null) {
            // no connections needed because the entity is itself a root.
            return;
        }

        GraphTraversal<Vertex, Vertex> rootGts = dbTraversalSource.V().hasLabel(rootLabel);
        GraphTraversal<Vertex, Vertex> entityGts = dbTraversalSource.V().hasLabel(label);
        Vertex rootVertex = rootGts.next();
        Vertex entityVertex = entityGts.next();
        rootVertex.addEdge(property, entityVertex);

        if (auditEnabled) {
            watch.start("RegistryDaoImpl.connectRootToEntity.auditRecord");
            AuditRecord record = appContext.getBean(AuditRecord.class);
            record
                    .subject(rootVertex.label())
                    .predicate(property)
                    .oldObject(null)
                    .newObject(entityVertex.label())
                    .record(databaseProvider);
            watch.stop("RegistryDaoImpl.connectRootToEntity.auditRecord");
        }

        logger.debug("RegistryDaoImpl : Audit record generated of connectRootToEntity for rootLabel : {}, label	:	{}, property :	{}", rootLabel, label, property);
    }


    /**
     *
     * @param v
     * @param existingVertex
     * @param dbTraversalSource
     * @param encDecPropertyBuilder - to be removed
     * @param methodOrigin - to be removed
     * @throws AuditFailedException
     * @throws EncryptionException
     * @throws RecordNotFoundException
     */
    //TODO: Refactor - remove methodOrigin and encDecPropertyBuilder
    private void doAuditedUpdateVertex(Vertex v, Vertex existingVertex, GraphTraversalSource dbTraversalSource,
                                       ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder, String methodOrigin)
            throws AuditFailedException, EncryptionException, RecordNotFoundException {
        setAuditInfo(v, false);
        copyProperties(v, existingVertex, methodOrigin, encDecPropertyBuilder);
        // watch.start("RegistryDaoImpl.addOrUpdateVertexAndEdge()");
        addOrUpdateVertexAndEdge(v, existingVertex, dbTraversalSource, methodOrigin, encDecPropertyBuilder);
    }


    /**
     * This method creates the root node of the entity if it already isn't present in the graph store
     * or updates the properties of the root node or adds new properties if the properties are not already
     * present in the node.
     *
     * @param dbTraversalSource
     * @param entitySource
     * @param rootLabel
     * @throws EncryptionException
     * @throws NoSuchElementException
     */
    private String addOrUpdateVerticesAndEdges(GraphTraversalSource dbTraversalSource,
                                               GraphTraversalSource entitySource, String rootLabel, String methodOrigin)
            throws NoSuchElementException, EncryptionException, AuditFailedException, RecordNotFoundException {

        GraphTraversal<Vertex, Vertex> gts = entitySource.clone().V().hasLabel(rootLabel);
        String label = rootLabel;
        GraphTraversal<Vertex, Vertex> traversalRoot = dbTraversalSource.clone().V().hasLabel(rootLabel);

        while (gts.hasNext()) {
            Vertex v = gts.next();
            ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder = ImmutableTable.<Vertex, Vertex, Map<String, Object>>builder();

            if (traversalRoot.hasNext()) {
                logger.info(String.format("Root node label {} already exists. Updating properties for the root node.", rootLabel));
                Vertex existingVertex = traversalRoot.next();
                if (methodOrigin.equalsIgnoreCase("update") &&
                        existingVertex.property(registryContext + "@status").isPresent() &&
                        Constants.STATUS_INACTIVE.equals(existingVertex.value(registryContext + "@status"))) {
                    throw new UnsupportedOperationException(Constants.ENTITY_NOT_FOUND);
                }
                doAuditedUpdateVertex(v, existingVertex, dbTraversalSource, encDecPropertyBuilder, methodOrigin);
            } else {
                if (methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN)) {
                    throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
                }
                label = generateBlankNodeLabel(rootLabel);
                logger.info(String.format("Creating entity with label {}", rootLabel));
                Vertex newVertex = dbTraversalSource.clone().addV(label).next();
                doAuditedUpdateVertex(v, newVertex, dbTraversalSource, encDecPropertyBuilder, methodOrigin);
            }
            Table<Vertex, Vertex, Map<String, Object>> encDecPropertyTable = encDecPropertyBuilder.build();
            if (encDecPropertyTable.size() > 0) {
                updateEncryptedDecryptedProperties(encDecPropertyTable, methodOrigin);
            }
        }

        return label;
    }

    private Edge addEdge(String methodOrigin, Vertex dbVertex, Vertex inVertex,
                         String edgeLabel, Vertex newOrOldVertex, boolean isNew,
                         ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder)
            throws AuditFailedException, EncryptionException
    {
        // TODO: Refactor - remove the encDecPropertyBuilder param from here.
        setAuditInfo(inVertex, isNew);

        logger.info(String.format("Vertex with label {} already exists. Updating properties for the vertex", newOrOldVertex.label()));
        copyProperties(inVertex, newOrOldVertex, methodOrigin, encDecPropertyBuilder);

        Edge edgeAdded = dbVertex.addEdge(edgeLabel, newOrOldVertex);

        if (auditEnabled) {
            watch.start("RegistryDaoImpl.addOrUpdateVertexAndEdge.auditRecord");
            AuditRecord record = appContext.getBean(AuditRecord.class);
            record
                    .subject(dbVertex.label())
                    .predicate(edgeLabel)
                    .oldObject(null)
                    .newObject(newOrOldVertex.label())
                    .record(databaseProvider);
            watch.stop("RegistryDaoImpl.addOrUpdateVertexAndEdge.auditRecord");
        }
        logger.debug("RegistryDaoImpl : Audit record created with label : {}  ", dbVertex.label());
        return edgeAdded;
    }



    /**
     * This method takes the root node of an entity and then recursively creates or updates child vertices
     * and edges.
     *
     * @param v
     * @param dbVertex
     * @param dbGraph
     * @throws EncryptionException
     * @throws NoSuchElementException
     */
    private void addOrUpdateVertexAndEdge(Vertex v, Vertex dbVertex, GraphTraversalSource dbGraph, String methodOrigin,
                                          ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder)
            throws NoSuchElementException, EncryptionException, AuditFailedException, RecordNotFoundException
    {
        Iterator<Edge> outEdgesOfV = v.edges(Direction.OUT);
        Stack<Pair<Vertex, Vertex>> parsedVertices = new Stack<>();
        List<Edge> dbEdgesForVertex = ImmutableList.copyOf(dbVertex.edges(Direction.OUT));
        List<Edge> edgeVertexMatchList = new ArrayList<Edge>();

        outEdgesOfV.forEachRemaining( outEdge -> {
            Vertex ver = outEdge.inVertex();
            String edgeLabel = outEdge.label();
            Optional<Edge> edgeVertexAlreadyExists =
                    dbEdgesForVertex.stream().filter(ed -> ed.label().equalsIgnoreCase(edgeLabel) &&
                            ed.inVertex().label().equalsIgnoreCase(ver.label())).findFirst();
            if (edgeVertexAlreadyExists.isPresent()) {
                edgeVertexMatchList.add(edgeVertexAlreadyExists.get());
            }
        });

        logger.debug("RegistryDaoImpl : Matching list size:" + edgeVertexMatchList.size());

        // Reset before reuse.
        outEdgesOfV = v.edges(Direction.OUT);
        while (outEdgesOfV.hasNext()) {
            Edge outEdge = outEdgesOfV.next();
            Vertex ver = outEdge.inVertex();
            String edgeLabel = outEdge.label();
            GraphTraversal<Vertex, Vertex> gt = dbGraph.clone().V().hasLabel(ver.label());

            Optional<Edge> onlyEdgeExists =
                    dbEdgesForVertex.stream().filter(ed -> ed.label().equalsIgnoreCase(edgeLabel)).findFirst();

            boolean edgeVertexExists = edgeVertexMatchList.contains(edgeLabel);
            boolean edgeExists = edgeVertexExists || onlyEdgeExists.isPresent();

            if (methodOrigin.equalsIgnoreCase("update") && edgeExists
                    || isSingleValued(outEdge)) {
                doAuditedDelete(dbVertex, outEdge, edgeVertexMatchList);
            }

            Edge edgeAdded = null;
            if (gt.hasNext()) {
                Vertex existingV = gt.next();
                if (!edgeVertexExists) {
                    edgeAdded = addEdge(methodOrigin, dbVertex, ver, edgeLabel, existingV, false, encDecPropertyBuilder);
                    parsedVertices.push(new Pair<>(ver, existingV));
                }
            } else {
                if (methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN) && !isIRI(ver.label())) {
                    throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
                }

                String label = generateBlankNodeLabel(ver.label());
                Vertex newV = dbGraph.addV(label).next();
                logger.debug(String.format("RegistryDaoImpl : Adding edge with label {} for the vertex label {}.", outEdge.label(), newV.label()));

                edgeAdded = addEdge(methodOrigin, dbVertex, ver, edgeLabel, newV, true, encDecPropertyBuilder);
                parsedVertices.push(new Pair<>(ver, newV));
            }
            edgeVertexMatchList.add(edgeAdded);
        }


        for (Pair<Vertex, Vertex> pv : parsedVertices) {
            addOrUpdateVertexAndEdge(pv.getValue0(), pv.getValue1(), dbGraph, methodOrigin, encDecPropertyBuilder);
        }
    }


    /**
     * Deletes the edge and node.
     * @param dbSourceVertex
     * @param e
     * @param edgeVertexMatchList
     * @throws AuditFailedException
     */
    private void doAuditedDelete(Vertex dbSourceVertex, Edge e, List<Edge> edgeVertexMatchList)
            throws AuditFailedException {
        // TODO: Refactor - can this be a remove than an iteration.
        Iterator<Edge> edgeIter = dbSourceVertex.edges(Direction.OUT, e.label());
        while (edgeIter.hasNext()) {
            Edge edge = edgeIter.next();
            Optional<Edge> existingEdgeVertex =
                    edgeVertexMatchList.stream().filter(ed -> ed.label().equalsIgnoreCase(edge.label()) && ed.inVertex().label().equalsIgnoreCase(edge.inVertex().label())).findFirst();
            if (!existingEdgeVertex.isPresent()) {
                deleteEdgeAndNode(edge);

                if (auditEnabled) {
                    watch.start("RegistryDaoImpl.deleteEdgeAndNode.auditRecord");
                    AuditRecord record = appContext.getBean(AuditRecord.class);
                    String tailOfdbVertex = dbSourceVertex.label().substring(dbSourceVertex.label().lastIndexOf("/") + 1).trim();
                    String auditVertexlabel = registryContext + tailOfdbVertex;
                    record
                            .subject(auditVertexlabel)
                            .predicate(edge.label())
                            .oldObject(edge.inVertex().label())
                            .newObject(null)
                            .record(databaseProvider);
                    watch.stop("RegistryDaoImpl.deleteEdgeAndNode.auditRecord");
                }
            }
        }
    }

    /**
     * Returns true if the edge is single value, false otherwise.
     * @param e
     * @return
     */
    private boolean isSingleValued(Edge e) {
        return schemaConfigurator.isSingleValued(e.label());
    }

    /**
     * This method deletes the edge and node if the node is an orphan node and if not, deletes only the edge
     *
     * @param dbEdgeToBeRemoved
     */
    private void deleteEdgeAndNode(Edge dbEdgeToBeRemoved) {
        logger.info("Deleting edge and node of label : {}", dbEdgeToBeRemoved.label());

        Vertex dbVertexToBeDeleted = dbEdgeToBeRemoved.inVertex();
        Iterator<Edge> inEdgeIter = dbVertexToBeDeleted.edges(Direction.IN);
        Iterator<Edge> outEdgeIter = dbVertexToBeDeleted.edges(Direction.OUT);

        if (!(inEdgeIter.hasNext() && IteratorUtils.count(inEdgeIter) > 1) || outEdgeIter.hasNext()) {
            dbVertexToBeDeleted.remove();
        }

        dbEdgeToBeRemoved.remove();
        logger.debug("RegistryDaoImpl : Audit record created for deletion of vertex : {}", dbVertexToBeDeleted);
    }

    /**
     * Blank nodes are no longer supported. If the input data has a blank node, which is identified
     * by the node's label which starts with :_, then a random UUID is used as the label for the blank node.
     *
     * @param label
     * @return
     */
    private String generateBlankNodeLabel(String label) {
        if (!isIRI(label)) {
            label = String.format("%s%s", registryContext, generateRandomUUID());
        }
        return label;
    }

    /**
     * Takes in a label and returns true if it of type http
     * Allows local URLs too.
     * @param label
     * @return
     */
    private boolean isIRI(String label) {
        return urlValidator.isValid(label);
    }

    /**
     * Generates a 4 part UUID.
     * @return
     */
    public static String generateRandomUUID() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean updateEntity(Graph entityForUpdate, String rootNodeLabel, String methodOrigin)
            throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        init();

        TinkerGraph graphForUpdate = (TinkerGraph) entityForUpdate;
        GraphTraversalSource traversal = graphForUpdate.traversal();
        // Check if the root node being updated exists in the database
        GraphTraversal<Vertex, Vertex> hasRootLabel = dbGraphTraversalSource.clone().V().hasLabel(rootNodeLabel);
        if (!hasRootLabel.hasNext()) {
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        } else {
            Transaction tx = startTransaction();
            watch.start("RegistryDaoImpl.updateEntity");
            addOrUpdateVerticesAndEdges(dbGraphTraversalSource, traversal, rootNodeLabel, methodOrigin);
            watch.stop("RegistryDaoImpl.updateEntity");
            logger.debug("RegistryDaoImpl : Entity Updated with rootNodeLabel : {}", rootNodeLabel);
            commitTransaction(tx);
        }
        return false;
    }


    @Override
    public Graph getEntityById(String label) throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
        init();
        GraphTraversal<Vertex, Vertex> hasLabel = dbGraphTraversalSource.clone().V().hasLabel(label);
        ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder = ImmutableTable.<Vertex, Vertex, Map<String, Object>>builder();
        Graph parsedGraph = TinkerGraph.open();
        if (!hasLabel.hasNext()) {
            logger.info("Record not found  for label : {}", label);
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        } else {
            logger.info("Record exists for label : {}", label);
            Vertex subject = hasLabel.next();
            if (subject.property(registryContext+Constants.STATUS_KEYWORD).isPresent() && Constants.STATUS_INACTIVE.equals(subject.value(registryContext + Constants.STATUS_KEYWORD))){
                throw new UnsupportedOperationException(Constants.READ_ON_DELETE_ENTITY_NOT_SUPPORTED);
            }
            Vertex newSubject = parsedGraph.addVertex(subject.label());
            copyProperties(subject, newSubject, Constants.READ_METHOD_ORIGIN, encDecPropertyBuilder);
            watch.start("RegistryDaoImpl.getEntityById.extractGraphFromVertex");
            extractGraphFromVertex(parsedGraph, newSubject, subject, encDecPropertyBuilder, Constants.READ_METHOD_ORIGIN);
            watch.stop("RegistryDaoImpl.getEntityById.extractGraphFromVertex");
            Table<Vertex, Vertex, Map<String, Object>> encDecPropertyTable = encDecPropertyBuilder.build();
            if (encDecPropertyTable.size() > 0) {
                watch.start("RegistryDaoImpl.getEntityById.updateEncryptedDecryptedProperties");
                updateEncryptedDecryptedProperties(encDecPropertyTable, Constants.READ_METHOD_ORIGIN);
                watch.stop("RegistryDaoImpl.getEntityById.updateEncryptedDecryptedProperties");
            }
        }
        return parsedGraph;
    }


    @Override
    public Graph getEntityByVertex(Vertex vertex) throws RecordNotFoundException, NoSuchElementException, EncryptionException, AuditFailedException {
    	Graph parsedGraph = TinkerGraph.open();
    	Vertex newSubject = parsedGraph.addVertex(vertex.label());
    	watch.start("RegistryDaoImpl.getEntityByVertex.copyProperties");
    	copyProperties(vertex, newSubject, Constants.SEARCH_METHOD_ORIGIN, null);
    	watch.stop("RegistryDaoImpl.getEntityByVertex.copyProperties");
    	watch.start("RegistryDaoImpl.getEntityByVertex.extractGraphFromVertex");
    	extractGraphFromVertex(parsedGraph, newSubject, vertex, null, Constants.SEARCH_METHOD_ORIGIN);
    	watch.stop("RegistryDaoImpl.getEntityByVertex.extractGraphFromVertex");
    	return parsedGraph;
    }

    @Override
    public boolean deleteEntityById(String idLabel) throws RecordNotFoundException {
        boolean isEntityDeleted = false;
        init();
        GraphTraversalSource traversalSource = dbGraphTraversalSource;
        GraphTraversal<Vertex, Vertex> hasLabel = traversalSource.clone().V().hasLabel(idLabel);

        if (!hasLabel.hasNext()) {
            logger.info("Record not found  for label : {}", idLabel);
            throw new RecordNotFoundException(Constants.ENTITY_NOT_FOUND);
        } else {
                Transaction tx = startTransaction();

                watch.start("RegistryDaoImpl.deleteEntityById");
                logger.debug("Record exists for label : {}", idLabel);
                Vertex s = hasLabel.next();
                if (s.property(registryContext+Constants.STATUS_KEYWORD).isPresent() && Constants.STATUS_INACTIVE.equals(s.value(registryContext+Constants.STATUS_KEYWORD))){
                    throw new UnsupportedOperationException(Constants.DELETE_UNSUPPORTED_OPERATION_ON_ENTITY);
                } else {
                    isEntityDeleted = deleteVertexWithInEdge(s);
                }

                commitTransaction(tx);
                watch.stop("RegistryDaoImpl.deleteEntityById");
                logger.debug("RegistryDaoImpl : Entity deleted for transactional DB with rootNodeLabel : {}", idLabel);
        }
        return isEntityDeleted;
    }


    private boolean deleteVertexWithInEdge(Vertex s) {
        Edge edge;
        Stack<Vertex> vStack = new Stack<Vertex>();
        Iterator<Edge> inEdgeIter = s.edges(Direction.IN);
        while (inEdgeIter.hasNext()) {
            edge = inEdgeIter.next();
            Vertex o = edge.outVertex();
            if (!vStack.contains(o)) {
                vStack.push(o);
                if (o.property(registryContext + Constants.STATUS_KEYWORD).isPresent() && Constants.STATUS_ACTIVE.equals(o.value(registryContext + Constants.STATUS_KEYWORD))) {
                    return false;
                }
            }
        }
        s.property(registryContext+Constants.STATUS_KEYWORD,Constants.STATUS_INACTIVE);
        return true;
    }

    private void copyProperties(Vertex subject, Vertex newSubject, String methodOrigin, ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder)
            throws NoSuchElementException, EncryptionException, AuditFailedException {
        HashMap<String, HashMap<String, String>> propertyMetaPropertyMap = new HashMap<String, HashMap<String, String>>();
        if(methodOrigin.equalsIgnoreCase(Constants.CREATE_METHOD_ORIGIN)) {
            subject.property(registryContext + Constants.STATUS_KEYWORD, Constants.STATUS_ACTIVE);
        }
        Iterator<VertexProperty<Object>> iter = subject.properties();
        Map<String, Object> propertyMap = new HashMap<String, Object>();

        while (iter.hasNext()) {
            VertexProperty<Object> property = iter.next();
            String tailOfPropertyKey = property.key().substring(property.key().lastIndexOf("/") + 1).trim();
            boolean existingEncyptedPropertyKey = schemaConfigurator.isEncrypted(tailOfPropertyKey);
            if ((methodOrigin.equalsIgnoreCase(Constants.CREATE_METHOD_ORIGIN) || methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN)) && existingEncyptedPropertyKey) {
                property.remove();
            }
            if ((methodOrigin.equalsIgnoreCase(Constants.CREATE_METHOD_ORIGIN) || methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN)) && schemaConfigurator.isPrivate(property.key())
            		&& encryptionEnabled && !existingEncyptedPropertyKey) {
                propertyMap.put(property.key(), property.value());
            } else if (methodOrigin.equalsIgnoreCase(Constants.READ_METHOD_ORIGIN) && existingEncyptedPropertyKey && encryptionEnabled) {
                propertyMap.put(property.key(), property.value());
                String decryptedKey = property.key().replace(tailOfPropertyKey, tailOfPropertyKey.substring(9));
                setProperty(newSubject, decryptedKey, EMPTY_STRING, methodOrigin);
            } else if (isaMetaProperty(property.key())) {
                buildPropertyMetaMap(propertyMetaPropertyMap, property);
            } else if ((!(methodOrigin.equalsIgnoreCase(Constants.READ_METHOD_ORIGIN)
                    && (property.key().contains(Constants.AUDIT_KEYWORD) || property.key().contains(Constants.STATUS_KEYWORD))) && !methodOrigin.equalsIgnoreCase(Constants.SEARCH_METHOD_ORIGIN))
            		|| (methodOrigin.equalsIgnoreCase(Constants.SEARCH_METHOD_ORIGIN) && !existingEncyptedPropertyKey && !property.key().contains(Constants.AUDIT_KEYWORD)
            				&& !property.key().contains(Constants.STATUS_KEYWORD))) {
                    setProperty(newSubject, property.key(), property.value(), methodOrigin);
                    setMetaProperty(subject, newSubject, property, methodOrigin);
            }
        }
        setMetaPropertyFromMap(newSubject, propertyMetaPropertyMap);
        if (propertyMap.size() > 0 && encDecPropertyBuilder != null) {
            encDecPropertyBuilder.put(subject, newSubject, propertyMap);
        }

    }

    private boolean isaMetaProperty(String key) {
        return key.startsWith(META);
    }

    private void setProperty(Vertex v, String key, Object newValue, String methodOrigin) throws AuditFailedException {
        if (!((methodOrigin.equalsIgnoreCase(Constants.SEARCH_METHOD_ORIGIN) || methodOrigin.equalsIgnoreCase(Constants.READ_METHOD_ORIGIN)) && isAuditField(key))) {
            VertexProperty vp = v.property(key);
            Object oldValue = vp.isPresent() ? vp.value() : null;
            if (oldValue != null && !methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN) && !schemaConfigurator.isSingleValued(key)) {
                List valueList = new ArrayList();
                if (oldValue instanceof List) {
                    valueList = (List) oldValue;
                } else {
                    String valueStr = (String) oldValue;
                    valueList.add(valueStr);
                }

                if (newValue instanceof List) {
                    valueList.addAll((List) newValue);
                } else {
                    valueList.add(newValue);
                }
                // newValue = valueList;
                newValue = processVertexProperty(valueList);
            }
            v.property(key, processVertexProperty(newValue));
            if (!isAuditField(key) && auditEnabled) {
                if (!isaMetaProperty(key) && !Objects.equals(oldValue, newValue)) {
                    GraphTraversal<Vertex, Vertex> configTraversal =
                            v.graph().traversal().clone().V().has(T.label, Constants.GRAPH_GLOBAL_CONFIG);
                    if (configTraversal.hasNext()
                            && configTraversal.next().property(Constants.PERSISTENT_GRAPH).value().equals(true)) {

                        AuditRecord record = appContext.getBean(AuditRecord.class);
                        watch.start("RegistryDaoImpl.setProperty.auditRecord");
                        record
                                .subject(v.label())
                                .predicate(key)
                                .oldObject(oldValue)
                                .newObject(newValue)
                                .record(databaseProvider);
                        watch.stop("RegistryDaoImpl.setProperty.auditRecord");
                        logger.debug("Audit record created for {}  !", v.label());
                    } else {
                        // System.out.println("NOT AUDITING");
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
        while (propertyIter.hasNext()) {
            Map.Entry pair = (Map.Entry) propertyIter.next();
            logger.info("PROPERTY <- " + pair.getKey());
            HashMap<String, String> _mpmap = (HashMap<String, String>) pair.getValue();
            Iterator _mpmapIter = _mpmap.entrySet().iterator();
            while (_mpmapIter.hasNext()) {
                Map.Entry _pair = (Map.Entry) _mpmapIter.next();
                logger.info("META PROPERTY <- " + _pair.getKey() + "|" + _pair.getValue() + "|" + newSubject.property(pair.getKey().toString()).isPresent());
                newSubject.property(pair.getKey().toString()).property(_pair.getKey().toString(), _pair.getValue().toString());
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

    private void extractGraphFromVertex(Graph parsedGraph, Vertex parsedGraphSubject, Vertex s, ImmutableTable.Builder<Vertex, Vertex, Map<String, Object>> encDecPropertyBuilder)
            throws NoSuchElementException, EncryptionException, AuditFailedException {
        Iterator<Edge> edgeIter = s.edges(Direction.OUT);
        Edge edge;
        Stack<Vertex> vStack = new Stack<Vertex>();
        Stack<Vertex> parsedVStack = new Stack<Vertex>();
        while (edgeIter.hasNext()) {
            edge = edgeIter.next();
            Vertex o = edge.inVertex();
            Vertex newo = parsedGraph.addVertex(o.label());
            copyProperties(o, newo, "read", encDecPropertyBuilder);
            parsedGraphSubject.addEdge(edge.label(), newo);
            vStack.push(o);
            parsedVStack.push(newo);
        }
        Iterator<Vertex> vIterator = vStack.iterator();
        Iterator<Vertex> parsedVIterator = parsedVStack.iterator();
        while (vIterator.hasNext()) {
            s = vIterator.next();
            parsedGraphSubject = parsedVIterator.next();
            extractGraphFromVertex(parsedGraph, parsedGraphSubject, s, encDecPropertyBuilder);
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

        if (methodOrigin.equalsIgnoreCase(Constants.CREATE_METHOD_ORIGIN) || methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN)) {
            encDecMap = encryptionService.encrypt(propertyMap);
        } else {
            logger.debug("FATAL: why would a non-create non-update come here?");
            encDecMap = encryptionService.decrypt(propertyMap);
        }

        if (listPropertyMap.size() > 0) {
            encDecListPropertyMap = updateEncDecListMap(listPropertyMap, methodOrigin);
            encDecMap.putAll(encDecListPropertyMap);
        }

        setEncDecMap(encDecMap, encDecPropertyTable);
        setEncryptedDecryptedProperty(encDecPropertyTable, methodOrigin);

    }


    private Map<String, Object> updateEncDecListMap(Map<String, Object> listPropertyMap, String methodOrigin) throws EncryptionException {
        Map<String, Object> encDecListPropertyMap = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : listPropertyMap.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            List values = (List) v;
            List encValues = new ArrayList();
            for (Object listV : values) {
                String encDecValue = null;
                if(methodOrigin.equalsIgnoreCase(Constants.CREATE_METHOD_ORIGIN) || methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN)){
                    encDecValue = encryptionService.encrypt(listV);
                } else {
                    encDecValue = encryptionService.decrypt(listV);
                }
                encValues.add(encDecValue);
            }
            encDecListPropertyMap.put(k, encValues);
        }
        return encDecListPropertyMap;
    }


    private void setEncDecMap(Map<String, Object> encryptedMap, Table<Vertex, Vertex, Map<String, Object>> encDecPropertyTable) {
        for (Map.Entry<String, Object> entry : encryptedMap.entrySet()) {
            encDecPropertyTable.values().forEach(map -> {
                if (map.containsKey(entry.getKey())) {
                    map.put(entry.getKey(), entry.getValue());
                }
            });
        }
    }

    private void setEncryptedDecryptedProperty(Table<Vertex, Vertex, Map<String, Object>> encDecPropertyTable, String methodOrigin) throws AuditFailedException {

        for (Table.Cell<Vertex, Vertex, Map<String, Object>> cell : encDecPropertyTable.cellSet()) {
            Vertex subject = cell.getRowKey();
            Vertex newSubject = cell.getColumnKey();
            for (Map.Entry<String, Object> entry : cell.getValue().entrySet()) {
                Object entryValue = entry.getValue();
                String entryKey = entry.getKey();
                String tailOfPropertyKey = entryKey.substring(entryKey.lastIndexOf("/") + 1).trim();
                String newKey = null;
                if (methodOrigin.equalsIgnoreCase(Constants.CREATE_METHOD_ORIGIN) || methodOrigin.equalsIgnoreCase(Constants.UPDATE_METHOD_ORIGIN)){
                    newKey = entryKey.replace(tailOfPropertyKey, "encrypted" + tailOfPropertyKey);
                    setProperty(newSubject, newKey, entryValue, methodOrigin);
                    VertexProperty property = subject.property(entryKey);
                    setMetaProperty(subject, newSubject, property, methodOrigin);
                } else if(methodOrigin.equalsIgnoreCase(Constants.READ_METHOD_ORIGIN)){
                    newKey = entryKey.replace(tailOfPropertyKey, tailOfPropertyKey.substring(9));
                    Iterator<Property<Object>> propIter = newSubject.property(newKey).properties();
                    setProperty(newSubject, newKey, entryValue, methodOrigin);
                    while (propIter.hasNext()) {
                        Property<Object> propertyObj = propIter.next();
                        newSubject.property(newKey).property(propertyObj.key(), propertyObj.value());
                    }
                }

            }
        }
    }


    public void setAuditInfo(Vertex v, boolean isNew) {
        if (authenticationEnabled) {
            String userId = ((AuthInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getSub();
            long timestamp = new Date().getTime();
            if (isNew) {
                v.property(registryContext + Constants.AuditProperties.createdBy.name(), userId);
                v.property(registryContext + Constants.AuditProperties.createdAt.name(), timestamp);
            }
            v.property(registryContext + Constants.AuditProperties.lastUpdatedBy.name(), userId);
            v.property(registryContext + Constants.AuditProperties.lastUpdatedAt.name(), timestamp);
        } else {
            logger.debug("AuthenticationEnabled: false - So not adding audit information.");
        }
    }

}

