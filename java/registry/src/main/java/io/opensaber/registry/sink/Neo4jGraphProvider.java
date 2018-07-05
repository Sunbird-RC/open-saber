package io.opensaber.registry.sink;

import com.steelbridgelabs.oss.neo4j.structure.Neo4JElementIdProvider;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JGraph;
import com.steelbridgelabs.oss.neo4j.structure.Neo4JVertex;
import com.steelbridgelabs.oss.neo4j.structure.providers.DatabaseSequenceElementIdProvider;
import com.steelbridgelabs.oss.neo4j.structure.providers.Neo4JNativeElementIdProvider;
import com.steelbridgelabs.oss.neo4j.structure.summary.ResultSummaryLogger;
import io.opensaber.registry.middleware.util.Constants;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.neo4j.driver.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class Neo4jGraphProvider extends DatabaseProvider {

    private Logger logger = LoggerFactory.getLogger(Neo4jGraphProvider.class);
    private Graph graph;
    private Driver driver;
    private Neo4JGraph neo4JGraph;
    private Environment environment;
    Neo4JElementIdProvider<?> idProvider;

    public Neo4jGraphProvider(Environment env) {
        environment = env;
        Boolean isDatabaseEmbedded = Boolean.parseBoolean(environment.getProperty("database.embedded"));
        if (isDatabaseEmbedded) {
            String graphDbLocation = environment.getProperty(Constants.NEO4J_DIRECTORY);
            logger.info(String.format("Initializing graph db at %s ...", graphDbLocation));
            Configuration config = new BaseConfiguration();
            config.setProperty(Neo4jGraph.CONFIG_DIRECTORY, graphDbLocation);
            config.setProperty("gremlin.neo4j.conf.cache_type", "none");
            graph = Neo4jGraph.open(config);
        } else {
            try {
                String databaseHost = environment.getProperty("database.host");
                String databasePort = environment.getProperty("database.port");
                Boolean profilerEnabled = Boolean.parseBoolean(environment.getProperty("database.neo4j-profiler-enabled"));
                driver = GraphDatabase.driver(String.format("bolt://%s:%s", databaseHost, databasePort), AuthTokens.none());
                idProvider = new GUIDElementIDProvider();
                neo4JGraph = new Neo4JGraph(driver, idProvider, idProvider);
                neo4JGraph.setProfilerEnabled(profilerEnabled);
                graph = neo4JGraph;
                logger.info("Initializing remote graph db for ");
                logger.info("host: %s \n\t port: %s \n\t driver:  %s", databaseHost, databasePort,driver);
            } catch (Exception ex) {
                logger.error("Exception when initializing Neo4J DB connection...", ex);
                throw ex;
            }
        }
    }

    @Override
    public Graph getGraphStore() {
        return graph;
    }

    @PostConstruct
    public void init() {
        logger.info("**************************************************************************");
        logger.info("Initializing Graph DB instance ...");
        logger.info("**************************************************************************");
    }

    @PreDestroy
    public void shutdown() throws Exception {
        logger.info("**************************************************************************");
        logger.info("Gracefully shutting down Graph DB instance ...");
        logger.info("**************************************************************************");
        graph.close();
        if (driver != null) {
            driver.close();
        }
    }

    @Override
    public boolean isMultiValuedLiteralPropertySupported() {
        return false;
    }

    @Override
    public List<String> getIDsFromLabel(String label){
        Boolean isDatabaseEmbedded = Boolean.parseBoolean(environment.getProperty("database.embedded"));
        ArrayList<String> vertexIDList = new ArrayList<>();
        if (isDatabaseEmbedded) {
            Iterator<Vertex> verticesWithLabel= graph.traversal().clone().V().hasLabel(label);
            while(verticesWithLabel.hasNext()){
                vertexIDList.add(String.valueOf(verticesWithLabel.next().id()));
            }
        } else {
            System.out.println("===> "+label);
            StatementResult result = neo4JGraph.execute("MATCH (n) WHERE n.`@_label`='"+label+"' RETURN n");
            while(result.hasNext()){
                Record record = result.next();
                vertexIDList.add(String.valueOf(record.get("n").get(idProvider.fieldName())));
            }
        }
        return vertexIDList;
    }
}
