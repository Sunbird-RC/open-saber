package io.opensaber.registry.dao;

import io.opensaber.pojos.SearchQuery;
import org.apache.tinkerpop.gremlin.structure.Graph;

import java.util.Map;

public interface SearchDao {

    Map<String, Graph> search(Graph graphFromStore, SearchQuery searchQuery);

}
