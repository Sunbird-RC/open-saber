package io.opensaber.registry.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.opensaber.registry.service.ISearchService;

public class ElasticSearch implements ISearchService {

    @Override
    public JsonNode search(JsonNode inputQueryNode) {
        //TODO: call the ElasticService search
        return null;
    }

}
