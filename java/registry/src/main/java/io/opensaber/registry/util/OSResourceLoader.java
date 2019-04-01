package io.opensaber.registry.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

@Component
public class OSResourceLoader {

    private static Logger logger = LoggerFactory.getLogger(OSResourceLoader.class);
    private Map<String, String> jsonNodeMap = new HashMap<>();
    private ResourceLoader resourceLoader;
 
    @Autowired
    public OSResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads the resources with a given pattern
     * @param pattern "Example: *.json to load all json files"
     * @return
     * @throws IOException
     */
    public Resource[] getResources(String pattern) throws IOException {
        Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(pattern);
        return resources;
    }

	public void loadResource(String path) throws Exception {
		Resource[] resources = getResources(path);

		for (Resource resource : resources) {
			String jsonContent = getContent(resource);
			jsonNodeMap.put(resource.getFilename(), jsonContent);
		}
		logger.info("size of resources loaded " + jsonNodeMap.size());

	}

    public Map<String, String> getJsonNodes() {
        return jsonNodeMap;
    }

    /**
     * Returns a content of resource
     * 
     * @param resource
     * @return
     */
    private String getContent(Resource resource) {
        String content = null;
        try {
            InputStream is = resource.getInputStream();
            byte[] encoded = IOUtils.toByteArray(is);
            content = new String(encoded, Charset.forName("UTF-8"));

        } catch (IOException e) {
            logger.error("Cannot load resource " + resource.getFilename());

        } 
        return content;
    }

}
