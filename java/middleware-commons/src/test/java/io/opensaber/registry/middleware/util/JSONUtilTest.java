package io.opensaber.registry.middleware.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

import static org.junit.Assert.*;

public class JSONUtilTest {

    private static final String ACTUAL_DATA = "actual_data.json";
    private static final String EXPECTED_DATA = "expected_data.json";
    private static final String EXPECTED_REPLACE_FIELD_DATA = "expected_replace_field_data.json";
    private static final String EXPECTED_ADDED_NODE_DATA = "expected_added_node_data.json";
    private static final String EXPECTED_ADDED_PREFIX_DATA = "expected_add_prefix_data.json";

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode getNodeData(String fileName) {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        try {
            objectNode = (ObjectNode) mapper.readTree(getContent(fileName));
        } catch (IOException e) {
            // let the test fail with null
        }
        return objectNode;
    }

//    @Test
//    public void convertObjectJsonMap() {
//    }
//
//    @Test
//    public void getStringWithReplacedText() {
//    }
//
//    @Test
//    public void frameJsonAndRemoveIds() {
//    }
//
//    @Test
//    public void isJsonString() {
//    }

    @Test
    public void replaceField() {
        ObjectNode actualNode = getNodeData(ACTUAL_DATA);
        ObjectNode expectedNode = getNodeData(EXPECTED_REPLACE_FIELD_DATA);

        JSONUtil.replaceField(actualNode, Constants.JsonldConstants.ID, "newValue");
        assertEquals(actualNode, expectedNode);
    }

    @Test
    public void trimPrefix() {
        ObjectNode testDataNode = JsonNodeFactory.instance.objectNode();
        ObjectNode expected = JsonNodeFactory.instance.objectNode();
        try {
            testDataNode = (ObjectNode) mapper.readTree("{\"Teacher\":{\"osid\":\"1-09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1-1a496e91-7886-44e2-abfa-1a40c3337d1e\"}],\"childObj\":{\"osid\":\"1-4a497b91-7886-44e2-abfa-1a40c3337d1f\"}}}");
            expected = (ObjectNode) mapper.readTree("{\"Teacher\":{\"osid\":\"09cc3c81-6180-4e74-aba9-f015bbaa95f1\",\"basicProficiencyLevel\":[{\"osid\":\"1a496e91-7886-44e2-abfa-1a40c3337d1e\"}],\"childObj\":{\"osid\":\"4a497b91-7886-44e2-abfa-1a40c3337d1f\"}}}");
        } catch (Exception e) {
        }

        JSONUtil.trimPrefix(testDataNode, "1-");
        assertEquals(expected, testDataNode);
    }

    @Test
    public void addPrefix() {
        ObjectNode actualNode = getNodeData(ACTUAL_DATA);
        ObjectNode expectedNode = getNodeData(EXPECTED_ADDED_PREFIX_DATA);
        List<String> keys = new ArrayList<String>();
        keys.add(Constants.JsonldConstants.ID);

        JSONUtil.addPrefix(actualNode, "prefix-", keys);
        assertEquals(actualNode, expectedNode);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNode_noKey_throws_exception() {
        ObjectNode actualNode = getNodeData(ACTUAL_DATA);
        JSONUtil.addNode(actualNode, "", JsonNodeFactory.instance.objectNode());
    }

    @Test
    public void addNode() {
        ObjectNode actualNode = getNodeData(ACTUAL_DATA);
        ObjectNode expectedNode = getNodeData(EXPECTED_ADDED_NODE_DATA);

        ObjectNode nodeToAdd = JsonNodeFactory.instance.objectNode();
        nodeToAdd.put("newNodeKey", "newNodeValue");

        JSONUtil.addNode(actualNode, "addedKey", nodeToAdd);
        assertEquals(actualNode, expectedNode);
    }

    @Test
    public void removeNode() {
        ObjectNode actualNode = getNodeData(ACTUAL_DATA);
        ObjectNode expectedNode = getNodeData(EXPECTED_DATA);

        JSONUtil.removeNode(actualNode, Constants.JsonldConstants.ID);
        assertEquals(actualNode, expectedNode);
    }

//    @Test
//    public void removeNodes() {
//    }
//
//    @Test
//    public void findKey() {
//    }

    private String getContent(String fileName) {
        InputStream in;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream(fileName);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());

        } catch (FileNotFoundException e1) {
            e1.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();

        }
        return null;
    }
}