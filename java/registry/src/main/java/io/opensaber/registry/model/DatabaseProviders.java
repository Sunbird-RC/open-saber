package io.opensaber.registry.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "database")
public class DatabaseProviders {
	
	private String provider;
	private List<DBConnectionInfo> connectionInfo = new ArrayList<>();
	
	public DatabaseProviders(){
		
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public List<DBConnectionInfo> getConnections() {
		return connectionInfo;
	}

	public void setConnections(List<DBConnectionInfo> connections) {
		this.connectionInfo = connections;
	}
	//TODO: add this as a map, for optimized search.
	public DBConnectionInfo getDatabaseConnection(String name){
		for(DBConnectionInfo con: connectionInfo){
			if(con.getName().equalsIgnoreCase(name))
				return con;
		}
		return null;
	}
	
	
	


}
