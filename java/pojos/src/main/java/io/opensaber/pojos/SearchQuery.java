package io.opensaber.pojos;

import java.util.List;

public class SearchQuery {

	private List<Filter> filters;

	// default limit of 1000 records at a time
	private int limit = 1000;
	private int offset;
	private List<String> fields;
	private String rootLabel;

	public SearchQuery(String rootLabel) {
		this.rootLabel = rootLabel;
	}

	public List<Filter> getFilters() {
		return filters;
	}

	public void setFilters(List<Filter> filters) {
		this.filters = filters;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
	    if(limit <= 1000){
	        this.limit = limit; 
	    }
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public List<String> getFields() {
		return fields;
	}

	public void setFields(List<String> fields) {
		this.fields = fields;
	}

	public String getRootLabel() {
		return rootLabel;
	}

	public void setRootLabel(String rootLabel) {
		this.rootLabel = rootLabel;
	}
}
