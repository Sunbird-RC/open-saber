package io.opensaber.registry.schema.configurator;

import org.springframework.stereotype.Component;

@Component
public class JsonSchemaConfigurator implements ISchemaConfigurator{

	@Override
	public boolean isPrivate(String propertyName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isEncrypted(String tailPropertyKey) {
		// TODO Auto-generated method stub
		return false;
	}

}
