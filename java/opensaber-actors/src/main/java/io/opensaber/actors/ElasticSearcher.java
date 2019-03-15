package io.opensaber.actors;


import io.opensaber.elastic.IElasticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.sunbird.akka.core.BaseActor;
import org.sunbird.akka.core.MessageProtos;

public class ElasticSearcher extends BaseActor {
    @Autowired
    public IElasticService elasticSearch;

    @Override
    public void onReceive(MessageProtos.Message request) throws Throwable {
        logger.debug("Received a message to ElasticSearch Actor {}", request.getPerformOperation());

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
