package io.opensaber.registry.service.impl;

import io.opensaber.pojos.SearchQuery;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
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

    @PostConstruct
    void init() throws IOException {
        addIndex(searchIndex,"_doc");
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
        IndexResponse response = getClient(index).index(new IndexRequest(index,"_doc").source(inputEntity),RequestOptions.DEFAULT);
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

    public static boolean addIndex(String indexName, String documentType) throws IOException {
        boolean response = false;
        String settings = "{\"analysis\": {       \"analyzer\": {         \"doc_index_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"lowercase\",             \"mynGram\"           ]         },         \"doc_search_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"standard\",             \"lowercase\"           ]         },         \"keylower\": {           \"tokenizer\": \"keyword\",           \"filter\": \"lowercase\"         }       },       \"filter\": {         \"mynGram\": {           \"type\": \"nGram\",           \"min_gram\": 1,           \"max_gram\": 20,           \"token_chars\": [             \"letter\",             \"digit\",             \"whitespace\",             \"punctuation\",             \"symbol\"           ]         }       }     }   }";
        String mappings = "{\"dynamic_templates\":[{\"longs\":{\"match_mapping_type\":\"long\",\"mapping\":{\"type\":\"long\",\"fields\":{\"raw\":{\"type\":\"long\"}}}}},{\"booleans\":{\"match_mapping_type\":\"boolean\",\"mapping\":{\"type\":\"boolean\",\"fields\":{\"raw\":{\"type\":\"boolean\"}}}}},{\"doubles\":{\"match_mapping_type\":\"double\",\"mapping\":{\"type\":\"double\",\"fields\":{\"raw\":{\"type\":\"double\"}}}}},{\"dates\":{\"match_mapping_type\":\"date\",\"mapping\":{\"type\":\"date\",\"fields\":{\"raw\":{\"type\":\"date\"}}}}},{\"strings\":{\"match_mapping_type\":\"string\",\"mapping\":{\"type\":\"text\",\"copy_to\":\"all_fields\",\"analyzer\":\"doc_index_analyzer\",\"search_analyzer\":\"doc_search_analyzer\",\"fields\":{\"raw\":{\"type\":\"text\",\"analyzer\":\"keylower\"}}}}}],\"properties\":{\"all_fields\":{\"type\":\"text\",\"analyzer\":\"doc_index_analyzer\",\"search_analyzer\":\"doc_search_analyzer\",\"fields\":{\"raw\":{\"type\":\"text\",\"analyzer\":\"keylower\"}}}}}";
        RestHighLevelClient client = getClient(indexName);
        if (!isIndexExists(indexName)) {
            CreateIndexRequest createRequest = new CreateIndexRequest(indexName);

            if (StringUtils.isNotBlank(settings))
                createRequest.settings(Settings.builder().loadFromSource(settings, XContentType.JSON));
            if (StringUtils.isNotBlank(documentType) && StringUtils.isNotBlank(mappings))
                createRequest.mapping(documentType, mappings, XContentType.JSON);
            CreateIndexResponse createIndexResponse = client.indices().create(createRequest,RequestOptions.DEFAULT);

            response = createIndexResponse.isAcknowledged();
        }
        return response;
    }

    public static boolean isIndexExists(String indexName) {
        Response response;
        try {
            response = getClient(indexName).getLowLevelClient().performRequest("HEAD", "/" + indexName);
            return (200 == response.getStatusLine().getStatusCode());
        } catch (IOException e) {
            return false;
        }

    }
}
