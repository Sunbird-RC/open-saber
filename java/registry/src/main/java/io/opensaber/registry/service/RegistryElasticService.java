package io.opensaber.registry.service;

import io.opensaber.pojos.Request;
import org.eclipse.rdf4j.model.Model;

import java.util.List;
import java.util.Map;

public interface RegistryElasticService {

    public void addDocument(String index,String docType,String docId,String document);

    public void addIndex(String Index);

    public void createClient(String index, String connectionInfo);

    void addDocument(Request requestModel, String response);

    Map<String, Object> search(Request requestModel) throws Exception;
}
