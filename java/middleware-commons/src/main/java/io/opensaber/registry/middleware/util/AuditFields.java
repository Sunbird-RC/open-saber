package io.opensaber.registry.middleware.util;

import com.fasterxml.jackson.databind.JsonNode;
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
            // TODO Auto-generated method stub
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
            // TODO Auto-generated method stub
        }

    },
    createdby {

        @Override
        public void createdBy(JsonNode node, String userId) {
            // TODO Auto-generated method stub
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
