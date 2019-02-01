package io.opensaber.registry.util;

import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.sink.DatabaseProvider;
import java.util.ArrayList;
import java.util.List;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexHelper {
    private static Logger logger = LoggerFactory.getLogger(IndexHelper.class);

    private List<String> indexFields;
    private List<String> indexUniqueFields;
    private Vertex parentVertex;

    private List<String> newIndexFields = new ArrayList<>();
    private List<String> newIndexUniqueFields = new ArrayList<>();

    public IndexHelper(List<String> indexFields, List<String> indexUniqueFields, Vertex parentVertex) {

        this.indexFields = indexFields;
        this.indexUniqueFields = indexUniqueFields;
        this.parentVertex = parentVertex;

    }

    /**
     * Creates index for a given databaseProvider
     * 
     * @param dbProvider
     * @param label
     *            a type vertex label (example:Teacher)
     */
    public void create(DatabaseProvider dbProvider, String label, String uuidPropertyName) {

        if(label != null && !label.isEmpty() && (uuidPropertyName != null &&  ! uuidPropertyName.isEmpty())){
            try {
                //added a default field(uuid) for indexing 
                indexFields.add(uuidPropertyName);
                
                // creates non-unique index
                addFields(indexFields, false);
                setPropertyValues(newIndexFields, false);
                dbProvider.createIndex(label, newIndexFields);

                // creates unique index
                addFields(indexUniqueFields, true);
                setPropertyValues(newIndexUniqueFields, true);
                dbProvider.createUniqueIndex(label, newIndexUniqueFields);

            } catch (Exception e) {
                e.printStackTrace();
                logger.error("On non-unique index creation " + e);
            }
        } else {
            logger.info("label, uuidPropertyName is required for creating indexing");
        }
        
    }

    /**
     * Gives new fields for creating index Parent vertex are always have
     * INDEX_FIELDS and UNIQUE_INDEX_FIELDS property
     * 
     * @param parentVertex
     * @param fields
     * @return
     */
    private void addFields(List<String> fields, boolean isUnique) {
        List<String> newFields = isUnique ? newIndexUniqueFields : newIndexFields;
        String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;
        String values = (String) parentVertex.property(propertyName).value();
        for (String field : fields) {
            if (!values.contains(field))
                newFields.add(field);
        }
        logger.info("No of fields to add index are {}", propertyName + newFields.size());
    }

    /**
     * Append the values to parent vertex INDEX_FIELDS and UNIQUE_INDEX_FIELDS
     * property
     * 
     * @param parentVertex
     * @param values
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
