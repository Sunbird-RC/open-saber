package io.opensaber.registry.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opensaber.pojos.ValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.google.gson.Gson;

import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.interceptor.handler.BaseRequestHandler;
import io.opensaber.registry.middleware.Middleware;
import io.opensaber.registry.middleware.MiddlewareHaltException;

import java.util.Map;

@Component
public class ValidationInterceptor implements HandlerInterceptor {

	private static Logger logger = LoggerFactory.getLogger(ValidationInterceptor.class);
	private Middleware validationFilter;

	private Gson gson;

	@Autowired
	private OpenSaberInstrumentation watch;

	public ValidationInterceptor(Middleware validationFilter, Gson gson) {
		this.validationFilter = validationFilter;
		this.gson = gson;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		BaseRequestHandler baseRequestHandler = new BaseRequestHandler();
		try {
			baseRequestHandler.setRequest(request);
			watch.start("RDFValidationInterceptor.execute");
			Map<String, Object> attributeMap = validationFilter.execute(baseRequestHandler.getRequestAttributeMap());
			baseRequestHandler.mergeRequestAttributes(attributeMap);
			watch.stop("RDFValidationInterceptor.execute");
			request = baseRequestHandler.getRequest();
			ValidationResponse validationResponse = new ValidationResponse("rdf successfully validated");
			validationResponse.setValid(true);
			//request.getAttribute(Constants.RDF_VALIDATION_OBJECT);
			if (validationResponse != null && validationResponse.isValid()) {
				logger.info("RDF Validated successfully !");
				return true;
			} else {
				logger.info("RDF Validation failed!");
				baseRequestHandler.setResponse(response);
				baseRequestHandler.writeResponseObj(validationResponse.getError(), validationResponse);
				response = baseRequestHandler.getResponse();
			}
		} catch (MiddlewareHaltException e) {
			logger.error("MiddlewareHaltException from RDFValidationInterceptor: ", e);
			baseRequestHandler.setResponse(response);
			baseRequestHandler.writeResponseObj(gson, e.getMessage());
			response = baseRequestHandler.getResponse();
		} catch (Exception e) {
			logger.error("Exception from RDFValidationInterceptor: ", e);
			baseRequestHandler.setResponse(response);
			baseRequestHandler.writeResponseObj(gson, "some validation error");
			response = baseRequestHandler.getResponse();
		}
		return false;
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
