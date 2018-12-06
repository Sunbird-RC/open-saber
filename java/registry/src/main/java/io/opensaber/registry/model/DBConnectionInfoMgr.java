package io.opensaber.registry.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/**
 * 
 * @author Pritha Chattopadhyay
 * Auto populate/bind the field values from yaml properties.
 *
 */
@Component
@ConfigurationProperties(prefix = "database")
public class DBConnectionInfoMgr {
	
	/**
	 * get the provider property value from properties or yaml file
	 */
	private String provider;
	/**
	 * get the connectionInfo list of properties values from yaml/properties file.
	 */
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
	/**
	 * To provide a connection info on based of a shard identifier(name)
	 * @param name
	 * @return
	 */
	public DBConnectionInfo getDatabaseConnection(String name){
		for(DBConnectionInfo con: connectionInfo){
			if(con.getName().equalsIgnoreCase(name))
				return con;
		}
		return null;
	}


}
