package io.opensaber.pojos;

public enum FilterOperators {
    gte("gte"), lte("lte"), contains("contains"), equals("eq"),
    gt(">"), lt("<"), eq("=");

    private String name;

    FilterOperators(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public enum FilterOperator{
        eq, // for number or strings
        lt, gt, gte, lte, between,  //for number values
        contains, startsWith, endsWith, notContains, notStartsWith, notEndsWith // for Strings value
    }
}
