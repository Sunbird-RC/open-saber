package io.opensaber.registry.util;

import io.opensaber.registry.service.ISearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SearchAdvisor {
    private static Logger logger = LoggerFactory.getLogger(SearchAdvisor.class);

    @Value("${search.advisor}")
    public String advisorClassName;

    public ISearchService getInstance() {

        ISearchService advisor = null;
        try {
            if (advisorClassName == null) {
                //default is set to native search service
                advisorClassName = "SearchServiceImpl";
            }
            Class<?> advisorClass = Class.forName(advisorClassName);
            advisor = (ISearchService) advisorClass.newInstance();
            logger.info("Invoked search advisor class with classname: " + advisorClassName);

        } catch (ClassNotFoundException | SecurityException | InstantiationException | IllegalAccessException
                | IllegalArgumentException e) {
            logger.error("Search advisor class {} cannot be instantiate with exception:", advisorClassName, e);
        }

        return advisor;
    }

}
