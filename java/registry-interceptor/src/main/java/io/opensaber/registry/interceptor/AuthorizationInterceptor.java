package io.opensaber.registry.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.google.gson.Gson;

import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.interceptor.handler.APIMessage;
import io.opensaber.registry.middleware.Middleware;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

	private static Logger logger = LoggerFactory.getLogger(AuthorizationInterceptor.class);
	private Middleware authorizationFilter;

	private Gson gson;

	@Autowired
	private APIMessage apiMessage;

	@Autowired
	private OpenSaberInstrumentation watch;

	public AuthorizationInterceptor(Middleware authorizationFilter, Gson gson) {
		this.authorizationFilter = authorizationFilter;
		this.gson = gson;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		watch.start("AuthorizationInterceptor.execute");
		authorizationFilter.execute(apiMessage.getRequestWrapper().getRequestHeaderMap());
		watch.stop("AuthorizationInterceptor.execute");
		logger.debug(" Authentication successfull !");

		return true;
	}
}
