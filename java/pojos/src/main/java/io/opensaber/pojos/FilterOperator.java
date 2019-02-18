package io.opensaber.pojos;

public enum FilterOperator{
    eq, or,                                                                    // for object
    lt, gt, gte, lte, between,                                                 // for object
    contains, startsWith, endsWith, notContains, notStartsWith, notEndsWith    // for Strings value
}
