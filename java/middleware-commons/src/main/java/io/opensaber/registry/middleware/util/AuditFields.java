package io.opensaber.registry.middleware.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum AuditFields {

    oscreatedat {
        @Override
        public void createdBy(JsonNode node, String userId) {
            // No implements
        }

        @Override
        public void createdAt(JsonNode node) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, "createdAt", currentTimeStamp());
        }

        @Override
        public void updatedAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedBy(JsonNode node, String userId) {
            // No implements
        }
    },
    osupdatedat {
        @Override
        public void createdBy(JsonNode node, String userId) {
            // No implements
        }

        @Override
        public void createdAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedAt(JsonNode node) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, "updatedAt", currentTimeStamp());
        }

        @Override
        public void updatedBy(JsonNode node, String userId) {
            // No implements
        }
    },
    oscreatedby {
        @Override
        public void createdBy(JsonNode node, String userId) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, "createdBy", userId != null ? userId : "");
        }

        @Override
        public void createdAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedBy(JsonNode node, String userId) {
            // No implements
        }
    },
    osupdatedby {
        @Override
        public void createdBy(JsonNode node, String userId) {
            // No implements
        }

        @Override
        public void createdAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedBy(JsonNode node, String userId) {
            String firstFieldName = node.fieldNames().next();
            JSONUtil.addField((ObjectNode) node, firstFieldName, "updatedBy", userId != null ? userId : "");
        }
    };

    static Logger logger = LoggerFactory.getLogger(AuditFields.class);
    static final String dateformat = "yyyy-MM-dd'T'HH:mm:ssX";
    static final String timezone = "UTC";

    public abstract void createdBy(JsonNode node, String userId);

    public abstract void updatedBy(JsonNode node, String userId);

    public abstract void createdAt(JsonNode node);

    public abstract void updatedAt(JsonNode node);

    public String currentTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat(dateformat);
        sdf.setTimeZone(TimeZone.getTimeZone(timezone));
        return sdf.format(new Date());
    }

    public static AuditFields getByValue(String value) {
        for (final AuditFields element : EnumSet.allOf(AuditFields.class)) {
            if (element.toString().equals(value)) {
                return element;
            }
        }
        return null;
    }

}
