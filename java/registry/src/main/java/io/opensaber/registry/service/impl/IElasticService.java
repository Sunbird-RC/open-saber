package io.opensaber.registry.service.impl;

import io.opensaber.pojos.SearchQuery;
import java.util.Map;

/**
 * This interface contains unimplemented abstract methods with respect to ElasticSearch
 */
public interface IElasticService {

    /** Saves document into ES(ElasticSearch)
     * @param index - ElasticSearch Index
     * @param inputEntity - input document for adding
     * @return
     */
    public boolean addEntity(String index, Map<String, Object> inputEntity);

    /** Reads document with respect to input osid from ES
     * @param index - ElasticSearch Index
     * @param osid - which maps to document
     * @return
     */
    public Map<String, Object> readEntity(String index, String osid);

    /** updates document with respect to input osid to ES
     * @param index - ElasticSearch Index
     * @param inputEntity - input document for updating
     * @param osid - which maps to document
     * @return
     */
    public boolean updateEntity(String index, Map<String, Object> inputEntity, String osid);

    /** deletes document with respect to input osid from ES
     * @param index - ElasticSearch Index
     * @param osid - which maps to document
     */
    public void deleteEntity(String index, String osid);

    /** searches documents from ES based on query
     * @param index - ElasticSearch Index
     * @param searchQuery - which contains details for search
     * @return
     */
    public Map<String, Object> search(String index, SearchQuery searchQuery);
}
