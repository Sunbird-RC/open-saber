package io.opensaber.pojos;

public enum FilterOperators {
    gte(">="), lte("<="), contains("contains"),
    gt(">"), lt("<"), eq("="),
    between("bet");

    private String name;

    FilterOperators(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
