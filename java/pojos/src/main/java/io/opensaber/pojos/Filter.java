package io.opensaber.pojos;

public class Filter {
	// Denotes the absolute path of the subject
	private String path;

	// The specific attribute being searched for
	private String property;

	// The operator
	private FilterOperators operator;

	// The value that needs to be searched
	private Object value;

	public Filter(String property, String operator, Object value) {
		this.property = property;
		//this.operator = operator;
		this.value = value;
	}

	public String getProperty() {
		return property;
	}

	/*public String getOperator() {
		return operator;
	}*/

	public Object getValue() {
		return value;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public FilterOperators getOperator() { return this.operator; }
}
