package io.opensaber.registry.util;

import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.sink.DatabaseProvider;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class helps to create index of unique or non-unique type. Must set the
 * values for unique index & non-unique index fields
 *
 */
public class Indexer {
    private static Logger logger = LoggerFactory.getLogger(Indexer.class);
/*
    *//**
     * Non unique index fields 
     *//*
    private List<String> indexFields;*/
    /**
     * Unique index fields
     */
    private List<String> indexUniqueFields;
    /**
     * Composite index fields
     */
    private List<String> compositeIndexFields;
    /**
     * Single index fields
     */
    private List<String> singleIndexFields;
    private DatabaseProvider databaseProvider;

    public Indexer(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

/*    *//**
     * Required to set non-unique fields to create
     * 
     * @param indexFields
     *//*
    public void setIndexFields(List<String> indexFields) {
        this.indexFields = indexFields;
    }
*/
    /**
     * Required to set unique fields to create
     * 
     * @param indexUniqueFields
     */
    public void setUniqueIndexFields(List<String> indexUniqueFields) {
        this.indexUniqueFields = indexUniqueFields;
    }
    /**
     * Required to set single fields to create
     * 
     * @param indexUniqueFields
     */
    public void setSingleIndexFields(List<String> singleIndexFields) {
        this.singleIndexFields = singleIndexFields;
    }
    /**
     * Required to set composite fields to create
     * 
     * @param indexUniqueFields
     */
    public void setCompositeIndexFields(List<String> compositeIndexFields) {
        this.compositeIndexFields = compositeIndexFields;
    }

    /**
     * Creates index for a given label
     * 
     * @param graph
     * @param label     type vertex label (example:Teacher) and table in rdbms           
     * @param parentVertex
     */
    public boolean createIndex(Graph graph, String label, Vertex parentVertex) throws NoSuchElementException {
        boolean isCreated = false;
        if (label != null && !label.isEmpty()) {
            try {
                createNonUniqueIndex(graph, label, parentVertex);
                createUniqueIndex(graph, label, parentVertex);
                updateIndices(parentVertex);
                isCreated = true;
            } catch (Exception e) {
                // TODO Auto-generated catch block
                logger.error(e.getMessage());
                logger.error("Failed to create index {}", label);
            }
        } else {
            logger.info("label is required for creating indexing");
        }
        return isCreated;
    }

    private void createNonUniqueIndex(Graph graph, String label, Vertex parentVertex) throws NoSuchElementException {
        databaseProvider.createIndex(graph, label, singleIndexFields);        
        databaseProvider.createCompositeIndex(graph, label, compositeIndexFields);

    }

    private void createUniqueIndex(Graph graph, String label, Vertex parentVertex) throws NoSuchElementException {
        databaseProvider.createUniqueIndex(graph, label, indexUniqueFields);
    }

/*    *//**
     * Append the values to parent vertex INDEX_FIELDS and UNIQUE_INDEX_FIELDS
     * property
     * 
     * @param parentVertex
     * @param values
     * @param isUnique
     *//*
    private void updateIndices(Vertex parentVertex, List<String> values, boolean isUnique) {
        String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;

        if (values.size() > 0) {
            String properties = new StringBuilder(String.join(",", values)).toString();
            parentVertex.property(propertyName, properties);
            logger.info("parent vertex property {}:{}", propertyName, properties);
        } else {
            logger.info("no values to set for parent vertex property for {}", propertyName);
        }
    }*/
    /**
     * 
     * @param parentVertex
     */
    private void updateIndices(Vertex parentVertex) {  
        if (singleIndexFields.size() > 0 && compositeIndexFields.size() > 0) {
            StringBuilder sproperties = new StringBuilder(String.join(",", singleIndexFields));
            StringBuilder cproperties = sproperties.append(",(").append(String.join(",", compositeIndexFields)).append(")");
            //sproperties.append(cproperties);
            replaceVertexProperty(parentVertex, cproperties.toString(), false);

        }
        if(indexUniqueFields.size() > 0){
            StringBuilder properties = new StringBuilder(String.join(",", indexUniqueFields));
            replaceVertexProperty(parentVertex, properties.toString(), true);
  
        }
    }
    /**
     * Replace the INDEX_FIELDS and UNIQUE_INDEX_FIELDS of parent vertex
     * @param parentVertex
     * @param properties
     * @param isUnique
     */
    private void replaceVertexProperty(Vertex parentVertex, String properties, boolean isUnique){
        String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;
        parentVertex.property(propertyName, properties);
        logger.info("parent vertex property {}:{}", propertyName, properties);
    }
    
}
