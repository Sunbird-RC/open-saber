package io.opensaber.registry.middleware.transform;

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

	
	private String frameFileName;
	private String registryContextBase;

	private String frameContent;
	
	public FrameContext(String frameFileName, String registryContextBase){
		this.frameFileName = frameFileName;
	}
	
	
	public String getContent() {
		InputStreamReader in;
		try {
			in = new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(frameFileName));
			frameContent = CharStreams.toString(in);
			return frameContent;

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			logger.info(e1.getLocalizedMessage());

		} catch (IOException e) {
			e.printStackTrace();
			logger.info(e.getLocalizedMessage());

		}
		return null;
	}
	
	public String getDomain() throws IOException{
		

		ObjectMapper mapper = new ObjectMapper();
		JsonNode frameNode = (ObjectNode) mapper.readTree(frameContent);
		String domainName = "";	
		Iterator<Map.Entry<String, JsonNode>> fields = frameNode.fields();
		 while (fields.hasNext()) {
		    Map.Entry<String, JsonNode> entry = fields.next();

		    if(entry.getValue().isTextual() && entry.getValue().textValue().equalsIgnoreCase(registryContextBase) ){
		    	domainName = entry.getKey();
		    	break;
		    }
		 }

		return domainName;
	}

}
