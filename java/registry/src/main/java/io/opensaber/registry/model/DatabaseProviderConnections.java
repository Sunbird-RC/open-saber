package io.opensaber.registry.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "databases")
public class DatabaseProviderConnections {
	
	private String provider;
	private List<DatabaseConnection> connections = new ArrayList<>();
	
	public DatabaseProviderConnections(){
		
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public List<DatabaseConnection> getConnections() {
		return connections;
	}

	public void setConnections(List<DatabaseConnection> connections) {
		this.connections = connections;
	}
	//TODO: add this as a map, for optimized search.
	public DatabaseConnection getDatabaseConnection(String name){
		for(DatabaseConnection con: connections){
			if(con.getName().equalsIgnoreCase(name))
				return con;
		}
		return null;
	}
	
	
	


}
