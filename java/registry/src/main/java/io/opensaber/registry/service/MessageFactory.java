package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Value;
import io.opensaber.elastic.ESMessage;
import org.sunbird.akka.core.MessageProtos;

public class MessageFactory {
    private static final MessageFactory instance = new MessageFactory();
    private MessageFactory() {}
    public static MessageFactory instance(){
        return instance;
    }

    public MessageProtos.Message createElasticSearchMessage(String operation, String index, String osid, JsonNode input) {
        MessageProtos.Message.Builder msgBuilder = MessageProtos.Message.newBuilder();
        msgBuilder.setPerformOperation(operation);
        msgBuilder.setTargetActorName("ElasticSearcher");

        ESMessage esMessage = new ESMessage(index, osid, input);
        Value.Builder payloadBuilder = msgBuilder.getPayloadBuilder();

        payloadBuilder.setStringValue(esMessage.toString());
        msgBuilder.setPayload(payloadBuilder.build());
        return msgBuilder.build();
    }
}
