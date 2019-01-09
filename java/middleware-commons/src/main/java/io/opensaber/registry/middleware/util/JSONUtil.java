package io.opensaber.registry.middleware.util;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class JSONUtil {

	private final static String KEY_NULL_ERROR = "key cannot be null or empty";
	private static final String EMPTY = "";
	private static Logger logger = LoggerFactory.getLogger(JSONUtil.class);
	private static Type stringObjMapType = new TypeToken<Map<String, Object>>() {
	}.getType();
	private static String key = "";

	public static Map<String, Object> convertObjectJsonMap(Object object) {
		Gson gson = new Gson();
		String result = gson.toJson(object);
		return gson.fromJson(result, stringObjMapType);
	}

	public static String getStringWithReplacedText(String payload, String value, String replacement) {
		Pattern pattern = Pattern.compile(value);
		Matcher matcher = pattern.matcher(payload);
		return matcher.replaceAll(replacement);
	}

	public static Map<String, Object> frameJsonAndRemoveIds(String regex, String json, Gson gson, String frame)
			throws JsonLdError, IOException {
		Map<String, Object> reqMap = gson.fromJson(json, stringObjMapType);
		JsonObject jsonObj = gson.fromJson(json, JsonObject.class);
		String rootType = null;
		if (jsonObj.get("@graph") != null) {
			rootType = jsonObj.get("@graph").getAsJsonArray().get(0).getAsJsonObject().get("@type").getAsString();
		} else {
			rootType = jsonObj.get("@type").getAsString();
		}
		String replacedframe = frame.replace("<@type>", rootType);
		// JsonUtils.fromString(frame)
		JsonLdOptions options = new JsonLdOptions();
		options.setCompactArrays(true);
		Map<String, Object> framedJsonLD = JsonLdProcessor.frame(reqMap, JsonUtils.fromString(replacedframe), options);
		// json = gson.toJson(framedJsonLD);
		String jsonld = JSONUtil.getStringWithReplacedText(gson.toJson(framedJsonLD), regex, EMPTY);
		logger.info("frameJsonAndRemoveIds: json - ", jsonld);
		return gson.fromJson(jsonld, stringObjMapType);
	}

	/**
	 * Returns true if the passed in string is a valid json
	 * 
	 * @param str
	 * @return
	 */
	public static boolean isJsonString(String str) {
		boolean isJson = false;
		try {
			final ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(str);
			// At least one key is expected
			if (node.fieldNames().hasNext()) {
				isJson = true;
			}
		} catch (IOException e) {
			isJson = false;
		}
		return isJson;
	}

	/**
	 * Field value to replace by new text. Replace node by given text to Parent's
	 * hierarchy. Field will not be added if not found existing already
	 * 
	 * @param parent
	 * @param fieldName
	 * @param newValue
	 */
	public static void replaceField(ObjectNode parent, String fieldName, String newValue) {
		if (parent.has(fieldName)) {
			parent.put(fieldName, newValue);
		}
		parent.fields().forEachRemaining(entry -> {
			JsonNode entryValue = entry.getValue();
			if (entryValue.isArray()) {
				for (int i = 0; i < entryValue.size(); i++) {
					if (entry.getValue().get(i).isObject())
						replaceField((ObjectNode) entry.getValue().get(i), fieldName, newValue);
				}
			} else if (entryValue.isObject()) {
				replaceField((ObjectNode) entry.getValue(), fieldName, newValue);
			}
		});
	}

	/**
	 * Trimming a given prefix if present from each TextNode value in parent's
	 * hierarchy (including nested objects).
	 * 
	 * @param parent
	 * @param prefix
	 */
	public static void trimPrefix(ObjectNode parent, String prefix) {

		parent.fields().forEachRemaining(entry -> {
			JsonNode entryValue = entry.getValue();
			if (entryValue.isValueNode() && entryValue.toString().contains(prefix)) {
				parent.put(entry.getKey(), entry.getValue().asText().replace(prefix, ""));
			} else if (entryValue.isArray()) {
				for (int i = 0; i < entryValue.size(); i++) {
					if (entry.getValue().get(i).isObject())
						trimPrefix((ObjectNode) entry.getValue().get(i), prefix);
				}
			} else if (entryValue.isObject()) {
				trimPrefix((ObjectNode) entry.getValue(), prefix);
			}
		});
	}

	/**
	 * Add prefix to given keys present in the parent's hierarchy.
	 * 
	 * @param parent
	 * @param prefix
	 * @param keys
	 */
	public static void addPrefix(ObjectNode parent, String prefix, List<String> keys) {

		parent.fields().forEachRemaining(entry -> {
			JsonNode entryValue = entry.getValue();
			if (entryValue.isValueNode() && keys.contains(entry.getKey())) {
				String defaultValue = prefix + entryValue.asText();
				parent.put(entry.getKey(), defaultValue);
			} else if (entryValue.isArray()) {
				ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
				for (int i = 0; i < entryValue.size(); i++) {
					if (entryValue.get(i).isTextual() && keys.contains(entry.getKey()))
						arrayNode.add(prefix + entryValue.get(i).asText());
					else if(entryValue.get(i).isObject())
						addPrefix((ObjectNode) entryValue.get(i), prefix, keys);
				}
				if (arrayNode.size() > 0)
					parent.set(entry.getKey(), arrayNode);
			} else if (entryValue.isObject()) {
				addPrefix((ObjectNode) entry.getValue(), prefix, keys);
			}
		});
	}

	/**
	 * Adding a child node to Parent's hierarchy.
	 * 
	 * @param parent
	 * @param childKey
	 * @param child
	 */
	public static void addNode(ObjectNode parent, String childKey, ObjectNode child) {
		parent.fields().forEachRemaining(entry -> {
			JsonNode entryValue = entry.getValue();
			if (entryValue.isObject()) {
				addNode((ObjectNode) entry.getValue(), childKey, child);
			}
			if (entryValue.isArray()) {
				for (int i = 0; i < entryValue.size(); i++) {
					if (entry.getValue().get(i).isObject())
						addNode((ObjectNode) entry.getValue().get(i), childKey, child);
				}
			}

		});
		if (childKey == null || childKey.isEmpty())
			throw new IllegalArgumentException(KEY_NULL_ERROR);
		parent.set(childKey, child);

	}

	/**
	 * Remove a node of given key from parent's hierarchy(including nested objects)
	 * 
	 * @param parent
	 * @param removeKey
	 */
	public static void removeNode(ObjectNode parent, String removeKey) {
		parent.fields().forEachRemaining(entry -> {

			JsonNode entryValue = entry.getValue();
			if (entryValue.isArray()) {
				for (int i = 0; i < entryValue.size(); i++) {
					if (entry.getValue().get(i).isObject())
						removeNode((ObjectNode) entry.getValue().get(i), removeKey);
				}
			} else if (entryValue.isObject()) {
				removeNode((ObjectNode) entry.getValue(), removeKey);
			}

		});
		parent.remove(removeKey);
	}

	/**
	 * Remove list of nodes given from parent's hierarchy(including nested objects
	 * too)
	 * 
	 * @param parent
	 * @param removeKeys
	 */
	public static void removeNodes(ObjectNode parent, List<String> removeKeys) {
		List<String> removeKeyNames = new ArrayList<String>();
		parent.fields().forEachRemaining(entry -> {
			if (removeKeys.contains(entry.getKey())) {
				removeKeyNames.add(entry.getKey());
			} else {
				JsonNode entryValue = entry.getValue();
				if (entryValue.isArray()) {
					for (int i = 0; i < entryValue.size(); i++) {
						if (entry.getValue().get(i).isObject())
							removeNodes((ObjectNode) entry.getValue().get(i), removeKeys);
					}
				} else if (entryValue.isObject()) {
					removeNodes((ObjectNode) entry.getValue(), removeKeys);
				}
			}
		});
		parent.remove(removeKeyNames);
	}
	/**
	 * Find a key from hierarchy of JsonNode of given value
	 * @param node
	 * @param value
	 * @return
	 */
	public static String findKey(JsonNode node, String value) {
		Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			if (entry.getValue().isTextual() && entry.getValue().textValue().equalsIgnoreCase(value)) {
				key = entry.getKey();
				break;
			} else if (entry.getValue().isObject()) {
				findKey(entry.getValue(), value);
			}
		}
		return key;
	}

}
