package io.opensaber.registry.service.impl;

import io.opensaber.pojos.SearchQuery;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ElasticServiceImpl implements IElasticService{
    private static Map<String, RestHighLevelClient> esClient = new HashMap<String, RestHighLevelClient>();
    private static Logger logger = LoggerFactory.getLogger(ElasticServiceImpl.class);


    private static String searchIndex;
    private static String connectionInfo;

    @Value("${elastic.search.index}")
    public void setSearchIndex(String index){
        searchIndex = index;
    }
    @Value("${elastic.search.connection_url}")
    public void setConnectionInfo(String connection){
        connectionInfo = connection;
    }

    /**
     *
     */
    private static void createClient(String indexName, String connectionInfo) {
        if (!esClient.containsKey(indexName)) {
            Map<String, Integer> hostPort = new HashMap<String, Integer>();
            for (String info : connectionInfo.split(",")) {
                hostPort.put(info.split(":")[0], Integer.valueOf(info.split(":")[1]));
            }
            List<HttpHost> httpHosts = new ArrayList<>();
            for (String host : hostPort.keySet()) {
                httpHosts.add(new HttpHost(host, hostPort.get(host)));
            }
            RestHighLevelClient client = new RestHighLevelClient(
                    RestClient.builder(httpHosts.toArray(new HttpHost[httpHosts.size()])));
            if (null != client)
                esClient.put(indexName, client);
        }
    }

    private static RestHighLevelClient getClient(String indexName) {
        logger.info("connection info: index:{} connectioninfo:{}", indexName, connectionInfo);
        if (StringUtils.isBlank(indexName))
            indexName = searchIndex;
        if (null == esClient.get(indexName)) {
            createClient(indexName,connectionInfo);
        }
        logger.info("resthighclient obj:"+esClient.get(indexName));
        return esClient.get(indexName);
    }

    @Override
    public boolean addEntity(String index, Map<String, Object> inputEntity) throws IOException {
        // getClient(index).index(new IndexRequest(index).source(inputEntity));
        logger.info("addEntity starts");
        IndexResponse response = getClient(index).index(new IndexRequest(index).source(inputEntity),RequestOptions.DEFAULT);
        logger.info("response id:",response.getId());
        if(response.getId()!=null){
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Object> readEntity(String index, String osid) {
        return null;
    }

    @Override
    public boolean updateEntity(String index, Map<String, Object> inputEntity, String osid) {
        return false;
    }

    @Override
    public void deleteEntity(String index, String osid) {

    }

    @Override
    public Map<String, Object> search(String index, SearchQuery searchQuery) {
        return null;
    }
}
