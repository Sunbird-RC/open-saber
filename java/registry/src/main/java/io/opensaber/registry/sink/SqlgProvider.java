package io.opensaber.registry.sink;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umlg.sqlg.structure.SqlgGraph;

import io.opensaber.registry.model.DBConnectionInfo;

public class SqlgProvider extends DatabaseProvider {

	private Logger logger = LoggerFactory.getLogger(SqlgProvider.class);
	private SqlgGraph graph;

	public SqlgProvider(DBConnectionInfo connectionInfo) {
		Configuration config = new BaseConfiguration();
		config.setProperty("jdbc.url", connectionInfo.getUri());
		config.setProperty("jdbc.username", connectionInfo.getUsername());
		config.setProperty("jdbc.password", connectionInfo.getPassword());
		graph = SqlgGraph.open(config);
	}

	@Override
	public Graph getGraphStore() {
		return graph;
	}

	@Override
	public SqlgGraph getRawGraph() {
		return graph;
	}

	@PostConstruct
	public void init() {
		logger.info("**************************************************************************");
		logger.info("Initializing SQLG DB instance ...");
		logger.info("**************************************************************************");
	}

	@PreDestroy
	public void shutdown() throws Exception {
		logger.info("**************************************************************************");
		logger.info("Gracefully shutting down SQLG DB instance ...");
		logger.info("**************************************************************************");
		graph.close();
	}

	@Override
	public void commitTransaction(Graph graph, Transaction tx) {
		commitTransaction(graph, tx, false);
	}
}
