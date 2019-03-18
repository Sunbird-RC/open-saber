package io.opensaber.actors.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Value;
import io.opensaber.elastic.ESMessage;
import io.opensaber.pojos.AuditRecord;
import io.opensaber.pojos.OSGenericMessage;
import java.util.HashMap;
import java.util.Map;
import org.sunbird.akka.core.MessageProtos;

public class MessageFactory {
    private static final MessageFactory instance = new MessageFactory();
    private MessageFactory() {}
    public static MessageFactory instance(){
        return instance;
    }

    public MessageProtos.Message createElasticSearchMessage(String operation, ESMessage esMessage) throws JsonProcessingException {
        MessageProtos.Message.Builder msgBuilder = MessageProtos.Message.newBuilder();
        msgBuilder.setPerformOperation(operation);
        msgBuilder.setTargetActorName("ElasticSearcher");
        Value.Builder payloadBuilder = msgBuilder.getPayloadBuilder();
        ObjectMapper objectMapper = new ObjectMapper();
        payloadBuilder.setStringValue(objectMapper.writeValueAsString(esMessage));
        msgBuilder.setPayload(payloadBuilder.build());
        return msgBuilder.build();
    }

    public MessageProtos.Message createAuditMessage(AuditRecord auditRecord) throws JsonProcessingException {
        MessageProtos.Message.Builder msgBuilder = MessageProtos.Message.newBuilder();
        msgBuilder.setTargetActorName("AuditActor");
        Value.Builder payloadBuilder = msgBuilder.getPayloadBuilder();
        ObjectMapper objectMapper = new ObjectMapper();
        payloadBuilder.setStringValue(objectMapper.writeValueAsString(auditRecord));
        msgBuilder.setPayload(payloadBuilder.build());
        return msgBuilder.build();
    }

    public MessageProtos.Message createOSActor(boolean esEnabled, String operation, String index, String osid, JsonNode input,
                                               AuditRecord auditRecord) throws JsonProcessingException {
        MessageProtos.Message.Builder msgBuilder = MessageProtos.Message.newBuilder();
        msgBuilder.setPerformOperation(operation);
        msgBuilder.setTargetActorName("OSActor");
        Value.Builder payloadBuilder = msgBuilder.getPayloadBuilder();
        ESMessage esMessage = new ESMessage();
        esMessage.setIndexName(index);
        esMessage.setOsid(osid);
        esMessage.setInput(input);
        ObjectMapper objectMapper = new ObjectMapper();
        OSGenericMessage osGenericMessage = new OSGenericMessage();
        Map<String, Object> osMsg = new HashMap<>();
        osMsg.put("esEnabled",esEnabled);
        osMsg.put("esMessage",esMessage);
        osMsg.put("auditMessage",auditRecord);
        osGenericMessage.setOsMap(osMsg);
        payloadBuilder.setStringValue(objectMapper.writeValueAsString(osGenericMessage));
        msgBuilder.setPayload(payloadBuilder.build());
        return msgBuilder.build();
    }
}
