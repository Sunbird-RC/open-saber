package io.opensaber.registry.dao;

import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.List;

public interface RegistryElasticDAO {

        public void addDocument(String index, String docType, String document);

    public void addDocumentId(String index, String docType, String docId, String document);

    public List<Object> search(String teacher, String docType, SearchSourceBuilder ssb);

}
