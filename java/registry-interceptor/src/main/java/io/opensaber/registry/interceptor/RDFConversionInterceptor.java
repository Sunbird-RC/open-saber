package io.opensaber.registry.interceptor;

import com.google.gson.Gson;
import io.opensaber.pojos.OpenSaberInstrumentation;
import io.opensaber.registry.interceptor.handler.APIMessage;
import io.opensaber.registry.interceptor.request.transform.RequestTransformFactory;
import io.opensaber.registry.middleware.Middleware;
import io.opensaber.registry.middleware.transform.Data;
import io.opensaber.registry.middleware.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Component
public class RDFConversionInterceptor implements HandlerInterceptor {

	private static Logger logger = LoggerFactory.getLogger(RDFConversionInterceptor.class);
	private Middleware rdfConverter;
	private Gson gson;

	@Autowired
	private OpenSaberInstrumentation watch;

	@Autowired
	private APIMessage apiMessage;

	@Autowired
	private RequestTransformFactory requestTransformFactory;

	public RDFConversionInterceptor(Middleware rdfConverter, Gson gson) {
		this.rdfConverter = rdfConverter;
		this.gson = gson;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		boolean result = false;
		String dataFromRequest = apiMessage.getRequest().getRequestMapAsString();
		String contentType = request.getContentType();
		logger.debug("ContentType {0} requestBody {1}", contentType, dataFromRequest);

		Data<Object> transformedData = requestTransformFactory.getInstance(contentType).transform(new Data<Object>(dataFromRequest));
		logger.debug("After transformation {0}", transformedData.getData());

		apiMessage.addLocal(Constants.LD_OBJECT, transformedData.getData().toString());

		watch.start("RDFConversionInterceptor.execute");
		Map<String, Object> attributeMap = rdfConverter.execute(apiMessage.getLocalMap());
		watch.stop("RDFConversionInterceptor.execute");

		if (attributeMap.get(Constants.RDF_OBJECT) != null) {
			apiMessage.addLocal(Constants.RDF_OBJECT, attributeMap.get(Constants.RDF_OBJECT));

			logger.debug("RDF object for conversion : {0}", attributeMap.get(Constants.RDF_OBJECT));
			result = true;
		}

		return result;
	}
}
