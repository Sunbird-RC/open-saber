package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.AuditInfo;
import io.opensaber.registry.model.AuditRecord;
import io.opensaber.registry.service.IAuditService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AuditServiceImpl implements IAuditService {

	private static Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);
	@Autowired
	private ObjectMapper objectMapper;

	@Override
	@Async("auditExecutor")
	public void audit(AuditRecord auditRecord) throws IOException {
		List<AuditInfo> auditItemDetails = null;
		if (!auditRecord.getAction().equalsIgnoreCase("READ")) {
			JsonNode differenceJson = JSONUtil.diffJsonNode(auditRecord.getExistingNode(), auditRecord.getLatestNode());
			auditItemDetails = Arrays.asList(objectMapper.treeToValue(differenceJson, AuditInfo[].class));
			auditRecord.setAuditInfo(auditItemDetails);
		}
		String auditString  = objectMapper.writeValueAsString(auditRecord);
		logger.info("{}", auditString);
	}
}
