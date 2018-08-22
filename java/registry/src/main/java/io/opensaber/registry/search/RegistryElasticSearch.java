/*
package io.opensaber.registry.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistryElasticSearch {

    private static Map<String, RestHighLevelClient> esClient = new HashMap<String, RestHighLevelClient>();
    private static ObjectMapper mapper = new ObjectMapper();

    */
/**
     *
     *//*

    private static void createClient(String indexName, String connectionInfo) {
        if(!esClient.containsKey(indexName)){
            Map<String, Integer> hostPort = new HashMap<String, Integer>();
            for(String url : connectionInfo.split(",")){

                hostPort.put(url.split(":")[0], Integer.valueOf(url.split(":")[1]));
            }
            List<HttpHost> httpHosts = new ArrayList<>();
            for(String host: hostPort.keySet()){
                httpHosts.add(new HttpHost(host, hostPort.get(host)));
            }
            RestHighLevelClient elasticClient =
                    new RestHighLevelClient(RestClient.builder(httpHosts.toArray(new HttpHost[httpHosts.size()])));
            if(null != elasticClient) {
                esClient.put(indexName, elasticClient);
            }
        }
    }

    private static RestHighLevelClient getClient(String indexName){
        */
/*if(StringUtils.isBlank(indexName)){

        }*//*

        return esClient.get(indexName);
    }

    public static boolean addIndex(String index, String docType, String settings, String mappings) throws IOException {
        boolean is
        RestHighLevelClient rsClinet = getClient(index);
        if(!isIndexExists(index)){
            CreateIndexRequest createIndexReq = new CreateIndexRequest();
            createIndexReq.settings(Settings.builder().loadFromSource(settings, XContentType.JSON));
            createIndexReq.mapping(docType, mappings, XContentType.JSON);
            CreateIndexResponse createIndexRes = rsClinet.indices().create(createIndexReq);
            return createIndexRes.isAcknowledged();
        }
    }

    public static boolean isIndexExists(String index){
        Response response;
        try {
            response = getClient(index).getLowLevelClient().performRequest("HEAD","/"+index);
            return (200 == response.getStatusLine().getStatusCode());
        } catch (IOException e) {
           return false;
        }

    }

    public IndexResponse addDocument(String index, String docType, String document){
        try {
            Map<String, Object> doc = mapper.readValue(document, new TypeReference<Map<String, Object>>() {});
            //if(isIndexExists(index)){
                getClient(index).index(new IndexRequest(index, docType).source(doc));
           // }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
*/
