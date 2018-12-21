package io.opensaber.registry.dao.impl;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import io.opensaber.registry.dao.RegistryDaoImpl;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import io.opensaber.converters.JenaRDF4J;
import io.opensaber.pojos.APIMessage;
import io.opensaber.registry.authorization.AuthorizationToken;
import io.opensaber.registry.authorization.pojos.AuthInfo;
import io.opensaber.registry.config.GenericConfiguration;
import io.opensaber.registry.controller.RegistryTestBase;
import io.opensaber.registry.exception.AuditFailedException;
import io.opensaber.registry.exception.DuplicateRecordException;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.exception.EntityCreationException;
import io.opensaber.registry.exception.MultipleEntityException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.exception.audit.LabelCannotBeNullException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.RDFUtil;
import io.opensaber.registry.model.AuditRecordReader;
import io.opensaber.registry.service.impl.EncryptionServiceImpl;
import io.opensaber.registry.sink.DBProviderFactory;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.tests.utility.TestHelper;
import io.opensaber.utils.converters.RDF2Graph;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { RegistryDaoImpl.class, Environment.class, ObjectMapper.class, GenericConfiguration.class,
		EncryptionServiceImpl.class, APIMessage.class, DBConnectionInfoMgr.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ActiveProfiles(Constants.TEST_ENVIRONMENT)
public class RegistryDaoImplTest extends RegistryTestBase {
	private static final String RICH_LITERAL_TTL = "rich-literal.jsonld";
	private static final String CONTEXT_CONSTANT = "sample:";
	private static Logger logger = LoggerFactory.getLogger(RegistryDaoImplTest.class);
	private static Graph graph;
	@Rule
	public ExpectedException expectedEx = ExpectedException.none();
	@Rule
	public TestRule watcher = new TestWatcher() {
		@Override
		protected void starting(Description description) {
			logger.debug("Executing test: " + description.getMethodName());
		}

		@Override
		protected void succeeded(Description description) {
			logger.debug("Successfully executed test: " + description.getMethodName());
		}

		@Override
		protected void failed(Throwable e, Description description) {
			logger.debug(
					String.format("Test %s failed. Error message: %s", description.getMethodName(), e.getMessage()));
		}
	};

	AuditRecordReader auditRecordReader;
	@Autowired
	private Environment environment;
	@Autowired
	private RegistryDaoImpl registryDao;
	private DatabaseProvider databaseProvider;
	@Value("${registry.context.base}")
	private String registryContext;
	@Autowired
	private DBProviderFactory dbProviderFactory;

	@Before
	public void initializeGraph() throws IOException {
		graph = TinkerGraph.open();
	    databaseProvider = dbProviderFactory.getInstance(null);
	    auditRecordReader = new AuditRecordReader(databaseProvider);

		MockitoAnnotations.initMocks(this);
		TestHelper.clearData(databaseProvider);
		databaseProvider.getGraphStore().addVertex(Constants.GRAPH_GLOBAL_CONFIG).property(Constants.PERSISTENT_GRAPH,
				true);
		AuthInfo authInfo = new AuthInfo();
		authInfo.setAud("aud");
		authInfo.setName("name");
		authInfo.setSub("sub");
		AuthorizationToken authorizationToken = new AuthorizationToken(authInfo,
				Collections.singletonList(new SimpleGrantedAuthority("blah")));
		SecurityContextHolder.getContext().setAuthentication(authorizationToken);
	}


	@After
	public void shutDown() throws Exception {
		if (graph != null) {
			graph.close();
		}
	}





}
