package io.opensaber.registry.util;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opensaber.registry.middleware.util.Constants;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { OSSystemFieldsHelper.class, DefinitionsManager.class, OSResourceLoader.class })
@ActiveProfiles(Constants.TEST_ENVIRONMENT)
public class OSSystemFieldsHelperTest {

    @Autowired
    private OSSystemFieldsHelper systemFieldsHelper;

    private final ObjectMapper mapper = new ObjectMapper();

    private String entityType;

    @Before
    public void init() {
        JsonNode testNode = getTestNode();
        entityType = testNode.fieldNames().next();

    }

    private JsonNode getTestNode() {
        ObjectNode testNode = JsonNodeFactory.instance.objectNode();
        try {
            testNode = (ObjectNode) mapper.readTree(
                    "{\"Teacher\":{\"osid\":\"1-09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1-1a496e91-7886-44e2-abfa-1a40c3337d1e\"}],\"childObj\":{\"osid\":\"1-4a497b91-7886-44e2-abfa-1a40c3337d1f\"}}}");
        } catch (IOException e) {
            // let the test fail with null
        }
        return testNode;

    }

    @Test
    public void testAddSystemPropertyNotValid() {
        JsonNode testNode = getTestNode();
        JsonNode node = testNode.get(entityType);

        try {
            systemFieldsHelper.addSystemProperty("notValid", node, "userId", "timeStamp");
        } catch (Exception e) {
            Assert.fail("Exception " + e);
        }

    }

    @Test
    public void testAddSystemPropertyCreatedAt() throws IOException {
        JsonNode testNode = getTestNode();
        JsonNode node = testNode.get(entityType);

        systemFieldsHelper.addSystemProperty("_osCreatedAt", node, "userId", "timeStamp");

        String expected = "{\"osid\":\"1-09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1-1a496e91-7886-44e2-abfa-1a40c3337d1e\",\"_osCreatedAt\":\"timeStamp\"}],\"childObj\":{\"osid\":\"1-4a497b91-7886-44e2-abfa-1a40c3337d1f\",\"_osCreatedAt\":\"timeStamp\"},\"_osCreatedAt\":\"timeStamp\"}";
        ObjectNode expectedNode = (ObjectNode) mapper.readTree(expected);

        assertEquals(expectedNode, node);

    }

    @Test
    public void testAddSystemPropertyCreatedBy() throws IOException {
        JsonNode testNode = getTestNode();
        JsonNode node = testNode.get(entityType);

        systemFieldsHelper.addSystemProperty("_osCreatedBy", node, "userId", "timeStamp");

        String expected = "{\"osid\":\"1-09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1-1a496e91-7886-44e2-abfa-1a40c3337d1e\",\"_osCreatedBy\":\"userId\"}],\"childObj\":{\"osid\":\"1-4a497b91-7886-44e2-abfa-1a40c3337d1f\",\"_osCreatedBy\":\"userId\"},\"_osCreatedBy\":\"userId\"}";
        ObjectNode expectedNode = (ObjectNode) mapper.readTree(expected);

        assertEquals(expectedNode, node);

    }

    @Test
    public void testAddSystemPropertyUpdatedAt() throws IOException {
        JsonNode testNode = getTestNode();
        JsonNode node = testNode.get(entityType);

        systemFieldsHelper.addSystemProperty("_osUpdatedAt", node, "userId", "timeStamp");

        String expected = "{\"osid\":\"1-09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1-1a496e91-7886-44e2-abfa-1a40c3337d1e\",\"_osUpdatedAt\":\"timeStamp\"}],\"childObj\":{\"osid\":\"1-4a497b91-7886-44e2-abfa-1a40c3337d1f\",\"_osUpdatedAt\":\"timeStamp\"},\"_osUpdatedAt\":\"timeStamp\"}";
        ObjectNode expectedNode = (ObjectNode) mapper.readTree(expected);

        assertEquals(expectedNode, node);

    }

    @Test
    public void testAddSystemPropertyUpdatedBy() throws IOException {
        JsonNode testNode = getTestNode();
        String key = testNode.fieldNames().next();
        JsonNode node = testNode.get(key);

        systemFieldsHelper.addSystemProperty("_osUpdatedBy", node, "userId", "timeStamp");

        String expected = "{\"osid\":\"1-09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1-1a496e91-7886-44e2-abfa-1a40c3337d1e\",\"_osUpdatedBy\":\"userId\"}],\"childObj\":{\"osid\":\"1-4a497b91-7886-44e2-abfa-1a40c3337d1f\",\"_osUpdatedBy\":\"userId\"},\"_osUpdatedBy\":\"userId\"}";
        ObjectNode expectedNode = (ObjectNode) mapper.readTree(expected);

        assertEquals(expectedNode, node);

    }

}
