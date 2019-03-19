package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensaber.pojos.AuditInfo;
import io.opensaber.pojos.AuditRecord;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.service.IAuditService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Audit service implementation for audit layer in the application, as of now audits save details to file system
 */
@Component
public class AuditServiceImpl implements IAuditService {

	private static Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);
	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * This is starting of audit in the application, audit details of read, add, update, delete and search activities
	 *
	 * @param auditRecord - input audit details
	 * @throws IOException
	 */
	@Override
	@Async("auditExecutor")
	public void audit(AuditRecord auditRecord) throws IOException {
		List<AuditInfo> auditItemDetails = null;
		/*if (!(auditRecord.getAction().equalsIgnoreCase(Constants.AUDIT_ACTION_READ) || auditRecord.getAction().equalsIgnoreCase(Constants.AUDIT_ACTION_DELETE))) {
			JsonNode differenceJson = JSONUtil.diffJsonNode(auditRecord.getExistingNode(), auditRecord.getLatestNode());
			auditItemDetails = Arrays.asList(objectMapper.treeToValue(differenceJson, AuditInfo[].class));
			auditRecord.setAuditInfo(auditItemDetails);
		}*/
		String auditString = objectMapper.writeValueAsString(auditRecord);
		logger.info("{}", auditString);
	}
}
