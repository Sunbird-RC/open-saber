package io.opensaber.registry.util;

import io.opensaber.registry.middleware.util.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IndexHelper {
    private static Logger logger = LoggerFactory.getLogger(IndexHelper.class);

    @Autowired
    private DefinitionsManager definitionManager;

    private Map<String, Boolean> definitionIndexMap = new HashMap<String, Boolean>();

    @PostConstruct
    public void loadDefinitionIndex() {
        List<Definition> definitions = definitionManager.getAllDefinitions();
        definitions.forEach(definition -> {
            definitionIndexMap.put(definition.getTitle(), false);
        });
    }

    /**
     * Checks any new index available to for index creation
     * 
     * @param parentVertex
     * @param definition
     * @return
     */
    public boolean isIndexPresent(Vertex parentVertex, Definition definition) {
        boolean isIndexPresent = false;
        String defTitle = definition.getTitle();
        logger.info("isIndexPresent flag for {}: {}", defTitle, definitionIndexMap.get(defTitle));
        if (!definitionIndexMap.get(defTitle)) {

            List<String> indexFields = definition.getOsSchemaConfiguration().getIndexFields();
            int indexSize = (indexFields.size() == 0) ? 0 : getNewFields(parentVertex, indexFields, false).size();
            if (indexSize > 0)
                return isIndexPresent;

            List<String> indexUniqueFields = definition.getOsSchemaConfiguration().getUniqueIndexFields();
            int uniqueIndexSize = (indexUniqueFields.size() == 0) ? 0
                    : getNewFields(parentVertex, indexUniqueFields, true).size();
            if (uniqueIndexSize > 0)
                return isIndexPresent;

        }
        definitionIndexMap.put(defTitle, true);
        isIndexPresent = true;

        return isIndexPresent;
    }

    /**
     * Identifies new fields for creating index. Parent vertex are always have
     * INDEX_FIELDS and UNIQUE_INDEX_FIELDS property
     * 
     * @param parentVertex
     * @param fields
     * @param isUnique
     */
    public List<String> getNewFields(Vertex parentVertex, List<String> fields, boolean isUnique) {
        List<String> newFields = new ArrayList<>();
        String propertyName = isUnique ? Constants.UNIQUE_INDEX_FIELDS : Constants.INDEX_FIELDS;
        String values = (String) parentVertex.property(propertyName).value();
        for (String field : fields) {
            if (!values.contains(field) && !newFields.contains(field))
                newFields.add(field);
        }
        return newFields;
    }

}
