package io.opensaber.registry.interceptor.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.opensaber.pojos.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component("apiMessage")
public class APIMessage {
	private static Logger logger = LoggerFactory.getLogger(APIMessage.class);
	private String body;

	private HttpServletRequest httpServletRequest;

	private RequestWrapper requestWrapper;

	private Request request;
	private Map<String, Object> placeholderMap = new HashMap<>();

	public APIMessage(HttpServletRequest servletRequest) {
		httpServletRequest = servletRequest;
		request = new Request();
		requestWrapper = new RequestWrapper(httpServletRequest);
		body = requestWrapper.getBody();
		try {
			request = new ObjectMapper().readValue(body, Request.class);
		} catch (IOException jpe) {
			logger.error("Can't read request body");
			request = null;
		}
	}

	public String get() {
		return body;
	}

	public RequestWrapper getRequestWrapper() {
		return requestWrapper;
	}

	public Request getRequest() {
		return request;
	}

	public void addLocal(String key, Object data) {
	    placeholderMap.put(key, data);
    }

    public Object getLocal(String key) {
	    return placeholderMap.get(key);
    }

    public Map<String, Object> getLocalMap() {
		return placeholderMap;
	}
}
