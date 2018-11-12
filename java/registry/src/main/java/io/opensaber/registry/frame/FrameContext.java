package io.opensaber.registry.frame;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.CharStreams;

public class FrameContext {
	private static Logger logger = LoggerFactory.getLogger(FrameContext.class);

	private String registryContextBase;
	private String frameContent;
	private String domainName = "";

	public FrameContext(String frameFileName, String registryContextBase) {
		this.registryContextBase = registryContextBase;

		InputStreamReader in;
		try {
			in = new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(frameFileName));
			frameContent = CharStreams.toString(in);

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			logger.error(e1.getLocalizedMessage());

		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getLocalizedMessage());

		}
	}

	public String getContent() {
		return frameContent;
	}

	public String getDomain() {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode frameNode = null;
		logger.info(
				"for FrameContext registryContextBase: " + registryContextBase + " and frame content: " + frameContent);
		try {
			frameNode = (ObjectNode) mapper.readTree(frameContent);
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getLocalizedMessage());
		}
		findDomain(frameNode);
		return domainName;
	}

	private void findDomain(JsonNode node) {
		Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			if (entry.getValue().isTextual() && entry.getValue().textValue().equalsIgnoreCase(registryContextBase)) {
				domainName = entry.getKey();
				break;
			} else if (entry.getValue().isObject()) {
				findDomain(entry.getValue());
			}
		}

	}

}
