package io.opensaber.registry.util;

import com.fasterxml.jackson.databind.JsonNode;
import io.opensaber.registry.middleware.util.AuditFields;
import io.opensaber.registry.middleware.util.DateUtil;
import io.opensaber.registry.service.impl.RegistryServiceImpl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuditHelper {

    private static Logger logger = LoggerFactory.getLogger(RegistryServiceImpl.class);

    @Autowired
    private DefinitionsManager definitionsManager;

    /**
     * ensure the audit fields(createdAt, createdBy) at time of adding a fresh record/node
     *
     * @param node
     */
    public void ensureCreateAuditFields(JsonNode node, String userId) {
        List<String> systemFields = systemFields(node.fieldNames().next());
        for (String field : systemFields) {
            addSystemProperty(field, node, userId);
        }
    }

    /**
     * ensure the audit fields(updatedAt, updatedBy) at time of updating a record/node
     *
     * @param node
     */
    public void ensureUpdateAuditFields(JsonNode node, String userId) {
        List<String> systemFields = systemFields(node.fieldNames().next());
        for (String field : systemFields) {
            addSystemProperty(field, node, userId);

        }
    }

    /**
     * adds a system property to given node
     * @param field       propertyName
     * @param node
     * @param userId
     */
    public void addSystemProperty(String field, JsonNode node, String userId) {
        String timeStamp = DateUtil.instantTimeStamp();
        try {
            switch (AuditFields.getByValue(field)) {
                case _osCreatedAt:
                    AuditFields._osCreatedAt.createdAt(node, timeStamp);
                    break;
                case _osCreatedBy:
                    AuditFields._osCreatedBy.createdBy(node, userId);
                    break;
                case _osUpdatedAt:
                    AuditFields._osUpdatedAt.updatedAt(node, timeStamp);
                    break;
                case _osUdatedBy:
                    AuditFields._osUdatedBy.updatedBy(node, userId);
                    break;
            }
        } catch (Exception e) {
            logger.error("Audit field - {} not valid!", field);
        }
    }

    public List<String> systemFields(String definitionName) {
        Definition def = definitionsManager.getDefinition(definitionName);
        return def != null ? def.getOsSchemaConfiguration().getSystemFields() : new ArrayList<>();

    }

}
