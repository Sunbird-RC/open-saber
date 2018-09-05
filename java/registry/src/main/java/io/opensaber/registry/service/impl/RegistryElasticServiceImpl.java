package io.opensaber.registry.service.impl;

import akka.actor.ActorRef;
import akka.pattern.*;
import akka.util.Timeout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.opensaber.pojos.Request;
import io.opensaber.registry.dao.RegistryElasticDAO;
import io.opensaber.registry.service.RegistryElasticService;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.jena.ext.com.google.common.io.ByteStreams;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.*;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.lucene.search.join.ScoreMode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.ekstep.common.dto.Response;
import org.ekstep.common.dto.ResponseParams;
import org.ekstep.common.exception.ClientException;
import org.ekstep.common.exception.ResponseCode;
import org.ekstep.compositesearch.enums.CompositeSearchErrorCodes;
import org.ekstep.compositesearch.enums.CompositeSearchParams;
import org.ekstep.compositesearch.enums.Modes;
import org.ekstep.compositesearch.enums.SearchActorNames;
import org.ekstep.search.actor.SearchManager;
import org.ekstep.search.router.SearchActorPool;
import org.ekstep.search.router.SearchRequestRouterPool;
import org.ekstep.searchindex.dto.SearchDTO;
import org.ekstep.searchindex.elasticsearch.ElasticSearchUtil;
import org.ekstep.searchindex.processor.SearchProcessor;
import org.ekstep.searchindex.util.CompositeSearchConstants;
import org.ekstep.searchindex.util.ObjectDefinitionCache;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;/*
import scala.annotation.meta.param;*/
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RegistryElasticServiceImpl implements RegistryElasticService {

    @Autowired
    private RegistryElasticDAO registryElasticDAO;

    @Value("${registry.context.base}")
    private String registryContext;

    @Autowired
    private Gson gson;

    @Value("${frame.file}")
    private String frameFile;

    @Value("${audit.frame.file}")
    private String auditFrameFile;


    @Value("#{'${search.fields.query}'.split(',')}")
    private List<String> searchFieldQuery;

    private Type mapType = new TypeToken<Map<String, Object>>(){}.getType();

    private Type lstmapType = new TypeToken<List<Map<String, Object>>>(){}.getType();

    ObjectMapper objectMapper = new ObjectMapper();


    private boolean relevanceSort = false;

    @Override
    @Async
    public void addDocument(String index, String docType, String docID, String document) {
        registryElasticDAO.addDocumentId(index, docType, docID, document);
    }

    @Override
    public void addIndex(String Index) {
        //registryElasticDAO.addIndex();
    }

    @Override
    public void createClient(String index, String connectionInfo) {
        // registryElasticDAO.createClient(String index, String connectionInfo);
    }

    @Override
    public void addDocument(Request requestModel, String entityId) {

        try{

            JsonObject json = gson.fromJson(String.valueOf(requestModel.getRequestMap().get("dataObject")), JsonObject.class);
            json.addProperty("id",entityId);
            //String jsonLdContent = entityId, gson.toJson(json,JsonObject.class );
            Map<String, Object> jsonObject = (Map<String, Object>) JsonUtils.fromString(gson.toJson(json,JsonObject.class));
            JsonLdOptions options = new JsonLdOptions();
            options.setCompactArrays(true);
            //options.setExpandContext(ctx);
            options.setRequireAll(true);

            String expandedJsonLd = gson.toJson(JsonLdProcessor.expand(jsonObject, options),lstmapType);
            ObjectMapper mapper =  new ObjectMapper();
            List<Map<String, Object>> doc = mapper.readValue(expandedJsonLd, new TypeReference<List<Map<String, Object>>>() {
            });
            Map<String, Object> result = new HashMap<>();
            doc.stream().forEach(map -> {
                result.putAll(map.entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> (Object) entry.getValue())));
            });
            System.out.println("expanded json:"+expandedJsonLd);
            System.out.println("expanded map:"+result);
            String con = gson.toJson(result);
            System.out.println("expanded map to string:"+con);
            addDocument("teacher", "doc", entityId, gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Object> search(Request requestModel) throws Exception {
        boolean simpleSearch = false;
        JsonObject json = gson.fromJson(String.valueOf(requestModel.getRequestMap().get("dataObject")), JsonObject.class);
        // json.addProperty("id",entityId);
        //String jsonLdContent = entityId, gson.toJson(json,JsonObject.class );
        Map<String, Object> jsonObject = null;
        try {
            jsonObject = (Map<String, Object>) JsonUtils.fromString(gson.toJson(json,JsonObject.class));
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonLdOptions options = new JsonLdOptions();
        options.setCompactArrays(true);
        //options.setExpandContext(ctx);
        options.setRequireAll(true);

        List<Object> obj = JsonLdProcessor.expand(jsonObject, options);
        String expandedJsonLd = gson.toJson(JsonLdProcessor.expand(jsonObject, options),lstmapType);
        List<Map<String, Object>> doc = objectMapper.readValue(expandedJsonLd, new TypeReference<List<Map<String, Object>>>() {
        });
        Map<String, Object> result = new HashMap<>();
        doc.stream().forEach(map -> {
            result.putAll(map.entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey(), entry -> (Object) entry.getValue())));
        });
        Map<String, Object> reqMap = new HashMap<>();
        Map<String, Object> filterMap = new HashMap<>();
        for(String key: result.keySet()){
            if(key!="@type"){
                if(result.get(key) instanceof  String){
                    filterMap.put(key,result.get(key));
                } else if(result.get(key) instanceof  ArrayList){
                    ArrayList<Map> rsultList1 = (ArrayList<Map>)result.get(key);
                    for(Map searchMap1 : rsultList1){
                        for(Object searchKey1 :searchMap1.keySet()){
                            if(!(searchMap1.get(searchKey1) instanceof ArrayList)){
                                filterMap.put(key+"."+searchKey1.toString(),searchMap1.get(searchKey1.toString()));
                            } else {
                                ArrayList<Map> rsultList2 = (ArrayList<Map>)searchMap1.get(searchKey1);
                                for(Map searchMap2 : rsultList2) {
                                    for (Object searchKey2 : searchMap2.keySet()) {
                                        if(searchKey2.toString() !="@type") {
                                            filterMap.put(key + "." + searchKey1.toString()+"."+searchKey2.toString(), searchMap2.get(searchKey2.toString()));
                                        }
                                    }
                                }
                            }

                        }
                    }

                }

            }
        }
        if(filterMap.size() == 1){
            simpleSearch = true;
        }
        try {
            if(simpleSearch){
                for(String key: result.keySet()){
                    if(key!="@type"){
                        if(result.get(key) instanceof String){
                            reqMap.put("query",result.get(key));
                        } else {
                            ArrayList<Map> rsultList = (ArrayList<Map>)result.get(key);
                            for(Map searchMap : rsultList){
                                for(Object searchKey :searchMap.keySet()){
                                    reqMap.put("query",searchMap.get(searchKey.toString()));
                                }
                            }
                        }

                    }
                }
            } else {
                reqMap.put("filters",filterMap);
            }
        } catch(Exception e){
            e.printStackTrace();
        }


        /*if(result.containsKey("@type")){
            if(null != result.get("@type")){
                List typeLst = (List)result.get("@type");
                for(Object type:typeLst) {
                    reqMap.put("query", type.toString());
                }
            }
        }*/
        reqMap.put("filters",filterMap);
        org.ekstep.common.dto.Request searchRequest = new org.ekstep.common.dto.Request();
        //Request searchRequest = new Request();
        searchRequest.setRequest(reqMap);
        /*searchRequest.sx
        SearchDTO searchDTO = getSearchDTO(searchRequest);
        Future<Map<String, Object>> searchResult = processor.processSearch(searchDTO, true);*/
        SearchProcessor searchProcessor = new SearchProcessor("teacher");
        final List<Map<String, Object>> groupByFinalList = new ArrayList();
        // SearchManager searchManager = new SearchManager();
        // SearchDTO searchDTO = searchManager.getSearchDTO(searchRequest);


            SearchRequestRouterPool.init();
            ActorRef router = SearchRequestRouterPool.getRequestRouter();
            try {
                Future<Object> future = Patterns.ask(router, searchRequest, SearchRequestRouterPool.REQ_TIMEOUT);
                Object obj1 = Await.result(future, SearchRequestRouterPool.WAIT_TIMEOUT.duration());
                if (obj1 instanceof Response) {
                    return  ((Response) obj1).getResult();
                } else {
                    //return ERROR(CompositeSearchErrorCodes.SYSTEM_ERROR.name(), "System Error", ResponseCode.SERVER_ERROR);
                }
            } catch (Exception e) {
               // return ERROR(CompositeSearchErrorCodes.SYSTEM_ERROR.name(), e.getMessage(), ResponseCode.SERVER_ERROR);
            }



        ActorRef searchManager = SearchActorPool.getActorRefFromPool(SearchActorNames.SEARCH_MANAGER.name());
        Patterns.ask(searchManager, searchRequest, new Timeout(10, TimeUnit.SECONDS));
        //SearchSourceBuilder ssb = searchProcessor.processSearchQuery(searchDTO, groupByFinalList,true);
        //SearchSourceBuilder ssb = searchProcessor.processSearchQuery(searchDTO, groupByFinalList,true);
        //List<Object> elasticSearchResponse =  registryElasticDAO.search("teacher", "doc",ssb);
        JsonLDWriteContext ctx = new JsonLDWriteContext();
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(auditFrameFile);
        //InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
        String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);

        ctx.setFrame(gson.fromJson(fileString,JsonObject.class));
        Map<String, Object> responseLstMap = JsonLdProcessor.compact(null,gson.fromJson(json.get("@context"),Map.class),new JsonLdOptions());

        //Map<String, Object> responseLstMap = JsonLdProcessor.compact(elasticSearchResponse,gson.fromJson(json.get("@context"),Map.class),new JsonLdOptions());

       // List<Map<String, Object>> responseLst = new LinkedList<Map<String, Object>>();
       /* for(Object map : elasticSearchResponse){
            json.get("@context");
            Map<String, Object> mappp = objectMapper.convertValue(map, HashMap.class);

            //mappp.put("@context",json.get("@context"));
            Map<String, Object> mapp = JsonLdProcessor.compact(mappp,gson.fromJson(json.get("@context"),Map.class),new JsonLdOptions());
           /* mapp.stream().forEach(map -> {*/
           /*     result.putAll(map.entrySet().stream()*/
           /*             .collect(Collectors.toMap(entry -> entry.getKey(), entry -> (Object) entry.getValue())));*/
           /* });*/
           /* //responseLst.add(mapp);*/
       // }
        //JsonLdProcessor.
       // JsonLdProcessor.flatten(gson.fromJson());

        /*SearchManager searchMg = new SearchManager();
        searchMg.getSearchDTO();*/
        return responseLstMap;

    }

    /*private SearchSourceBuilder processSearchQuery(SearchDTO searchDTO, List<Map<String, Object>> groupByFinalList, boolean sortBy) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        List<String> fields = searchDTO.getFields();
        if (null != fields && !fields.isEmpty()) {
            fields.add("objectType");
            fields.add("identifier");
            searchSourceBuilder.fetchSource((String[])fields.toArray(new String[fields.size()]), (String[])null);
        }

        Iterator queryItr;
        if (searchDTO.getFacets() != null && groupByFinalList != null) {
            queryItr = searchDTO.getFacets().iterator();

            while(queryItr.hasNext()) {
                String facet = (String)queryItr.next();
                Map<String, Object> groupByMap = new HashMap();
                groupByMap.put("groupByParent", facet);
                groupByFinalList.add(groupByMap);
            }
        }

        searchSourceBuilder.size(searchDTO.getLimit());
        searchSourceBuilder.from(searchDTO.getOffset());
        //query = null;
        QueryBuilder query = null;
        if (searchDTO.isFuzzySearch()) {
            query = this.prepareFilteredSearchQuery(searchDTO);
            this.relevanceSort = true;
        } else {
            query = this.prepareSearchQuery(searchDTO);
        }


        searchSourceBuilder.query(query);
        //Not necessary for registry, as of now we are not using sorting(appending raw keyword, which is useful for LP)
         *//*if (sortBy && !this.relevanceSort && (null == searchDTO.getSoftConstraints() || searchDTO.getSoftConstraints().isEmpty())) {
            Map<String, String> sorting = searchDTO.getSortBy();
            if (sorting == null || ((Map)sorting).isEmpty()) {
                sorting = new HashMap();
                ((Map)sorting).put("name", "asc");
                ((Map)sorting).put("lastUpdatedOn", "desc");
            }

            Iterator var12 = ((Map)sorting).keySet().iterator();

            while(var12.hasNext()) {
                String key = (String)var12.next();
                searchSourceBuilder.sort(key + ".raw", this.getSortOrder((String)((Map)sorting).get(key)));
            }
        }*//*

        this.setAggregations(groupByFinalList, searchSourceBuilder);
        searchSourceBuilder.trackScores(true);
        return searchSourceBuilder;
    }*/

    /*private QueryBuilder prepareFilteredSearchQuery(SearchDTO searchDTO) {
        List<FilterFunctionBuilder> filterFunctionBuilder = new ArrayList();
        Map<String, Float> weightages = (Map)searchDTO.getAdditionalProperty("weightagesMap");
        if (weightages == null) {
            weightages = new HashMap();
            ((Map)weightages).put("default_weightage", 1.0F);
        }

        List<String> querySearchFeilds = ElasticSearchUtil.getQuerySearchFields();
        List<Map> properties = searchDTO.getProperties();
        Iterator var6 = properties.iterator();

        while(var6.hasNext()) {
            Map<String, Object> property = (Map)var6.next();
            String opertation = (String)property.get("operation");

            List values;
            try {
                values = (List)property.get("values");
            } catch (Exception var14) {
                values = Arrays.asList(property.get("values"));
            }

            String propertyName = (String)property.get("propertyName");
            if (propertyName.equals("*")) {
                this.relevanceSort = true;
                propertyName = "all_fields";
                filterFunctionBuilder.add(new FilterFunctionBuilder(this.getAllFieldsPropertyQuery(values), ScoreFunctionBuilders.weightFactorFunction((Float)((Map)weightages).get("default_weightage"))));
            } else {
                propertyName = propertyName + ".raw";
                float weight = this.getweight(querySearchFeilds, propertyName);
                byte var13 = -1;
                switch(opertation.hashCode()) {
                    case -1294237072:
                        if (opertation.equals("NT_LIKE")) {
                            var13 = 5;
                        }
                        break;
                    case 60:
                        if (opertation.equals("<")) {
                            var13 = 12;
                        }
                        break;
                    case 62:
                        if (opertation.equals(">")) {
                            var13 = 10;
                        }
                        break;
                    case 1921:
                        if (opertation.equals("<=")) {
                            var13 = 13;
                        }
                        break;
                    case 1983:
                        if (opertation.equals(">=")) {
                            var13 = 11;
                        }
                        break;
                    case 2220:
                        if (opertation.equals("EQ")) {
                            var13 = 0;
                        }
                        break;
                    case 2226:
                        if (opertation.equals("EW")) {
                            var13 = 2;
                        }
                        break;
                    case 2660:
                        if (opertation.equals("SW")) {
                            var13 = 6;
                        }
                        break;
                    case 2336663:
                        if (opertation.equals("LIKE")) {
                            var13 = 3;
                        }
                        break;
                    case 74630597:
                        if (opertation.equals("NT_EQ")) {
                            var13 = 1;
                        }
                        break;
                    case 74630718:
                        if (opertation.equals("NT_IN")) {
                            var13 = 9;
                        }
                        break;
                    case 215180831:
                        if (opertation.equals("CONTAINS")) {
                            var13 = 4;
                        }
                        break;
                    case 1592094965:
                        if (opertation.equals("NT_EXISTS")) {
                            var13 = 8;
                        }
                        break;
                    case 2058938460:
                        if (opertation.equals("EXISTS")) {
                            var13 = 7;
                        }
                }

                switch(var13) {
                    case 0:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getMustTermQuery(propertyName, values, true), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 1:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getMustTermQuery(propertyName, values, true), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 2:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getRegexQuery(propertyName, values), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 3:
                    case 4:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getMatchPhraseQuery(propertyName, values, true), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 5:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getMatchPhraseQuery(propertyName, values, false), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 6:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getMatchPhrasePrefixQuery(propertyName, values), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 7:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getExistsQuery(propertyName, values, true), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 8:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getExistsQuery(propertyName, values, false), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 9:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getNotInQuery(propertyName, values), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 10:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getRangeQuery(propertyName, values, ">"), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 11:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getRangeQuery(propertyName, values, ">="), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 12:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getRangeQuery(propertyName, values, "<"), ScoreFunctionBuilders.weightFactorFunction(weight)));
                        break;
                    case 13:
                        filterFunctionBuilder.add(new FilterFunctionBuilder(this.getRangeQuery(propertyName, values, "<="), ScoreFunctionBuilders.weightFactorFunction(weight)));
                }
            }
        }

        FunctionScoreQueryBuilder queryBuilder = QueryBuilders.functionScoreQuery((FilterFunctionBuilder[])filterFunctionBuilder.toArray(new FilterFunctionBuilder[filterFunctionBuilder.size()])).boostMode(CombineFunction.REPLACE).scoreMode(org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode.SUM);
        return queryBuilder;
    }

    private QueryBuilder prepareSearchQuery(SearchDTO searchDTO) {
        BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        QueryBuilder queryBuilder = null;
        String totalOperation = searchDTO.getOperation();
        List<Map> properties = searchDTO.getProperties();
        Iterator var6 = properties.iterator();

        while(var6.hasNext()) {
            Map<String, Object> property = (Map)var6.next();
            String opertation = (String)property.get("operation");

            List values;
            try {
                values = (List)property.get("values");
            } catch (Exception var13) {
                values = Arrays.asList(property.get("values"));
            }

            String propertyName = (String)property.get("propertyName");
            if (propertyName.equals("*")) {
                this.relevanceSort = true;
                propertyName = "all_fields";
                queryBuilder = this.getAllFieldsPropertyQuery(values);
                boolQuery.must(queryBuilder);
            } else {
                //propertyName = propertyName + ".raw";
                byte var12 = -1;
                switch(opertation.hashCode()) {
                    case -1294237072:
                        if (opertation.equals("NT_LIKE")) {
                            var12 = 6;
                        }
                        break;
                    case 60:
                        if (opertation.equals("<")) {
                            var12 = 12;
                        }
                        break;
                    case 62:
                        if (opertation.equals(">")) {
                            var12 = 10;
                        }
                        break;
                    case 1921:
                        if (opertation.equals("<=")) {
                            var12 = 13;
                        }
                        break;
                    case 1983:
                        if (opertation.equals(">=")) {
                            var12 = 11;
                        }
                        break;
                    case 2220:
                        if (opertation.equals("EQ")) {
                            var12 = 0;
                        }
                        break;
                    case 2226:
                        if (opertation.equals("EW")) {
                            var12 = 3;
                        }
                        break;
                    case 2660:
                        if (opertation.equals("SW")) {
                            var12 = 7;
                        }
                        break;
                    case 2336663:
                        if (opertation.equals("LIKE")) {
                            var12 = 4;
                        }
                        break;
                    case 74630597:
                        if (opertation.equals("NT_EQ")) {
                            var12 = 1;
                        }
                        break;
                    case 74630718:
                        if (opertation.equals("NT_IN")) {
                            var12 = 2;
                        }
                        break;
                    case 108280125:
                        if (opertation.equals("range")) {
                            var12 = 14;
                        }
                        break;
                    case 215180831:
                        if (opertation.equals("CONTAINS")) {
                            var12 = 5;
                        }
                        break;
                    case 1592094965:
                        if (opertation.equals("NT_EXISTS")) {
                            var12 = 9;
                        }
                        break;
                    case 2058938460:
                        if (opertation.equals("EXISTS")) {
                            var12 = 8;
                        }
                }

                switch(var12) {
                    case 0:
                        queryBuilder = this.getMustTermQuery(propertyName, values, true);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 1:
                        queryBuilder = this.getMustTermQuery(propertyName, values, false);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 2:
                        queryBuilder = this.getNotInQuery(propertyName, values);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 3:
                        queryBuilder = this.getRegexQuery(propertyName, values);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 4:
                    case 5:
                        queryBuilder = this.getMatchPhraseQuery(propertyName, values, true);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 6:
                        queryBuilder = this.getMatchPhraseQuery(propertyName, values, false);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 7:
                        queryBuilder = this.getMatchPhrasePrefixQuery(propertyName, values);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 8:
                        queryBuilder = this.getExistsQuery(propertyName, values, true);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 9:
                        queryBuilder = this.getExistsQuery(propertyName, values, false);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 10:
                        queryBuilder = this.getRangeQuery(propertyName, values, ">");
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 11:
                        queryBuilder = this.getRangeQuery(propertyName, values, ">=");
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 12:
                        queryBuilder = this.getRangeQuery(propertyName, values, "<");
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 13:
                        queryBuilder = this.getRangeQuery(propertyName, values, "<=");
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                        break;
                    case 14:
                        queryBuilder = this.getRangeQuery(propertyName, values);
                        queryBuilder = this.checkNestedProperty(queryBuilder, propertyName);
                }

                if (totalOperation.equalsIgnoreCase("AND")) {
                    boolQuery.must(queryBuilder);
                } else {
                    boolQuery.should(queryBuilder);
                }
            }
        }

        Map<String, Object> softConstraints = searchDTO.getSoftConstraints();
        if (null != softConstraints && !softConstraints.isEmpty()) {
            boolQuery.should(this.getSoftConstraintQuery(softConstraints));
            searchDTO.setSortBy((Map)null);
        }

        return boolQuery;
    }

    private QueryBuilder getSoftConstraintQuery(Map<String, Object> softConstraints) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var3 = softConstraints.keySet().iterator();

        while(true) {
            while(var3.hasNext()) {
                String key = (String)var3.next();
                List<Object> data = (List)softConstraints.get(key);
                if (data.get(1) instanceof List) {
                    List<Object> dataList = (List)data.get(1);
                    Iterator var7 = dataList.iterator();

                    while(var7.hasNext()) {
                        Object value = var7.next();
                        queryBuilder.should(QueryBuilders.matchQuery(key + ".raw", value).boost(Integer.valueOf((Integer)data.get(0)).floatValue()));
                    }
                } else {
                    queryBuilder.should(QueryBuilders.matchQuery(key + ".raw", data.get(1)).boost(Integer.valueOf((Integer)data.get(0)).floatValue()));
                }
            }

            return queryBuilder;
        }
    }

    private QueryBuilder checkNestedProperty(QueryBuilder queryBuilder, String propertyName) {
        //This code not necessary for registry
       *//* if (propertyName.replaceAll(".raw", "").contains(".")) {
            queryBuilder = QueryBuilders.nestedQuery(propertyName.split("\\.")[0], (QueryBuilder)queryBuilder, ScoreMode.None);
        }*//*

        return (QueryBuilder)queryBuilder;
    }

    private void setAggregations(List<Map<String, Object>> groupByList, SearchSourceBuilder searchSourceBuilder) {
        TermsAggregationBuilder termBuilder = null;
        if (groupByList != null && !groupByList.isEmpty()) {
            for(Iterator var4 = groupByList.iterator(); var4.hasNext(); searchSourceBuilder.aggregation(termBuilder)) {
                Map<String, Object> groupByMap = (Map)var4.next();
                String groupByParent = (String)groupByMap.get("groupByParent");
                termBuilder = ((TermsAggregationBuilder)AggregationBuilders.terms(groupByParent).field(groupByParent + ".raw")).size(ElasticSearchUtil.defaultResultLimit);
                List<String> groupByChildList = (List)groupByMap.get("groupByChildList");
                if (groupByChildList != null && !groupByChildList.isEmpty()) {
                    Iterator var8 = groupByChildList.iterator();

                    while(var8.hasNext()) {
                        String childGroupBy = (String)var8.next();
                        termBuilder.subAggregation(((TermsAggregationBuilder)AggregationBuilders.terms(childGroupBy).field(childGroupBy + ".raw")).size(ElasticSearchUtil.defaultResultLimit));
                    }
                }
            }
        }

    }

    private SortOrder getSortOrder(String value) {
        return value.equalsIgnoreCase("asc") ? SortOrder.ASC : SortOrder.DESC;
    }

    private float getweight(List<String> querySearchFeilds, String propertyName) {
        float weight = 1.0F;
        if (querySearchFeilds.contains(propertyName)) {
            Iterator var4 = querySearchFeilds.iterator();

            while(var4.hasNext()) {
                String field = (String)var4.next();
                if (field.contains(propertyName)) {
                    weight = Float.parseFloat(org.apache.commons.lang.StringUtils.isNotBlank(field.split("^")[1]) ? field.split("^")[1] : "1.0");
                }
            }
        }

        return weight;
    }

    private QueryBuilder getAllFieldsPropertyQuery(List<Object> values) {

        //List<String> queryFields = ElasticSearchUtil.getQuerySearchFields();
        List<String> queryFields = getQuerySearchFields();
        Map<String, Float> queryFieldsMap = new HashMap();
        Iterator var4 = queryFields.iterator();

        while(var4.hasNext()) {
            String field = (String)var4.next();
            if (field.contains("^")) {
                queryFieldsMap.put(registryContext+field.split("\\^")[0], Float.valueOf(field.split("\\^")[1]));
            } else {
                queryFieldsMap.put(registryContext+field, 1.0F);
            }
        }

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var8 = values.iterator();

        while(var8.hasNext()) {
            Object value = var8.next();
            queryBuilder.should(QueryBuilders.multiMatchQuery(value, new String[0]).fields(queryFieldsMap).operator(Operator.AND).type(MultiMatchQueryBuilder.Type.CROSS_FIELDS).lenient(true));
        }

        return queryBuilder;
    }

    public List<String> getQuerySearchFields() {
        List<String> querySearchFields = searchFieldQuery;
        return querySearchFields;
    }

    private QueryBuilder getMustTermQuery(String propertyName, List<Object> values, boolean match) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var5 = values.iterator();

        while(var5.hasNext()) {
            Object value = var5.next();
            if (match) {
                queryBuilder.should(QueryBuilders.matchQuery(propertyName, value).operator(Operator.AND));
            } else {
                queryBuilder.mustNot(QueryBuilders.matchQuery(propertyName, value));
            }
        }

        return queryBuilder;
    }

    private QueryBuilder getRegexQuery(String propertyName, List<Object> values) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var4 = values.iterator();

        while(var4.hasNext()) {
            Object value = var4.next();
            String stringValue = String.valueOf(value);
            queryBuilder.should(QueryBuilders.regexpQuery(propertyName, ".*" + stringValue.toLowerCase()));
        }

        return queryBuilder;
    }

    private QueryBuilder getMatchPhrasePrefixQuery(String propertyName, List<Object> values) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var4 = values.iterator();

        while(var4.hasNext()) {
            Object value = var4.next();
            queryBuilder.should(QueryBuilders.prefixQuery(propertyName, ((String)value).toLowerCase()));
        }

        return queryBuilder;
    }

    private QueryBuilder getMatchPhraseQuery(String propertyName, List<Object> values, boolean match) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var5 = values.iterator();

        while(var5.hasNext()) {
            Object value = var5.next();
            String stringValue = String.valueOf(value);
            if (match) {
                queryBuilder.should(QueryBuilders.regexpQuery(propertyName, ".*" + stringValue.toLowerCase() + ".*"));
            } else {
                queryBuilder.mustNot(QueryBuilders.regexpQuery(propertyName, ".*" + stringValue.toLowerCase() + ".*"));
            }
        }

        return queryBuilder;
    }

    private QueryBuilder getExistsQuery(String propertyName, List<Object> values, boolean exists) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var5 = values.iterator();

        while(var5.hasNext()) {
            Object value = var5.next();
            if (exists) {
                queryBuilder.should(QueryBuilders.existsQuery(String.valueOf(value)));
            } else {
                queryBuilder.mustNot(QueryBuilders.existsQuery(String.valueOf(value)));
            }
        }

        return queryBuilder;
    }

    private QueryBuilder getNotInQuery(String propertyName, List<Object> values) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        queryBuilder.mustNot(QueryBuilders.termsQuery(propertyName, values));
        return queryBuilder;
    }

    private QueryBuilder getRangeQuery(String propertyName, List<Object> values) {
        RangeQueryBuilder queryBuilder = new RangeQueryBuilder(propertyName);
        Iterator var4 = values.iterator();

        while(true) {
            Map rangeMap;
            do {
                if (!var4.hasNext()) {
                    return queryBuilder;
                }

                Object value = var4.next();
                rangeMap = (Map)value;
            } while(rangeMap.isEmpty());

            Iterator var7 = rangeMap.keySet().iterator();

            while(var7.hasNext()) {
                String key = (String)var7.next();
                byte var10 = -1;
                switch(key.hashCode()) {
                    case 102680:
                        if (key.equals("gte")) {
                            var10 = 0;
                        }
                        break;
                    case 107485:
                        if (key.equals("lte")) {
                            var10 = 1;
                        }
                }

                switch(var10) {
                    case 0:
                        queryBuilder.from(rangeMap.get(key));
                        break;
                    case 1:
                        queryBuilder.to(rangeMap.get(key));
                }
            }
        }
    }

    private QueryBuilder getRangeQuery(String propertyName, List<Object> values, String opertation) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Iterator var5 = values.iterator();

        while(var5.hasNext()) {
            Object value = var5.next();
            byte var8 = -1;
            switch(opertation.hashCode()) {
                case 60:
                    if (opertation.equals("<")) {
                        var8 = 2;
                    }
                    break;
                case 62:
                    if (opertation.equals(">")) {
                        var8 = 0;
                    }
                    break;
                case 1921:
                    if (opertation.equals("<=")) {
                        var8 = 3;
                    }
                    break;
                case 1983:
                    if (opertation.equals(">=")) {
                        var8 = 1;
                    }
            }

            switch(var8) {
                case 0:
                    queryBuilder.should(QueryBuilders.rangeQuery(propertyName).gt(value));
                    break;
                case 1:
                    queryBuilder.should(QueryBuilders.rangeQuery(propertyName).gte(value));
                    break;
                case 2:
                    queryBuilder.should(QueryBuilders.rangeQuery(propertyName).lt(value));
                    break;
                case 3:
                    queryBuilder.should(QueryBuilders.rangeQuery(propertyName).lte(value));
            }
        }

        return queryBuilder;
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    *//*private SearchDTO getSearchDTO(Request request) throws Exception {
        SearchDTO searchObj = new SearchDTO();
        try {
            Map<String, Object> req = request.getRequestMap();
           // TelemetryManager.log("Search Request: ", req);
            String queryString = String.valueOf(req.get(CompositeSearchParams.query.name()));
            int limit = getLimitValue(req.get(CompositeSearchParams.limit.name()));
            Boolean fuzzySearch = (Boolean) request.get("fuzzy");
            if (null == fuzzySearch)
                fuzzySearch = false;
            Boolean wordChainsRequest = (Boolean) request.get("traversal");
            if (null == wordChainsRequest)
                wordChainsRequest = false;
            List<Map> properties = new ArrayList<Map>();
            Map<String, Object> filters = (Map<String, Object>) req.get(CompositeSearchParams.filters.name());
            if (null == filters)
                filters = new HashMap<>();
            if (filters.containsKey("tags")) {
                Object tags = filters.get("tags");
                if (null != tags) {
                    filters.remove("tags");
                    filters.put("keywords", tags);
                }
            }

            Object objectTypeFromFilter = filters.get(CompositeSearchParams.objectType.name());
            String objectType = null;
            if (objectTypeFromFilter != null) {
                if (objectTypeFromFilter instanceof List) {
                    List objectTypeList = (List) objectTypeFromFilter;
                    if (objectTypeList.size() > 0)
                        objectType = (String) objectTypeList.get(0);
                } else if (objectTypeFromFilter instanceof String) {
                    objectType = (String) objectTypeFromFilter;
                }
            }

            Object graphIdFromFilter = filters.get(CompositeSearchParams.graph_id.name());
            String graphId = null;
            if (graphIdFromFilter != null) {
                if (graphIdFromFilter instanceof List) {
                    List graphIdList = (List) graphIdFromFilter;
                    if (graphIdList.size() > 0)
                        graphId = (String) graphIdList.get(0);
                } else if (graphIdFromFilter instanceof String) {
                    graphId = (String) graphIdFromFilter;
                }
            }
            if (fuzzySearch && filters != null) {
                Map<String, Float> weightagesMap = new HashMap<String, Float>();
                weightagesMap.put("default_weightage", 1.0f);

                if (StringUtils.isNotBlank(objectType) && StringUtils.isNotBlank(graphId)) {
                    Map<String, Object> objDefinition = ObjectDefinitionCache.getMetaData(objectType, graphId);
                    // DefinitionDTO objDefinition =
                    // DefinitionCache.getDefinitionNode(graphId, objectType);
                    String weightagesString = (String) objDefinition.get("weightages");
                    if (StringUtils.isNotBlank(weightagesString)) {
                        weightagesMap = getWeightagesMap(weightagesString);
                    }
                }
                searchObj.addAdditionalProperty("weightagesMap", weightagesMap);
            }

            List<String> exists = null;
            Object existsObject = req.get(CompositeSearchParams.exists.name());
            if (existsObject instanceof List) {
                exists = (List<String>) existsObject;
            } else if (existsObject instanceof String) {
                exists = new ArrayList<String>();
                exists.add((String) existsObject);
            }

            List<String> notExists = null;
            Object notExistsObject = req.get(CompositeSearchParams.not_exists.name());
            if (notExistsObject instanceof List) {
                notExists = (List<String>) notExistsObject;
            } else if (notExistsObject instanceof String) {
                notExists = new ArrayList<String>();
                notExists.add((String) notExistsObject);
            }

            Map<String, Object> softConstraints = null;
            if (null != req.get(CompositeSearchParams.softConstraints.name())) {
                softConstraints = (Map<String, Object>) req.get(CompositeSearchParams.softConstraints.name());
            }

            String mode = (String) req.get(CompositeSearchParams.mode.name());
            if (null != mode && mode.equals(Modes.soft.name())
                    && (null == softConstraints || softConstraints.isEmpty())) {
                try {
                    Map<String, Object> metaData = ObjectDefinitionCache.getMetaData(objectType);
                    if (null != metaData.get("softConstraints")) {
                        org.codehaus.jackson.map.ObjectMapper mapper = new org.codehaus.jackson.map.ObjectMapper();
                        String constraintString = (String) metaData.get("softConstraints");
                        softConstraints = mapper.readValue(constraintString, Map.class);
                    }
                } catch (Exception e) {
                   // TelemetryManager.warn("Invalid soft Constraints" + e.getMessage());
                }
            }
            //TelemetryManager.log("Soft Constraints with only Mode: ", softConstraints);
            if (null != softConstraints && !softConstraints.isEmpty()) {
                Map<String, Object> softConstraintMap = new HashMap<>();
                //TelemetryManager.log("SoftConstraints:", softConstraints);
                try {
                    for (String key : softConstraints.keySet()) {
                        if (filters.containsKey(key) && null != filters.get(key)) {
                            List<Object> data = new ArrayList<>();
                            Integer boost = 1;
                            Object boostValue = softConstraints.get(key);
                            if (null != boostValue) {
                                try {
                                    boost = Integer.parseInt(boostValue.toString());
                                } catch (Exception e) {
                                    boost = 1;
                                }
                            }
                            data.add(boost);
                            if (filters.get(key) instanceof Map) {
                                data.add(((Map) filters.get(key)).values().toArray()[0]);
                            } else {
                                data.add(filters.get(key));
                            }

                            softConstraintMap.put(key, data);
                            filters.remove(key);
                        }
                    }
                } catch (Exception e) {
                    //TelemetryManager.warn("Invalid soft Constraints: " + e.getMessage());
                }
                searchObj.setSoftConstraints(softConstraintMap);
            }
           // TelemetryManager.log("SoftConstraints" + searchObj.getSoftConstraints());

            List<String> fieldsSearch = getList(req.get(CompositeSearchParams.fields.name()));
            List<String> facets = getList(req.get(CompositeSearchParams.facets.name()));
            Map<String, String> sortBy = (Map<String, String>) req.get(CompositeSearchParams.sort_by.name());
            properties.addAll(getAdditionalFilterProperties(exists, CompositeSearchParams.exists.name()));
            properties.addAll(getAdditionalFilterProperties(notExists, CompositeSearchParams.not_exists.name()));
            // Changing fields to null so that search all fields but returns
            // only the fields specified
            if(!queryString.equals("null")) {
                properties.addAll(getSearchQueryProperties(queryString, null));
            }
            properties.addAll(getSearchFilterProperties(filters, wordChainsRequest));
            searchObj.setSortBy(sortBy);
            searchObj.setFacets(facets);
            searchObj.setProperties(properties);
            searchObj.setLimit(limit);
            searchObj.setFields(fieldsSearch);
            searchObj.setOperation(CompositeSearchConstants.SEARCH_OPERATION_AND);

            if (null != req.get(CompositeSearchParams.offset.name())) {
                int offset = (Integer) req.get(CompositeSearchParams.offset.name());
                //TelemetryManager.log("Offset: " + offset);
                searchObj.setOffset(offset);
            }

            if (fuzzySearch != null) {
                searchObj.setFuzzySearch(fuzzySearch);
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
            throw new ClientException(CompositeSearchErrorCodes.ERR_COMPOSITE_SEARCH_INVALID_PARAMS.name(),
                    "Invalid Input.", e);
        }
        return searchObj;
    }*//*

    @SuppressWarnings("unchecked")
    private Map<String, Float> getWeightagesMap(String weightagesString)
            throws JsonParseException, JsonMappingException, IOException {
        Map<String, Float> weightagesMap = new HashMap<String, Float>();
        org.codehaus.jackson.map.ObjectMapper mapper = new org.codehaus.jackson.map.ObjectMapper();
        if (weightagesString != null && !weightagesString.isEmpty()) {
            Map<String, Object> weightagesRequestMap = mapper.readValue(weightagesString,
                    new org.codehaus.jackson.type.TypeReference<Map<String, Object>>() {
                    });

            for (Map.Entry<String, Object> entry : weightagesRequestMap.entrySet()) {
                Float weightage = Float.parseFloat(entry.getKey());
                if (entry.getValue() instanceof List) {
                    List<String> fields = (List<String>) entry.getValue();
                    for (String field : fields) {
                        weightagesMap.put(field, weightage);
                    }
                } else {
                    String field = (String) entry.getValue();
                    weightagesMap.put(field, weightage);
                }
            }
        }
        return weightagesMap;
    }

    private List<Map<String, Object>> getAdditionalFilterProperties(List<String> fieldList, String operation) {
        List<Map<String, Object>> properties = new ArrayList<Map<String, Object>>();
        if (fieldList != null) {
            for (String field : fieldList) {
                String searchOperation = "";
                switch (operation) {
                    case "exists": {
                        searchOperation = CompositeSearchConstants.SEARCH_OPERATION_EXISTS;
                        break;
                    }
                    case "not_exists": {
                        searchOperation = CompositeSearchConstants.SEARCH_OPERATION_NOT_EXISTS;
                        break;
                    }
                }
                Map<String, Object> property = new HashMap<String, Object>();
                property.put(CompositeSearchParams.operation.name(), searchOperation);
                property.put(CompositeSearchParams.propertyName.name(), field);
                property.put(CompositeSearchParams.values.name(), Arrays.asList(field));
                properties.add(property);
            }
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Object param) {
        List<String> paramList;
        try {
            paramList = (List<String>) param;
        } catch (Exception e) {
            String str = (String) param;
            paramList = Arrays.asList(str);
        }
        return paramList;
    }

    private Integer getLimitValue(Object limit) {
        int i = 100;
        if (null != limit) {
            try {
                i = (int) limit;
            } catch (Exception e) {
                i = new Long(limit.toString()).intValue();
            }
        }
        return i;
    }

    private List<Map<String, Object>> getSearchQueryProperties(String queryString, List<String> fields) {
        List<Map<String, Object>> properties = new ArrayList<Map<String, Object>>();
        if (queryString != null && !queryString.isEmpty()) {
            if (null == fields || fields.size() <= 0) {
                Map<String, Object> property = new HashMap<String, Object>();
                property.put(CompositeSearchParams.operation.name(), CompositeSearchConstants.SEARCH_OPERATION_LIKE);
                property.put(CompositeSearchParams.propertyName.name(), "*");
                property.put(CompositeSearchParams.values.name(), Arrays.asList(queryString));
                properties.add(property);
            } else {
                for (String field : fields) {
                    Map<String, Object> property = new HashMap<String, Object>();
                    property.put(CompositeSearchParams.operation.name(),
                            CompositeSearchConstants.SEARCH_OPERATION_LIKE);
                    property.put(CompositeSearchParams.propertyName.name(), field);
                    property.put(CompositeSearchParams.values.name(), Arrays.asList(queryString));
                    properties.add(property);
                }
            }
        }
        return properties;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<Map<String, Object>> getSearchFilterProperties(Map<String, Object> filters, Boolean traversal)
            throws Exception {
        List<Map<String, Object>> properties = new ArrayList<Map<String, Object>>();
        boolean compatibilityFilter = false;
        boolean isContentSearch = false;
        boolean statusFilter = false;
        if (null != filters && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                if ("identifier".equalsIgnoreCase(entry.getKey())) {
                    List ids = new ArrayList<>();
                    if (entry.getValue() instanceof String) {
                        ids.add(entry.getValue());
                    } else {
                        ids = (List<String>) entry.getValue();
                    }
                    List<String> identifiers = new ArrayList<>();
                    identifiers.addAll((List<String>) (List<?>) ids);
                    for (Object id : ids) {
                        identifiers.add(id + ".img");
                    }
                    entry.setValue(identifiers);
                }
                if (CompositeSearchParams.objectType.name().equals(entry.getKey())) {
                    List value = new ArrayList<>();
                    if (entry.getValue() instanceof String) {
                        value.add(entry.getValue());
                    } else {
                        value = (List<String>) entry.getValue();
                    }
                    List<String> objectTypes = new ArrayList<>();
                    objectTypes.addAll((List<String>) (List<?>) value);
                    for (Object val : value) {
                        objectTypes.add(val + "Image");
                    }
                    entry.setValue(objectTypes);
                }
                Object filterObject = entry.getValue();
                if (filterObject instanceof Map) {
                    Map<String, Object> filterMap = (Map<String, Object>) filterObject;
                    if (!filterMap.containsKey(CompositeSearchConstants.SEARCH_OPERATION_RANGE_MIN)
                            && !filterMap.containsKey(CompositeSearchConstants.SEARCH_OPERATION_RANGE_MAX)) {
                        for (Map.Entry<String, Object> filterEntry : filterMap.entrySet()) {
                            Map<String, Object> property = new HashMap<String, Object>();
                            property.put(CompositeSearchParams.values.name(), filterEntry.getValue());
                            property.put(CompositeSearchParams.propertyName.name(), entry.getKey());
                            switch (filterEntry.getKey()) {
                                case "startsWith": {
                                    property.put(CompositeSearchParams.operation.name(),
                                            CompositeSearchConstants.SEARCH_OPERATION_STARTS_WITH);
                                    break;
                                }
                                case "endsWith": {
                                    property.put(CompositeSearchParams.operation.name(),
                                            CompositeSearchConstants.SEARCH_OPERATION_ENDS_WITH);
                                    break;
                                }
                                case CompositeSearchConstants.SEARCH_OPERATION_NOT_EQUAL_OPERATOR:
                                case CompositeSearchConstants.SEARCH_OPERATION_NOT_EQUAL_TEXT:
                                case CompositeSearchConstants.SEARCH_OPERATION_NOT_EQUAL_TEXT_LOWERCASE:
                                case CompositeSearchConstants.SEARCH_OPERATION_NOT_EQUAL_TEXT_UPPERCASE:
                                    property.put(CompositeSearchParams.operation.name(),
                                            CompositeSearchConstants.SEARCH_OPERATION_NOT_EQUAL);
                                    break;
                                case CompositeSearchConstants.SEARCH_OPERATION_NOT_IN_OPERATOR:
                                    property.put(CompositeSearchParams.operation.name(),
                                            CompositeSearchConstants.SEARCH_OPERATION_NOT_IN);
                                    break;
                                case CompositeSearchConstants.SEARCH_OPERATION_GREATER_THAN:
                                case CompositeSearchConstants.SEARCH_OPERATION_GREATER_THAN_EQUALS:
                                case CompositeSearchConstants.SEARCH_OPERATION_LESS_THAN_EQUALS:
                                case CompositeSearchConstants.SEARCH_OPERATION_LESS_THAN: {
                                    property.put(CompositeSearchParams.operation.name(), filterEntry.getKey());
                                    break;
                                }
                                case "value":
                                case CompositeSearchConstants.SEARCH_OPERATION_CONTAINS_OPERATOR: {
                                    property.put(CompositeSearchParams.operation.name(),
                                            CompositeSearchConstants.SEARCH_OPERATION_CONTAINS);
                                    break;
                                }
                                default: {
                                    throw new Exception("Unsupported operation");
                                }
                            }
                            properties.add(property);
                        }
                    } else {
                        Map<String, Object> property = new HashMap<String, Object>();
                        Map<String, Object> rangeMap = new HashMap<String, Object>();
                        Object minFilterValue = filterMap.get(CompositeSearchConstants.SEARCH_OPERATION_RANGE_MIN);
                        if (minFilterValue != null) {
                            rangeMap.put(CompositeSearchConstants.SEARCH_OPERATION_RANGE_GTE, minFilterValue);
                        }
                        Object maxFilterValue = filterMap.get(CompositeSearchConstants.SEARCH_OPERATION_RANGE_MAX);
                        if (maxFilterValue != null) {
                            rangeMap.put(CompositeSearchConstants.SEARCH_OPERATION_RANGE_LTE, maxFilterValue);
                        }
                        property.put(CompositeSearchParams.values.name(), rangeMap);
                        property.put(CompositeSearchParams.propertyName.name(), entry.getKey());
                        property.put(CompositeSearchParams.operation.name(),
                                CompositeSearchConstants.SEARCH_OPERATION_RANGE);
                        properties.add(property);
                    }
                } else {
                    boolean emptyVal = false;
                    if (null == filterObject) {
                        emptyVal = true;
                    } else if (filterObject instanceof List) {
                        if (((List) filterObject).size() <= 0)
                            emptyVal = true;
                    } else if (filterObject instanceof Object[]) {
                        if (((Object[]) filterObject).length <= 0)
                            emptyVal = true;
                    }
                    if (!emptyVal) {
                        Map<String, Object> property = new HashMap<String, Object>();
                        property.put(CompositeSearchParams.values.name(), entry.getValue());
                        property.put(CompositeSearchParams.propertyName.name(), entry.getKey());
                        property.put(CompositeSearchParams.operation.name(),
                                CompositeSearchConstants.SEARCH_OPERATION_EQUAL);
                        properties.add(property);
                    }
                }
                if (StringUtils.equals(CompositeSearchParams.objectType.name(), entry.getKey())) {
                    String objectType = null;
                    if (filterObject instanceof List) {
                        List objectTypeList = (List) filterObject;
                        if (objectTypeList.size() == 1)
                            objectType = (String) objectTypeList.get(0);
                    } else if (filterObject instanceof Object[]) {
                        Object[] objectTypeList = (Object[]) filterObject;
                        if (objectTypeList.length == 1)
                            objectType = (String) objectTypeList[0];
                    } else if (filterObject instanceof String) {
                        objectType = (String) filterObject;
                    }
                    if (StringUtils.equalsIgnoreCase(CompositeSearchParams.Content.name(), objectType))
                        isContentSearch = true;
                }
                if (StringUtils.equals("status", entry.getKey()))
                    statusFilter = true;
                if (StringUtils.equals(CompositeSearchParams.compatibilityLevel.name(), entry.getKey()))
                    compatibilityFilter = true;
            }
        }

        if (!compatibilityFilter && isContentSearch && !traversal) {
            Map<String, Object> property = new HashMap<String, Object>();
            property.put(CompositeSearchParams.propertyName.name(), CompositeSearchParams.compatibilityLevel.name());
            property.put(CompositeSearchParams.operation.name(), CompositeSearchConstants.SEARCH_OPERATION_EQUAL);
            property.put(CompositeSearchParams.values.name(), Arrays.asList(new Integer[] { 1 }));
            properties.add(property);
        }

        //Not necessary for Registry elastic search
        *//*if (!statusFilter && !traversal) {
            Map<String, Object> property = new HashMap<String, Object>();
            property.put(CompositeSearchParams.operation.name(), CompositeSearchConstants.SEARCH_OPERATION_EQUAL);
            property.put(CompositeSearchParams.propertyName.name(), "status");
            property.put(CompositeSearchParams.values.name(), Arrays.asList(new String[] { "Live" }));
            properties.add(property);
        }*//*
        return properties;
    }*/

    /**
     * Error.
     *
     * @param errorCode the error code
     * @param errorMessage the error message
     * @param responseCode the response code
     * @return the response
     */

    protected Response ERROR(String errorCode, String errorMessage, ResponseCode responseCode) {
        Response response = new Response();
        response.setParams(getErrorStatus(errorCode, errorMessage));
        response.setResponseCode(responseCode);
        return response;
    }

    /**
     * Gets the error status.
     *
     * @param errorCode the error code
     * @param errorMessage the error message
     * @return the error status
     */

    private ResponseParams getErrorStatus(String errorCode, String errorMessage) {
        ResponseParams params = new ResponseParams();
        params.setErr(errorCode);
        params.setStatus(ResponseParams.StatusType.failed.name());
        params.setErrmsg(errorMessage);
        return params;
    }

}
