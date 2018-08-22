package io.opensaber.registry.dao.impl;

import io.opensaber.registry.dao.RegistryElasticDAO;
import org.ekstep.searchindex.elasticsearch.ElasticSearchUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RegistryElasticDAOImpl implements RegistryElasticDAO {

   // @Value("${elastic.index}")
    private String indexName = "teacher";
   // @Value("${elastic.connection_url}")
    private String connectionInfo = "localhost:9200";

    RegistryElasticDAOImpl(){
        ElasticSearchUtil.initialiseESClient(indexName, connectionInfo);
    }

    @Override
    public void addDocument(String index, String docType, String document) {
        ElasticSearchUtil.addDocument(index, docType, document);
    }

    @Override
    public void addDocumentId(String index, String docType,String docId, String document) {
        ElasticSearchUtil.addDocumentWithId(index, docType, docId, document);
    }

    @Override
    public List<Object> search(String teacher, String docType, SearchSourceBuilder ssb) {
        List<Object> lst = null;
        try {
            SearchResponse searchResponse  = ElasticSearchUtil.search(teacher, docType, ssb);
            lst =  ElasticSearchUtil.getDocumentsFromHits(searchResponse.getHits());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lst;
    }


}
