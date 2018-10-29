package io.opensaber.registry.schema.configurator;

public interface ISchemaConfigurator {
	
	public boolean isPrivate(String propertyName);
	public boolean isEncrypted(String tailPropertyKey);
	//public boolean isSingleValued(String property);

}
