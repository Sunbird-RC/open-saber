package io.opensaber.validators;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.opensaber.pojos.APIMessage;
import io.opensaber.registry.middleware.Middleware;
import io.opensaber.registry.middleware.MiddlewareHaltException;

@Component
public class ValidationFilter implements Middleware {
	private IValidate validationService;

	@Autowired
	private APIMessage apiMessage;

	public ValidationFilter(IValidate validationServiceImpl) {
		this.validationService = validationServiceImpl;
	}

	@Override
	public Map<String, Object> execute(Map<String, Object> mapData) throws MiddlewareHaltException {
		validationService.validate(apiMessage);
		return mapData;
	}

    @Override
    public Map<String, Object> next(Map<String, Object> mapData) throws IOException {
        return null;
    }
}
