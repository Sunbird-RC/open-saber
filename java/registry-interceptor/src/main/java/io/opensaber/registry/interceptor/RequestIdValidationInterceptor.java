package io.opensaber.registry.interceptor;

import com.google.gson.Gson;
import io.opensaber.registry.interceptor.handler.APIMessage;
import io.opensaber.registry.interceptor.handler.RequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Component
public class RequestIdValidationInterceptor implements HandlerInterceptor {
	private static Logger logger = LoggerFactory.getLogger(RequestIdValidationInterceptor.class);
	private Gson gson;
	private Map<String, String> requestIdMap;

	@Autowired
	private APIMessage apiMessage;

	public RequestIdValidationInterceptor(Map requestIdMap, Gson gson) {
		this.gson = gson;
		this.requestIdMap = requestIdMap;
	}

	/**
	 * This method checks for each request it contains a valid request id for
	 * accessing the api
	 * 
	 * @param request
	 * @param response
	 * @param handler
	 * @return true or false
	 * @throws Exception
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		RequestWrapper wrapper = apiMessage.getRequestWrapper();
		String expectedAPI = requestIdMap.getOrDefault(wrapper.getRequestURI(), "");

		return !expectedAPI.isEmpty();
	}

}
