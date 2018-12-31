package io.opensaber.registry.dao;

import io.opensaber.pojos.Filter;
import io.opensaber.pojos.FilterOperators;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.middleware.util.Constants;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class SearchDaoImpl implements SearchDao {

	public Map<String, Graph> search(Graph graphFromStore, SearchQuery searchQuery) {

		GraphTraversalSource dbGraphTraversalSource = graphFromStore.traversal().clone();
		List<Filter> filterList = searchQuery.getFilters();
		Map<String, Graph> graphMap = new HashMap<String, Graph>();

		List<P> predicates = new ArrayList<>();
		// Ensure the root label is correct
		if (filterList != null) {
			for (Filter filter : filterList) {
				String property = filter.getProperty();
				Object value = filter.getValue();
				FilterOperators operator = filter.getOperator();

				List valueList = getValueList(value);

				switch(operator) {
					case eq:
					default:
						if (valueList.size() > 0) {
							//dbGraphTraversalSource.V().h

						}
						break;
				}
			}

			P resultPredicate =
			predicates.forEach( p -> resultPredicate.and(p));
			GraphTraversal<Vertex, Vertex> resultGraphTraversal = dbGraphTraversalSource.V(resultPredicate)
					.hasLabel(searchQuery.getRootLabel());

			getGraphByTraversal(resultGraphTraversal, graphMap);
		}
		return graphMap;
	}

	private void updateValueList(Object value, List valueList) {
		valueList.add(value);
	}

	private List getValueList(Object value) {
		List valueList = new ArrayList();
		if (value instanceof List) {
			for (Object o : (List) value) {
				updateValueList(o, valueList);
			}
		} else {
			updateValueList(value, valueList);
		}
		return valueList;
	}

	private void getGraphByTraversal(GraphTraversal resultTraversal, Map<String, Graph> graphMap) {
		if (resultTraversal != null) {
			while (resultTraversal.hasNext()) {
				Vertex v = (Vertex) resultTraversal.next();
				if ((!v.property(Constants.STATUS_KEYWORD).isPresent() ||
					Constants.STATUS_ACTIVE.equals(v.value(Constants.STATUS_KEYWORD)))) {
					graphMap.put(v.label(), null);
					//graphMap.put(v.label(), registryDao.getEntityByVertex(v));
				}
			}
			System.out.println("done with while loop");
		}
	}
}
