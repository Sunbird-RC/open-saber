package io.opensaber.registry.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.opensaber.pojos.Filter;
import io.opensaber.pojos.FilterOperators;
import io.opensaber.pojos.SearchQuery;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.util.ReadConfigurator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

public class SearchDaoImpl implements SearchDao {
    private IRegistryDao registryDao;

    public SearchDaoImpl(IRegistryDao registryDaoImpl) {
        registryDao = registryDaoImpl;
    }

	public JsonNode search(Graph graphFromStore, SearchQuery searchQuery) {

		GraphTraversalSource dbGraphTraversalSource = graphFromStore.traversal().clone();
		List<Filter> filterList = searchQuery.getFilters();
		int offset = searchQuery.getOffset();
		GraphTraversal<Vertex, Vertex> resultGraphTraversal = dbGraphTraversalSource.clone().V().hasLabel(searchQuery.getRootLabel())
		        .range(offset, offset + searchQuery.getLimit()).limit(searchQuery.getLimit()); 

		BiPredicate<String, String> condition = null;
		// Ensure the root label is correct
		if (filterList != null) {
			for (Filter filter : filterList) {
				String property = filter.getProperty();
				Object genericValue = filter.getValue();

				FilterOperators operator = filter.getOperator();
				String path = filter.getPath();

                switch (operator) {
                case eq:
                    resultGraphTraversal = resultGraphTraversal.has(property, P.eq(genericValue));
                    break;
                case gt:
                    resultGraphTraversal = resultGraphTraversal.has(property, P.gt(genericValue));
                    break;
                case lt:
                    resultGraphTraversal = resultGraphTraversal.has(property, P.lt(genericValue));
                    break;
                case gte:
                    resultGraphTraversal = resultGraphTraversal.has(property, P.gte(genericValue));
                    break;
                case lte:
                    resultGraphTraversal = resultGraphTraversal.has(property, P.lte(genericValue));
                    break;
                case between:
                    List<Object> objects = (List<Object>) genericValue;
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            P.between(objects.get(0), objects.get(objects.size() - 1)));
                    break;
                case contains:
                    condition = (s1, s2) -> (s1.contains(s2));
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            new P<String>(condition, genericValue.toString()));
                    break;
                case startsWith:
                    condition = (s1, s2) -> (s1.startsWith(s2));
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            new P<String>(condition, genericValue.toString()));
                    break;
                case endsWith:
                    condition = (s1, s2) -> (s1.endsWith(s2));
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            new P<String>(condition, genericValue.toString()));
                    break;
                case notContains:
                    condition = (s1, s2) -> (s1.contains(s2));
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            new P<String>(condition, genericValue.toString()));
                    break;
                case notStartsWith:
                    condition = (s1, s2) -> (!s1.startsWith(s2));
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            new P<String>(condition, genericValue.toString()));
                    break;
                case notEndsWith:
                    condition = (s1, s2) -> (!s1.endsWith(s2));
                    resultGraphTraversal = resultGraphTraversal.has(property,
                            new P<String>(condition, genericValue.toString()));
                    break;
                default:
                    resultGraphTraversal = resultGraphTraversal.has(property, P.eq(genericValue));
                    break;
                }

				if (path != null) {
					if (resultGraphTraversal.asAdmin().clone().hasNext()) {
						resultGraphTraversal = resultGraphTraversal.asAdmin().clone().outE(path).outV();
					}
				}
			}
		}

		return getResult(graphFromStore, resultGraphTraversal);
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

	private JsonNode getResult (Graph graph, GraphTraversal resultTraversal) {
		ArrayNode result = JsonNodeFactory.instance.arrayNode();
		if (resultTraversal != null) {
			while (resultTraversal.hasNext()) {
				Vertex v = (Vertex) resultTraversal.next();
				if ((!v.property(Constants.STATUS_KEYWORD).isPresent() ||
					Constants.STATUS_ACTIVE.equals(v.value(Constants.STATUS_KEYWORD)))) {

					ReadConfigurator configurator = new ReadConfigurator();
					configurator.setIncludeSignatures(false);
					configurator.setIncludeTypeAttributes(false);

					JsonNode answer = null;
					try {
						answer = registryDao.getEntity(graph, v, configurator);
					} catch (Exception e) {
						e.printStackTrace();
					}
					result.add(answer);
				}
			}
		}
		return result;
	}
}
