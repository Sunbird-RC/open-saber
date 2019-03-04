package io.opensaber.elastic;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.opensaber.pojos.Filter;
import io.opensaber.pojos.FilterOperators;
import io.opensaber.pojos.SearchQuery;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class ElasticServiceImplTest {

    private static String indexName = "test";
    private static String typeName = "doc";
    private static ElasticServiceImpl elasticService = new ElasticServiceImpl();
    
    @BeforeClass
    public static void init() throws IOException{
        System.out.println("test before method");
        elasticService.setType(typeName);
        elasticService.setConnectionInfo("localhost:9200");
        if(!elasticService.isIndexExists(indexName))
            elasticService.addIndex(indexName, typeName);
        
        populateEntity(); // gets updated from 2nd time
    }
         
    private static void populateEntity() throws IOException {

        //first entity added
        HashMap<String, Object> inputEntity =  new HashMap<String, Object>();
        inputEntity.put("name","First");
        inputEntity.put("year", 2011);
        inputEntity.put("language", "java"); 
        inputEntity.put("gender", "GenderTypeCode-MALE");
        HashMap<String, Object> subEntity =  new HashMap<String, Object>();
        subEntity.put("pin", "560068");
        subEntity.put("state", "bangalore");
        inputEntity.put("address", subEntity);        
        elasticService.addEntity(indexName, "id-1", inputEntity);
        
        //seacond entity added
        HashMap<String, Object> inputEntity2 =  new HashMap<String, Object>();
        inputEntity2.put("name","Second");
        inputEntity2.put("year", 2012);
        inputEntity2.put("language", "java"); 
        inputEntity2.put("gender", "GenderTypeCode-FEMALE");
        HashMap<String, Object> subEntity2 =  new HashMap<String, Object>();
        subEntity2.put("pin", "560061");
        subEntity2.put("state", "bangalore");        
        inputEntity2.put("address", subEntity2);        
        elasticService.addEntity(indexName, "id-2", inputEntity2);
    }
    
    private SearchQuery getSearchQuery(String rootType, String property, Object value, FilterOperators op) {
        SearchQuery searchQuery = new SearchQuery(rootType, 0, 100);
        List<Filter> filterList = new ArrayList<>();
        Filter filter = new Filter(property, op, value);
        filterList.add(filter);
        searchQuery.setFilters(filterList);
        return searchQuery;
    }

    @Test
    public void testSearchEqOperator(){
        SearchQuery searchQuery = getSearchQuery("", "gender", "GenderTypeCode-FEMALE", FilterOperators.eq);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==1);        
    }
    @Test
    public void testSearchNeqOperator(){
        SearchQuery searchQuery = getSearchQuery("", "gender", "GenderTypeCode-FEMALE", FilterOperators.neq);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==1);        
    }
    @Test
    public void testSearchGteOperator(){
        SearchQuery searchQuery = getSearchQuery("", "year", 2011, FilterOperators.gte);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==2);        
    }
    @Test
    public void testSearchLteOperator(){
        SearchQuery searchQuery = getSearchQuery("", "year", 2012, FilterOperators.lte);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==2);        
    }
    
    @Test
    public void testSearchRangeOperator(){
        List<Object> range = new ArrayList<>();
        range.add(2010);
        range.add(2012);
        SearchQuery searchQuery = getSearchQuery("", "year", range, FilterOperators.between);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==2);        
    }
    
    @Test
    public void testSearchOrOperator(){
        List<Object> range = new ArrayList<>();
        range.add(2010);
        range.add(2011);
        range.add(2012);
        SearchQuery searchQuery = getSearchQuery("", "year", range, FilterOperators.or);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==2);        
    }
    
    @Test
    public void testSearchNestedOperator(){
        SearchQuery searchQuery = new SearchQuery("", 0, 100);
        List<Filter> filterList = new ArrayList<>();
        Filter filter = new Filter("pin", FilterOperators.eq, "560068");
        filter.setPath("address");
        filterList.add(filter);
        searchQuery.setFilters(filterList);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.size()==1);        
    }
    
    @Test
    public void testSearchStartsWithOperator(){
        SearchQuery searchQuery = getSearchQuery("", "name", "Fi", FilterOperators.startsWith);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.get(0).get("name").asText().equalsIgnoreCase("First"));        
    }
    @Test
    public void testSearchEndsWithOperator(){
        SearchQuery searchQuery = getSearchQuery("", "name", "nd", FilterOperators.endsWith);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.get(0).get("name").asText().equalsIgnoreCase("Second"));        
    }    
    @Test
    public void testSearchNotStartsWithOperator(){
        SearchQuery searchQuery = getSearchQuery("", "name", "Fi", FilterOperators.notStartsWith);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.get(0).get("name").asText().equalsIgnoreCase("Second"));        
    }
    @Test
    public void testSearchNotEndsWithOperator(){
        SearchQuery searchQuery = getSearchQuery("", "name", "nd", FilterOperators.notEndsWith);
        JsonNode result = elasticService.search(indexName, searchQuery);
        assertTrue(result.get(0).get("name").asText().equalsIgnoreCase("First"));        
    }

}
