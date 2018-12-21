package io.opensaber.registry.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import io.opensaber.registry.middleware.util.LogMarkers;

@Component("definitionsManager")
public class DefinitionsManager {
    private static Logger logger = LoggerFactory.getLogger(DefinitionsManager.class);

    @Autowired
    private DefinitionsReader definitionsReader;
    private Map<String, Resource> definitionResourceMap = new HashMap<String, Resource>();

    // Loads the definitions from the _schemas folder
    public Set<String> getAllKnownDefinitions() {
        if (definitionResourceMap.isEmpty()) {
            try {
                Resource[] resources = definitionsReader.getResources("classpath:public/_schemas/*.json");
                for (Resource resource : resources) {
                	String fileNameWithoutExt = FilenameUtils.getBaseName(resource.getFilename());
                    definitionResourceMap.put(fileNameWithoutExt, resource);
                }
            } catch (IOException ioe) {
                logger.error(LogMarkers.FATAL, "Cannot load json resources. Validation can't work");
            }
        }
        return definitionResourceMap.keySet();
    }
}
