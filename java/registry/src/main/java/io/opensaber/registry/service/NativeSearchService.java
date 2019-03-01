package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.dao.RegistryDaoImpl;
import io.opensaber.registry.dao.SearchDaoImpl;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.ISearchService;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.sink.shard.ShardManager;
import io.opensaber.registry.util.DefinitionsManager;
import io.opensaber.registry.util.RecordIdentifier;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
/**
 * This class provides native search which hits the native database
 * Hence, this have performance in-efficiency on search operations    
 * 
 */
@Component
public class NativeSearchService implements ISearchService {

	private static Logger logger = LoggerFactory.getLogger(NativeSearchService.class);

	@Autowired
	private DBConnectionInfoMgr dbConnectionInfoMgr;

	@Autowired
	private DefinitionsManager definitionsManager;

	@Autowired
	private ShardManager shardManager;

	@Autowired
	private Shard shard;

	@Value("${database.uuidPropertyName}")
	public String uuidPropertyName;
	
	@Value("${search.offset}")
	private int offset;
	
	@Value("${search.limit}")
	private int limit;

	@Override
	public JsonNode search(JsonNode inputQueryNode) {
		ArrayNode result = JsonNodeFactory.instance.arrayNode();
		SearchQuery searchQuery = getSearchQuery(inputQueryNode, offset, limit);

		// Now, search across all shards and return the results.
		for (DBConnectionInfo dbConnection : dbConnectionInfoMgr.getConnectionInfo()) {

			// TODO: parallel search.
			shardManager.activateShard(dbConnection.getShardId());
			IRegistryDao registryDao = new RegistryDaoImpl(shard.getDatabaseProvider(), definitionsManager, uuidPropertyName);
			SearchDaoImpl searchDao = new SearchDaoImpl(registryDao);
			try (OSGraph osGraph = shard.getDatabaseProvider().getOSGraph()) {
				Graph graph = osGraph.getGraphStore();
				try (Transaction tx = shard.getDatabaseProvider().startTransaction(graph)) {
					ArrayNode oneShardResult = (ArrayNode) searchDao.search(graph, searchQuery);
					for (JsonNode jsonNode: oneShardResult) {
						if (!shard.getShardLabel().isEmpty()) {
							// Replace osid with shard details
							String prefix = shard.getShardLabel() + RecordIdentifier.getSeparator();
							JSONUtil.addPrefix((ObjectNode) jsonNode, prefix, new ArrayList<>(Arrays.asList(uuidPropertyName)));
						}

						result.add(jsonNode);
					}
				}
			} catch (Exception e) {
				logger.error("search operation failed: {}", e);
			}
		}

		return result;
	}
}
