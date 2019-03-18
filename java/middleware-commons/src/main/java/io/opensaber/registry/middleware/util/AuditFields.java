package io.opensaber.registry.middleware.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System fields for created, updated time and userId appended to the json node.
 */
public enum AuditFields {

    _osCreatedAt {
        @Override
        public void createdAt(JsonNode node, String timeStamp) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, _osCreatedAt.toString(), timeStamp);
        }
    },
    _osUpdatedAt {
        @Override
        public void updatedAt(JsonNode node, String timeStamp) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, _osUpdatedAt.toString(), timeStamp);
        }

    },
    _osCreatedBy {
        @Override
        public void createdBy(JsonNode node, String userId) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, _osCreatedBy.toString(), userId != null ? userId : "");
        }
    },
    _osUdatedBy {
        @Override
        public void updatedBy(JsonNode node, String userId) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, _osUdatedBy.toString(), userId != null ? userId : "");
        }
    };

    public void createdBy(JsonNode node, String userId){};

    public void updatedBy(JsonNode node, String userId){};

    public void createdAt(JsonNode node, String timeStamp){};

    public void updatedAt(JsonNode node, String timeStamp){};

    public static AuditFields getByValue(String value) {
        for (final AuditFields element : EnumSet.allOf(AuditFields.class)) {
            if (element.toString().equals(value)) {
                return element;
            }
        }
        return null;
    }

}
