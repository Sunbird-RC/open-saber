package io.opensaber.registry.interceptor;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opensaber.registry.middleware.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import io.opensaber.pojos.APIMessage;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.middleware.Middleware;

@Component
public class ValidationInterceptor implements HandlerInterceptor {

	private static Logger logger = LoggerFactory.getLogger(ValidationInterceptor.class);

	private Middleware validationFilter;

	@Autowired
	private APIMessage apiMessage;

	@Autowired
	private OpenSaberInstrumentation watch;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		boolean result = false;
		watch.start("ValidationInterceptor.execute");
		Map<String, Object> data = apiMessage.getRequest().getRequestMap();
		data.put(Constants.METHOD_ORIGIN, apiMessage.getRequestWrapper().getRequestURI());
		Map<String, Object> attributeMap = validationFilter.execute(apiMessage.getRequest().getRequestMap());
		watch.stop("ValidationInterceptor.execute");
		result = attributeMap.containsKey("isValid");
		return result;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
	}

}
