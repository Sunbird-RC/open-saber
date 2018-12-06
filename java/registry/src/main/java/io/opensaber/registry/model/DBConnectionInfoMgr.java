package io.opensaber.registry.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "database")
public class DBConnectionInfoMgr {
	
	private String provider;
	private List<DBConnectionInfo> connectionInfo = new ArrayList<>();
	
	public DBConnectionInfoMgr(){
		
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public List<DBConnectionInfo> getConnectionInfo() {
		return connectionInfo;
	}

	public void setConnectionInfo(List<DBConnectionInfo> connectionInfo) {
		this.connectionInfo = connectionInfo;
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
