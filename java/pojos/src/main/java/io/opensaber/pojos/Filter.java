package io.opensaber.pojos;

import io.opensaber.pojos.FilterOperators.FilterOperator;

public class Filter {
	// Denotes the absolute path of the subject
	private String path;

	// The specific attribute being searched for
	private String property;

	// The operator
	private FilterOperator operator;

	// The value that needs to be searched
	private Object value;

	public Filter(String path) {
		this.path = path;
	}

	public Filter(String property, String operator, String value) {
		this.property = property;
		//this.operator = operator;
		this.value = value;
	}

	public void setProperty(String property) { this.property = property; }

	public String getProperty() {
		return property;
	}

	/*public String getOperator() {
		return operator;
	}*/

	public Object getValue() {
		return value;
	}

	public void setValue(Object object) { this.value = object; }

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public FilterOperator getOperator() { return this.operator;}

	public void setOperator(FilterOperator filterO) { this.operator = filterO; }
}
