package io.opensaber.registry.service.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.opensaber.pojos.Request;
import io.opensaber.registry.dao.RegistryElasticDAO;
import io.opensaber.registry.service.RegistryElasticService;
import org.apache.jena.ext.com.google.common.io.ByteStreams;
import org.apache.jena.riot.JsonLDWriteContext;
import org.ekstep.common.dto.Response;
import org.ekstep.common.dto.ResponseParams;
import org.ekstep.common.exception.ClientException;
import org.ekstep.common.exception.ResponseCode;
import org.ekstep.common.exception.ServerException;
import org.ekstep.compositesearch.enums.CompositeSearchErrorCodes;
import org.ekstep.compositesearch.enums.SearchActorNames;
import org.ekstep.compositesearch.enums.SearchOperations;
import org.ekstep.search.router.SearchActorPool;
import org.ekstep.search.router.SearchRequestRouterPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RegistryElasticServiceImpl implements RegistryElasticService {

    @Autowired
    private RegistryElasticDAO registryElasticDAO;

    @Value("${registry.context.base}")
    private String registryContext;

    @Value("${elastic.index}")
    private String elasticIndex;

    @Value("${elastic.connection_url}")
    private String connection_url;

    @Autowired
    private Gson gson;

    @Value("${frame.file}")
    private String frameFile;

    @Value("${audit.frame.file}")
    private String auditFrameFile;


    @Value("#{'${search.fields.query}'.split(',')}")
    private List<String> searchFieldQuery;

    private static ActorRef actor;

    private Type mapType = new TypeToken<Map<String, Object>>(){}.getType();

    private Type lstmapType = new TypeToken<List<Map<String, Object>>>(){}.getType();

    ObjectMapper objectMapper = new ObjectMapper();


    private boolean relevanceSort = false;

    public RegistryElasticServiceImpl(){
        final Config settings = ConfigFactory.load("akka.conf");
        Config actualConfig = settings.getConfig("SearchActorSystem");
        ActorSystem as = ActorSystem.create("SearchActorSystem", actualConfig);
        SearchRequestRouterPool.init(as);
    }

    public ActorRef getRequestRouter() {
        ActorRef ref = SearchActorPool.getActorRefFromPool(SearchActorNames.REGISTRY_SEARCH_MANAGER.name());
        if (null == ref)
            throw new ClientException(CompositeSearchErrorCodes.ERR_ROUTER_ACTOR_NOT_FOUND.name(),
                    "Actor not found in the pool for manager: " + SearchActorNames.REGISTRY_SEARCH_MANAGER.name());
        return ref;
    }

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
       // SearchProcessor searchProcessor = new SearchProcessor("teacher");
        final List<Map<String, Object>> groupByFinalList = new ArrayList();
        // SearchManager searchManager = new SearchManager();
        // SearchDTO searchDTO = searchManager.getSearchDTO(searchRequest);
        searchRequest.setConnectionInfo(connection_url);
        searchRequest.setRegistryIndex(elasticIndex);
        searchRequest.setOperation(SearchOperations.INDEX_SEARCH.name());
        searchRequest.setRegistryQueyFileds(searchFieldQuery);
        searchRequest.setRegistryContext(registryContext);

        ActorRef searchManager = getRequestRouter();
        Future<Object> future = Patterns.ask(searchManager, searchRequest, new Timeout(30, TimeUnit.SECONDS));
        Object obj1 = Await.result(future, SearchRequestRouterPool.WAIT_TIMEOUT.duration());

        JsonLDWriteContext ctx = new JsonLDWriteContext();
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(auditFrameFile);
        //InputStream is = this.getClass().getClassLoader().getResourceAsStream(frameFile);
        String fileString = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
        ctx.setFrame(gson.fromJson(fileString,JsonObject.class));
        //Map<String, Object> responseLstMap = null;
        Map<String, Object> responseLstMap = JsonLdProcessor.compact(((Response) obj1).getResult().get("results"),gson.fromJson(json.get("@context"),Map.class),new JsonLdOptions());


        return responseLstMap;

    }


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
