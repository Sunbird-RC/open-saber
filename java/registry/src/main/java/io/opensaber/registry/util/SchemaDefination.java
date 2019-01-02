package io.opensaber.registry.util;

import java.util.List;
/**
 * Currently provide skeleton for SchemaDefination only
 * TODO: implement methods.
 */
public class SchemaDefination {
	
	private DefinationNode defination;
	
	public SchemaDefination(DefinationNode defination){
		this.defination = defination;
	}
	
	public String getTitle(){
		return null;
	}
	
	public String getContent(){
		return null;
	}
	
	public List<String> getSignedFields(){
		return null;
	}
	
	public List<String> getPrivateFields(){
		return null;
	}

	public boolean isEncrypted(String fieldName){
		return true;
	}
	public boolean isPrivate(String fieldName){
		return true;
	}
	

}
