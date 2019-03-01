package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.elastic.IElasticService;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.service.ISearchService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This class provide search option with Elastic search Hits elastic search
 * database to operate
 *
 */
@Component
public class ElasticSearchService implements ISearchService {

    @Autowired
    private IElasticService elasticService;

    @Value("${search.offset}")
    private int offset;

    @Value("${search.limit}")
    private int limit;

    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;

    @Override
    public JsonNode search(JsonNode inputQueryNode) {
        // calls the Elastic search
        String indexName = inputQueryNode.fieldNames().next().toLowerCase();
        SearchQuery searchQuery = getSearchQuery(inputQueryNode, offset, limit);
        Map<String, Object> result = elasticService.search(indexName, searchQuery);

        // build the response
        ArrayNode resultArray = JsonNodeFactory.instance.arrayNode();
        result.forEach((key, value) -> {
            logger.info(key + ":" + value);
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode node = (ObjectNode) mapper.readTree(mapper.writeValueAsString(value));
                node.put(uuidPropertyName, key);
                resultArray.add(node);

            } catch (Exception e) {
                logger.error("Failed to create node for {}, {}", value, e);
            }

        });

        return resultArray;
    }

}
