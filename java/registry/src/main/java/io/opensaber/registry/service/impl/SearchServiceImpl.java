package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.circe.Json;
import io.opensaber.pojos.Filter;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.dao.SearchDao;
import io.opensaber.registry.dao.SearchDaoImpl;
import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.SearchService;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.sink.shard.ShardManager;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class SearchServiceImpl implements SearchService {

	private static Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

	@Autowired
	private DBConnectionInfoMgr dbConnectionInfoMgr;

	@Autowired
	private ShardManager shardManager;

	@Autowired
	private SearchDao searchDao;

	@Autowired
	private Shard shard;

	private SearchQuery getSearchQuery(JsonNode inputQueryNode) {
		List<Filter> filterList = new ArrayList<>();
		String rootLabel = inputQueryNode.fieldNames().next();

		SearchQuery searchQuery = new SearchQuery(rootLabel);
		if (rootLabel != null && !rootLabel.isEmpty()) {
			// The root label is set.
			searchQuery.setRootLabel(rootLabel);

			Iterator<Map.Entry<String, JsonNode>> searchFields = inputQueryNode.get(rootLabel).fields();
			// Iterate and get the fields.
			while (searchFields.hasNext()) {
				Map.Entry<String, JsonNode> entry = searchFields.next();
				Filter filter = new Filter(entry.getKey(), "=", entry.getValue().asText());
				filterList.add(filter);
			}
			searchQuery.setFilters(filterList);
		}

		return searchQuery;
	}

	@Override
	public JsonNode search(JsonNode inputQueryNode) {
		ArrayNode result = JsonNodeFactory.instance.arrayNode();
		SearchQuery searchQuery = getSearchQuery(inputQueryNode);

		// Now, search across all shards and return the results.
		for (DBConnectionInfo dbConnection : dbConnectionInfoMgr.getConnectionInfo()) {
			shardManager.activateShard(dbConnection.getShardId());

			try (OSGraph osGraph = shard.getDatabaseProvider().getOSGraph()) {
				Graph graph = osGraph.getGraphStore();
				try (Transaction tx = shard.getDatabaseProvider().startTransaction(graph)) {
					ArrayNode oneShardResult = (ArrayNode) searchDao.search(graph, searchQuery);
					for (JsonNode jsonNode: oneShardResult) {
						result.add(jsonNode);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return result;
	}

}
