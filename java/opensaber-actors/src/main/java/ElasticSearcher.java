import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Value;
import io.opensaber.elastic.IElasticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.sunbird.akka.core.ActorCache;
import org.sunbird.akka.core.BaseActor;
import org.sunbird.akka.core.MessageProtos;
import org.sunbird.akka.core.Router;

import java.util.UUID;

public class ElasticSearcher extends BaseActor {
    @Autowired
    public IElasticService elasticSearch;

    @Override
    public void onReceive(MessageProtos.Message request) throws Throwable {
        logger.debug("Received a message to ElasticSearch Actor");

        switch (request.getPerformOperation()) {
            case "add":
                break;
            case "update":
                break;
            case "delete":
                break;
            case "read":
                break;
        }

        MessageProtos.Message.Builder pMsgBuilder = MessageProtos.Message.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setPerformOperation("greet");
        Value.Builder payloadBuilder = pMsgBuilder.getPayloadBuilder();
        ObjectNode person = JsonNodeFactory.instance.objectNode();
        person.put("name", "Ram");
        person.put("age", 16);

        payloadBuilder.setStringValue(person.toString());
        pMsgBuilder.setPayload(payloadBuilder);

        String targetActor = "";
        if (request.getPerformOperation() != null && request.getPerformOperation().equals("sendToBadGreeter")) {
            targetActor = "BadGeeter";
        } else {
            targetActor = "HelloGreeter";
            //pMsgBuilder.setMsgOption(MessageProtos.MessageOption.GET_BACK_RESPONSE);
        }
        pMsgBuilder.setTargetActorName(targetActor);

        ActorCache.instance().get(Router.ROUTER_NAME).tell(pMsgBuilder.build(), self());
    }

    @Override
    public void onFailure(MessageProtos.Message message) {
        logger.info("Send hello failed {}", message.toString());
    }

    @Override
    public void onSuccess(MessageProtos.Message message) {
        logger.info("Send hello answered successfully {}", message.toString());
    }

}
