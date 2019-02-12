package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.opensaber.pojos.*;
import io.opensaber.registry.dao.*;
import io.opensaber.registry.middleware.util.*;
import io.opensaber.registry.model.*;
import io.opensaber.registry.service.*;
import io.opensaber.registry.sink.*;
import io.opensaber.registry.sink.shard.*;
import io.opensaber.registry.util.*;
import org.apache.tinkerpop.gremlin.structure.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.util.*;

@Component
public class SearchServiceImpl implements SearchService {

	private static Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

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

	private SearchQuery getSearchQuery(JsonNode inputQueryNode) {
		String rootLabel = inputQueryNode.fieldNames().next();

		SearchQuery searchQuery = new SearchQuery(rootLabel);
		List<Filter> filterList = new ArrayList<>();
		if (rootLabel != null && !rootLabel.isEmpty()) {
			addToFilterList(null, inputQueryNode.get(rootLabel), filterList);
		}

		searchQuery.setFilters(filterList);
		return searchQuery;
	}

	/**
	 * For a given path filter, iterate through the fields given and set the filterList
	 * @param path
	 * @param inputQueryNode
	 * @return
	 */
	private void addToFilterList(String path, JsonNode inputQueryNode, List<Filter> filterList) {
		Iterator<Map.Entry<String, JsonNode>> searchFields = inputQueryNode.fields();
		// Iterate and get the fields.
		while (searchFields.hasNext()) {
			Map.Entry<String, JsonNode> entry = searchFields.next();
			JsonNode entryVal = entry.getValue();
			if (entryVal.isObject()) {
				addToFilterList(entry.getKey(), entryVal, filterList);
			} else if (entryVal.isValueNode()){
				Filter filter = new Filter(path);
				filter.setProperty(entry.getKey());
				filter.setOperator("=");
				filter.setValue(entryVal.asText());
				filterList.add(filter);
			}
		}
	}

	@Override
	public JsonNode search(JsonNode inputQueryNode) {
		ArrayNode result = JsonNodeFactory.instance.arrayNode();
		SearchQuery searchQuery = getSearchQuery(inputQueryNode);

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
							JSONUtil.addPrefix((ObjectNode) jsonNode, prefix, new ArrayList<String>(Arrays.asList(uuidPropertyName)));
						}

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
