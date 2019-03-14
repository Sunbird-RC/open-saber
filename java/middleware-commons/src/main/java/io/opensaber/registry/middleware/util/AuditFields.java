package io.opensaber.registry.middleware.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum AuditFields {

    createdat {

        @Override
        public void createdBy(JsonNode node, String userId) {
            // No implements
        }

        @Override
        public void createdAt(JsonNode node) {
            JSONUtil.addField((ObjectNode)node, "createdAt", currentTimeStamp());
        }

        @Override
        public void updatedAt(JsonNode node) {
            // No implements
        }

    },
    updatedat {

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
            JSONUtil.addField((ObjectNode)node, "updatedAt", currentTimeStamp());
        }

    },
    createdby {

        @Override
        public void createdBy(JsonNode node, String userId) {
            JSONUtil.addField((ObjectNode)node, "createdBy", userId);
        }

        @Override
        public void createdAt(JsonNode node) {
            // No implements
        }

        @Override
        public void updatedAt(JsonNode node) {
            // No implements
        }
    };

    static Logger logger = LoggerFactory.getLogger(AuditFields.class);
    static final String dateformat = "yyyy-MM-dd HH:mm:ss";
    static final String timezone = "UTC";

    public abstract void createdBy(JsonNode node, String userId);

    public abstract void createdAt(JsonNode node);

    public abstract void updatedAt(JsonNode node);

    public String currentTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat(dateformat);
        sdf.setTimeZone(TimeZone.getTimeZone(timezone));
        return sdf.format(new Date());
    }

}
