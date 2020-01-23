package io.opensaber.registry.service;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.opensaber.pojos.AuditRecord;
import io.opensaber.registry.sink.shard.Shard;

public interface IAuditService {

    /**
     * This is starting of audit in the application, audit details of read, add, update, delete and search activities
     *
     * 
     */
    public void doAudit(String userId, JsonNode readNode, JsonNode mergedNode, String operation, String auditAction, String id,
    		List<String> entityTypes, String entityRootId, List<Integer> transactionId, Shard shard);
    public void auditToFile(AuditRecord auditRecord) throws JsonProcessingException;
}
