package io.opensaber.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opensaber.actors.factory.MessageFactory;
import io.opensaber.elastic.ESMessage;
import io.opensaber.pojos.AuditRecord;
import io.opensaber.pojos.OSGenericMessage;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.sunbird.akka.core.ActorCache;
import org.sunbird.akka.core.BaseActor;
import org.sunbird.akka.core.MessageProtos;
import org.sunbird.akka.core.Router;

public class OSActor extends BaseActor {
    @Value("${elastic.search.enabled}")
    private boolean elasticSearchEnabled;
    public ObjectMapper objectMapper;
    @Override
    protected void onReceive(MessageProtos.Message message) throws Throwable {
        objectMapper = new ObjectMapper();
        ESMessage esMessage = null;
        AuditRecord auditRecord = null;
        OSGenericMessage osGenericMessage = objectMapper.readValue(message.getPayload().getStringValue(), OSGenericMessage.class);
        Map<String, Object> osMap = osGenericMessage.getOsMap();
        elasticSearchEnabled = (boolean) osMap.get("esEnabled");
        if(null != osMap.get("esMessage")) {
            esMessage = objectMapper.convertValue(osMap.get("esMessage"),ESMessage.class);
        }
        if(elasticSearchEnabled) {
            MessageProtos.Message esProtoMessage = MessageFactory.instance().createElasticSearchMessage(
                    message.getPerformOperation(), esMessage);
            ActorCache.instance().get(Router.ROUTER_NAME).tell(esProtoMessage, null);
        }
        if(null != osMap.get("auditMessage")) {
            auditRecord = objectMapper.convertValue(osMap.get("auditMessage"),AuditRecord.class);;
            MessageProtos.Message auditProtoMessage = MessageFactory.instance().createAuditMessage(auditRecord);
            ActorCache.instance().get(Router.ROUTER_NAME).tell(auditProtoMessage, null);
        }

    }
}
