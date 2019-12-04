package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.audit.IAuditService;
import io.opensaber.pojos.APIMessage;
import io.opensaber.pojos.AuditInfo;
import io.opensaber.pojos.AuditRecord;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.dao.RegistryDaoImpl;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.DateUtil;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.sink.DatabaseProvider;
import io.opensaber.registry.sink.OSGraph;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.util.DefinitionsManager;
import io.opensaber.registry.util.ReadConfigurator;
import io.opensaber.registry.util.RecordIdentifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.LinkedList;
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
public class NativeReadService implements IReadService {

	private static Logger logger = LoggerFactory.getLogger(NativeReadService.class);

	@Autowired
	private DefinitionsManager definitionsManager;

	@Autowired
	private IAuditService auditService;

	@Value("${database.uuidPropertyName}")
	public String uuidPropertyName;

	/**
	 * This method interacts with the native db and reads the record
	 *
	 * @param id           - osid
	 * @param entityType
	 * @param configurator
	 * @return
	 * @throws Exception
	 */
	@Override
	public JsonNode getEntity(Shard shard, String userId, String id, String entityType, ReadConfigurator configurator) throws Exception {
		AuditRecord auditRecord = null;
		DatabaseProvider dbProvider = shard.getDatabaseProvider();
		IRegistryDao registryDao = new RegistryDaoImpl(dbProvider, definitionsManager, uuidPropertyName);
		try (OSGraph osGraph = dbProvider.getOSGraph()) {
			Graph graph = osGraph.getGraphStore();
			Transaction tx = dbProvider.startTransaction(graph);
			JsonNode result = registryDao.getEntity(graph, entityType, id, configurator);

			if (!shard.getShardLabel().isEmpty()) {
				// Replace osid with shard details
				String prefix = shard.getShardLabel() + RecordIdentifier.getSeparator();
				JSONUtil.addPrefix((ObjectNode) result, prefix, new ArrayList<String>(Arrays.asList(uuidPropertyName)));
			}

			dbProvider.commitTransaction(graph, tx);
			auditRecord =  new AuditRecord();
			auditRecord.setUserId(userId).setAction(Constants.AUDIT_ACTION_READ).setRecordId(id).setTransactionId(new LinkedList<>(Arrays.asList(tx.hashCode())))
					.setAuditId(UUID.randomUUID().toString()).setTimeStamp(DateUtil.getTimeStamp());
			AuditInfo auditInfo = new AuditInfo();
			auditInfo.setOp(Constants.AUDIT_ACTION_READ_OP);
			auditInfo.setPath("/"+entityType);
			auditRecord.setAuditInfo(Arrays.asList(auditInfo));
			auditService.audit(auditRecord);
			return result;
		}
	}

}
