package io.opensaber.registry.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.audit.IAuditService;
import io.opensaber.pojos.APIMessage;
import io.opensaber.pojos.AuditInfo;
import io.opensaber.pojos.AuditRecord;
import io.opensaber.pojos.FilterOperators;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.dao.RegistryDaoImpl;
import io.opensaber.registry.dao.SearchDaoImpl;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.sink.shard.ShardManager;
import io.opensaber.registry.util.DefinitionsManager;
import io.opensaber.registry.util.RecordIdentifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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

	@Autowired
	private IAuditService auditService;

	@Autowired
	private APIMessage apiMessage;

	@Value("${database.uuidPropertyName}")
	public String uuidPropertyName;
	
	@Value("${search.offset}")
	private int offset;
	
	@Value("${search.limit}")
	private int limit;

	@Override
	public JsonNode search(JsonNode inputQueryNode) throws IOException {
		AuditRecord auditRecord = null;
		List<Integer> transaction = new LinkedList<>();
		List<AuditInfo> auditInfoLst = new LinkedList<>();
		ArrayNode result = JsonNodeFactory.instance.arrayNode();
		SearchQuery searchQuery = getSearchQuery(inputQueryNode, offset, limit);

		if(searchQuery.getFilters().size() == 1 && searchQuery.getFilters().get(0).getOperator() == FilterOperators.queryString)
            throw new IllegalArgumentException("free-text queries not supported for native search!");

		// Now, search across all shards and return the results.
		for (DBConnectionInfo dbConnection : dbConnectionInfoMgr.getConnectionInfo()) {

			// TODO: parallel search.
			shardManager.activateShard(dbConnection.getShardId());
			IRegistryDao registryDao = new RegistryDaoImpl(shard.getDatabaseProvider(), definitionsManager, uuidPropertyName);
			SearchDaoImpl searchDao = new SearchDaoImpl(registryDao);
			try (OSGraph osGraph = shard.getDatabaseProvider().getOSGraph()) {
				Graph graph = osGraph.getGraphStore();
				try (Transaction tx = shard.getDatabaseProvider().startTransaction(graph)) {
                    ObjectNode shardResult = (ObjectNode) searchDao.search(graph, searchQuery);
                    if (!shard.getShardLabel().isEmpty()) {
                        // Replace osid with shard details
                        String prefix = shard.getShardLabel() + RecordIdentifier.getSeparator();
                        JSONUtil.addPrefix((ObjectNode) shardResult, prefix, new ArrayList<>(Arrays.asList(uuidPropertyName)));
                    }
                   
                    result.add(shardResult);
					transaction.add(tx.hashCode());
				}
			} catch (Exception e) {
				logger.error("search operation failed: {}", e);
			}
		}
		
		auditRecord = new AuditRecord();
		for (String entity : searchQuery.getEntityTypes()) {
			AuditInfo auditInfo = new AuditInfo();
			auditInfo.setOp(Constants.AUDIT_ACTION_SEARCH_OP);
			auditInfo.setPath(entity);
			auditInfoLst.add(auditInfo);
		}
		auditRecord.setAuditInfo(auditInfoLst);
		auditRecord.setUserId(apiMessage.getUserID()).setAction(Constants.AUDIT_ACTION_SEARCH).setTransactionId(transaction);
		auditService.audit(auditRecord);
		return buildResultNode(searchQuery, result);
	}
	
	/**
	 * combines all the nodes for an entity
	 * @param entity
	 * @param allShardResult
	 * @return
	 */
	private ArrayNode getEntityAttibute(String entity, ArrayNode allShardResult) {
		ArrayNode resultArray = JsonNodeFactory.instance.arrayNode();
		for (int i = 0; i < allShardResult.size(); i++) {
			resultArray.addAll((ArrayNode) allShardResult.get(i).get(entity));
		}
		return resultArray;
	}
	/**
	 * Builds result node from given array of shard nodes 
	 * @param searchQuery
	 * @param allShardResult
	 * @return
	 */
	private JsonNode buildResultNode(SearchQuery searchQuery, ArrayNode allShardResult) {
		ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
		for (String entity : searchQuery.getEntityTypes()) {
			ArrayNode entityResult = getEntityAttibute(entity, allShardResult);
			resultNode.set(entity, entityResult);
		}
		return resultNode;
	}
}
