package io.opensaber.registry.util;

import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.sink.DatabaseProvider;
import java.util.ArrayList;
import java.util.List;
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

    private List<String> indexFields;
    private List<String> indexUniqueFields;
    private DatabaseProvider databaseProvider;

    private List<String> newIndexFields = new ArrayList<>();
    private List<String> newIndexUniqueFields = new ArrayList<>();

    public Indexer(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    /**
     * Required to set non-unique fields to create
     * @param indexFields
     */
    public void setIndexFields(List<String> indexFields) {
        this.indexFields = indexFields;
    }

    /**
     * Required to set unique fields to create 
     * @param indexUniqueFields
     */
    public void setUniqueIndexFields(List<String> indexUniqueFields) {
        this.indexUniqueFields = indexUniqueFields;
    }

    /**
     * Creates index for a given label
     * 
     * @param label
     *            a type vertex label (example:Teacher) and table in rdbms
     */
    public void createIndex(String label, Vertex parentVertex) {
        if (label != null && !label.isEmpty()) {
            createNonUniqueIndex(label, parentVertex);
            createUniqueIndex(label, parentVertex);
        } else {
            logger.info("label is required for creating indexing");
        }
    }

    private void createNonUniqueIndex(String label, Vertex parentVertex) {
        try {
            addFields(indexFields, parentVertex, false);
            setPropertyValues(newIndexFields, parentVertex, false);
            databaseProvider.createIndex(label, newIndexFields);

        } catch (Exception e) {
            logger.error("Non-unique index creation error: " + e);
        }
    }

    private void createUniqueIndex(String label, Vertex parentVertex) {
        try {
            addFields(indexUniqueFields, parentVertex, true);
            setPropertyValues(newIndexUniqueFields, parentVertex, true);
            databaseProvider.createUniqueIndex(label, newIndexUniqueFields);

        } catch (Exception e) {
            logger.error("Unique index creation error: " + e);
        }
    }

    /**
     * Adds new fields for creating index. Parent vertex are always have
     * INDEX_FIELDS and UNIQUE_INDEX_FIELDS property
     * 
     * @param fields
     * @param isUnique
     */
    private void addFields(List<String> fields, Vertex parentVertex, boolean isUnique) {
        List<String> newFields = isUnique ? newIndexUniqueFields : newIndexFields;
        String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;
        String values = (String) parentVertex.property(propertyName).value();
        for (String field : fields) {
            if (!values.contains(field))
                newFields.add(field);
        }
    }

    /**
     * Append the values to parent vertex INDEX_FIELDS and UNIQUE_INDEX_FIELDS
     * property
     * 
     * @param values
     * @param isUnique
     */
    private void setPropertyValues(List<String> values, Vertex parentVertex, boolean isUnique) {

        if (values.size() > 0) {
            String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;
            String existingValue = (String) parentVertex.property(propertyName).value();
            for (String value : values) {
                existingValue = existingValue.isEmpty() ? value : (existingValue + "," + value);
                parentVertex.property(propertyName, existingValue);
            }
            logger.info("parent vertex property {}:{}", propertyName, existingValue);
        } else {
            logger.info("no values to set for parent vertex property ");
        }
    }

}
