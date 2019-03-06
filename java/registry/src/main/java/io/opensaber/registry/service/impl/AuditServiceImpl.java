package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensaber.registry.dao.IRegistryDao;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.AuditItemDetails;
import io.opensaber.registry.model.AuditRecord;
import io.opensaber.registry.service.IAuditService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuditServiceImpl implements IAuditService {

	private static Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

	@Autowired
	private ObjectMapper objectMapper;

	// TODO - how audit happens and where it is written to needs some thought
	private IRegistryDao registryDao;

	@Value("${audit.frame.file}")
	private String auditFrameFile;

	@Override
	public void audit(AuditRecord auditRecord) throws IOException {
		List<AuditItemDetails> auditItemDetails = null;
		if (!auditRecord.getAction().equalsIgnoreCase("READ")) {
			JsonNode differenceJson = JSONUtil.diff(auditRecord.getExistingNode(), auditRecord.getLatestNode());
			auditItemDetails = Arrays.asList(objectMapper.treeToValue(differenceJson, AuditItemDetails[].class));
			//auditRecord.setItemDetails(auditItemDetails);
		} else {
			auditItemDetails = auditRecord.getItemDetails();
		}
		logger.debug("audit record printed");

		auditItemDetails.forEach(auditItem -> {
			logger.info("Action: {} Transaction: {} UserID: {} Operation: {} Path: {}",
					auditRecord.getAction(), auditRecord.getTransactionId(), auditRecord.getUserId(),
					auditItem.getOp(), auditItem.getPath());
		});

		logger.info("audit info {}", auditRecord);
	}
}
