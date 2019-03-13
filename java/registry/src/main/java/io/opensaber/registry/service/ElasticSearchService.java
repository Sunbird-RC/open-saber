package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.elastic.IElasticService;
import io.opensaber.pojos.APIMessage;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.model.AuditInfo;
import io.opensaber.registry.model.AuditRecord;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * This class provide search option with Elastic search Hits elastic search
 * database to operate
 *
 */
@Component
public class ElasticSearchService implements ISearchService {
    private static Logger logger = LoggerFactory.getLogger(ElasticSearchService.class);

    @Autowired
    private IElasticService elasticService;

    @Autowired
    private APIMessage apiMessage;

    private IAuditService auditService;

    @Value("${search.offset}")
    private int offset;

    @Value("${search.limit}")
    private int limit;


    @Override
    public JsonNode search(JsonNode inputQueryNode) throws IOException {
        logger.debug("search request body = " + inputQueryNode);
        AuditRecord auditRecord = new AuditRecord();
        List<AuditInfo> auditInfoLst = new LinkedList<>();
        SearchQuery searchQuery = getSearchQuery(inputQueryNode, offset, limit);
        ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
        for(String indexName : searchQuery.getEntityTypes()){
            JsonNode node = elasticService.search(indexName.toLowerCase(), searchQuery);
            resultNode.set(indexName, node);
            if(node !=  null) {
                AuditInfo auditInfo = new AuditInfo();
                auditInfo.setOp(Constants.AUDIT_ACTION_SEARCH_OP);
                auditInfo.setPath(indexName);
                auditInfoLst.add(auditInfo);
            }
        }
        auditRecord.setAuditInfo(auditInfoLst);
        auditRecord.setUserId(apiMessage.getUserID()).setAction(Constants.AUDIT_ACTION_SEARCH);
        auditService.audit(auditRecord);
        return resultNode;

    }

}
