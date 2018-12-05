package io.opensaber.registry.sink;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.neo4j.driver.v1.AuthToken;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;

import com.steelbridgelabs.oss.neo4j.structure.Neo4JElementIdProvider;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JGraph;
import com.steelbridgelabs.oss.neo4j.structure.providers.Neo4JNativeElementIdProvider;

import io.opensaber.registry.model.DatabaseConnection;


public class Neo4jShardProvider extends DatabaseProvider{
	
	private Driver driver;
	private Graph graph;

	public Neo4jShardProvider(DatabaseConnection connection) {

		AuthToken authToken = AuthTokens.basic(connection.getUsername(), connection.getPassword());
        if (authToken != null) {
            driver = GraphDatabase.driver(connection.getUri(),
                    authToken);
        } else {
            driver = GraphDatabase.driver(connection.getUri(),
                    AuthTokens.none());
        }

		Neo4JElementIdProvider<?> idProvider = new Neo4JNativeElementIdProvider();
		Neo4JGraph neo4JGraph = new Neo4JGraph(driver, idProvider, idProvider);
		//neo4JGraph.setProfilerEnabled(profilerEnabled);
		graph = neo4JGraph;
		System.out.println("Initializing remote graph db for "+connection.getName());	
	}

	public Graph getGraphStore() {
		return graph;
	}

	public void shutdown() throws Exception {
		graph.close();
		if (driver != null) {
			driver.close();
		}
	}


}
