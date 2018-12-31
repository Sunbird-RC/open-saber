package io.opensaber.registry.dao;

import io.opensaber.pojos.SearchQuery;

import java.util.Map;

import org.apache.tinkerpop.gremlin.structure.Graph;


public interface SearchDao {

    Map<String, Graph> search(Graph graphFromStore, SearchQuery searchQuery);

}
