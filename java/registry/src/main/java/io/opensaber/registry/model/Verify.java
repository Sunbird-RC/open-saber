package io.opensaber.registry.model;

public class Verify {
	
	private String index;
	private Object claim;
	private String signatureValue;
	private String keyId;
	
	public String getIndex() {
		return index;
	}
	public void setIndex(String index) {
		this.index = index;
	}
	public Object getClaim() {
		return claim;
	}
	public void setClaim(Object claim) {
		this.claim = claim;
	}
	public String getSignatureValue() {
		return signatureValue;
	}
	public void setSignatureValue(String signatureValue) {
		this.signatureValue = signatureValue;
	}
	public String getKeyId() {
		return keyId;
	}
	public void setKeyId(String keyId) {
		this.keyId = keyId;
	}
	
	

}
