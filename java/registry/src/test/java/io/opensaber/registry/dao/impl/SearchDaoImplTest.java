package io.opensaber.registry.dao.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.jena.vocabulary.RDF;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import io.opensaber.pojos.Filter;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.app.OpenSaberApplication;
import io.opensaber.registry.authorization.AuthorizationToken;
import io.opensaber.registry.authorization.pojos.AuthInfo;
import io.opensaber.registry.config.GenericConfiguration;
import io.opensaber.registry.controller.RegistryTestBase;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.dao.SearchDao;
import io.opensaber.registry.exception.AuditFailedException;
import io.opensaber.registry.exception.EncryptionException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.sink.DBProviderFactory;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.tests.utility.TestHelper;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { OpenSaberApplication.class, IRegistryDao.class, SearchDao.class,
		GenericConfiguration.class })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ActiveProfiles(Constants.TEST_ENVIRONMENT)
public class SearchDaoImplTest extends RegistryTestBase {

	private static Graph graph;
	@Autowired
	private IRegistryDao registryDao;
	@Autowired
	private SearchDao searchDao;
	private DatabaseProvider databaseProvider;
	@Autowired
	private DBProviderFactory dbProviderFactory;


	@Before
	public void initializeGraph() throws IOException {
	    databaseProvider = dbProviderFactory.getInstance(null);
	    searchDao.setDatabaseProvider(databaseProvider);

		graph = TinkerGraph.open();
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

	@Test
	public void test_search_no_response() throws AuditFailedException, EncryptionException, RecordNotFoundException {
		SearchQuery searchQuery = new SearchQuery();
		Map<String, Graph> responseGraph = searchDao.search(searchQuery);
		assertTrue(responseGraph.isEmpty());
	}


	private SearchQuery getSearchQuery(SearchQuery searchQuery, Filter filter, String type) {
		List<Filter> filterList = new ArrayList<Filter>();
		if (searchQuery.getFilters() != null) {
			filterList = searchQuery.getFilters();
		}
		filterList.add(filter);
		searchQuery.setFilters(filterList);
		searchQuery.setType(type);
		searchQuery.setTypeIRI(RDF.type.toString());
		return searchQuery;
	}

	private Filter getFilter(String property, String value) {
		Filter filter = new Filter();
		filter.setProperty(property);
		filter.setValue(value);
		return filter;
	}
}
