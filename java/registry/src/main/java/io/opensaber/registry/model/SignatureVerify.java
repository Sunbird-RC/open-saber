package io.opensaber.registry.model;

import org.springframework.stereotype.Component;

@Component
public class SignatureVerify {
	
	private int index;
	private Object claim;
	private int keyId;
	
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	
	public Object getClaim() {
		return claim;
	}
	public void setClaim(Object claim) {
		this.claim = claim;
	}
	public int getKeyId() {
		return keyId;
	}
	public void setKeyId(int keyId) {
		this.keyId = keyId;
	}
	
	

}
