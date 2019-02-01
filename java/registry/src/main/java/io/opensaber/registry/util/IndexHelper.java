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
public class IndexHelper {
    private static Logger logger = LoggerFactory.getLogger(IndexHelper.class);

    private List<String> indexFields;
    private List<String> indexUniqueFields;
    private Vertex parentVertex;
    private DatabaseProvider databaseProvider;

    private List<String> newIndexFields = new ArrayList<>();
    private List<String> newIndexUniqueFields = new ArrayList<>();

    public IndexHelper(DatabaseProvider databaseProvider, Vertex parentVertex) {
        this.databaseProvider = databaseProvider;
        this.parentVertex = parentVertex;
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
    public void create(String label) {
        if (label != null && !label.isEmpty()) {
            createIndex(label);
            createUniqueIndex(label);
        } else {
            logger.info("label is required for creating indexing");
        }
    }

    private void createIndex(String label) {
        try {
            addFields(indexFields, false);
            setPropertyValues(newIndexFields, false);
            databaseProvider.createIndex(label, newIndexFields);

        } catch (Exception e) {
            e.printStackTrace();
            logger.error("On non-unique index creation " + e);
        }
    }

    private void createUniqueIndex(String label) {
        try {
            addFields(indexUniqueFields, true);
            setPropertyValues(newIndexUniqueFields, true);
            databaseProvider.createUniqueIndex(label, newIndexUniqueFields);

        } catch (Exception e) {
            e.printStackTrace();
            logger.error("On unique index creation " + e);
        }
    }

    /**
     * Adds new fields for creating index. Parent vertex are always have
     * INDEX_FIELDS and UNIQUE_INDEX_FIELDS property
     * 
     * @param fields
     * @param isUnique
     */
    private void addFields(List<String> fields, boolean isUnique) {
        List<String> newFields = isUnique ? newIndexUniqueFields : newIndexFields;
        String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;
        String values = (String) parentVertex.property(propertyName).value();
        for (String field : fields) {
            if (!values.contains(field))
                newFields.add(field);
        }
        logger.info("No of fields to add {} ", propertyName + newFields.size());
    }

    /**
     * Append the values to parent vertex INDEX_FIELDS and UNIQUE_INDEX_FIELDS
     * property
     * 
     * @param values
     * @param isUnique
     */
    private void setPropertyValues(List<String> values, boolean isUnique) {

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
