package io.opensaber.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.opensaber.pojos.Filter;
import io.opensaber.pojos.FilterOperators;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.dao.ValueType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ISearchService {

    static Logger logger = LoggerFactory.getLogger(ISearchService.class);

    JsonNode search(JsonNode inputQueryNode);
    
    /**
     * Building SearchQuery from given input search json
     * @param inputQueryNode          request search json
     * @param offset                  starting point
     * @param limit                   size of object search result hold 
     * @return
     */
    default SearchQuery getSearchQuery(JsonNode inputQueryNode, int offset, int limit) {
        String rootLabel = inputQueryNode.fieldNames().next();

        SearchQuery searchQuery = new SearchQuery(rootLabel, offset, limit);
        List<Filter> filterList = new ArrayList<>();
        JsonNode rootNode = inputQueryNode.get(rootLabel);
        if (rootLabel != null && !rootLabel.isEmpty()) {
            addToFilterList(null, rootNode, filterList);
        }
        // populates limit & offset
        try {
            searchQuery.setLimit(inputQueryNode.get("limit").asInt());
            searchQuery.setOffset(inputQueryNode.get("offset").asInt());
        } catch (Exception e) {
            logger.error("Populates SearchQuery for limit/offset: {}", e.getMessage());
        }

        searchQuery.setFilters(filterList);
        return searchQuery;
    }

    /**
     * For a given path filter, iterate through the fields given and set the filterList
     * @param path
     * @param inputQueryNode
     * @return
     */
    default void addToFilterList(String path, JsonNode inputQueryNode, List<Filter> filterList) {
        Iterator<Map.Entry<String, JsonNode>> searchFields = inputQueryNode.fields();
     
        // Iterate and get the fields.
        while (searchFields.hasNext()) {
            Map.Entry<String, JsonNode> entry = searchFields.next();
            String property = entry.getKey();
            JsonNode entryVal = entry.getValue();
            if (entryVal.isObject() && (entryVal.fields().hasNext())) {
                Map.Entry<String, JsonNode> entryValMap = entryVal.fields().next();
                String operatorStr = entryValMap.getKey();
                
                if (entryValMap.getValue().isObject()) {
                    addToFilterList(entry.getKey(), entryVal, filterList);
                } else {
                    Object value = null;
                    if (entryValMap.getValue().isArray()) {
                        value = getObjects(entryValMap.getValue());

                    } else if (entryValMap.getValue().isValueNode()) {
                        value = ValueType.getValue(entryValMap.getValue());
                    }
                    FilterOperators operator = FilterOperators.get(operatorStr);
                    if(operator == null)
                        throw new IllegalArgumentException("Search query cannot perform without operator!");

                    Filter filter = new Filter(property, operator, value);
                    filter.setPath(path);
                    filterList.add(filter);
                }
            } else {
                 throw new IllegalArgumentException("Search query is invalid!");
            }
        }
    }
    /**
     * Return all values
     * 
     * @param node
     * @return
     */
    default List<Object> getObjects(JsonNode node) {
            
        List<Object> rangeValues = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode entryVal = node.get(i);
            if (entryVal.isValueNode())
                rangeValues.add(ValueType.getValue(entryVal));
        }
        return rangeValues;
    }



}
