package io.opensaber.registry.util;

/**
 * ReadConfigurator controls the data visible to the end user.
 */
public class ReadConfigurator {
    /**
     * Whether or not to include @type attributes
     * True, by default
     */
    private boolean includeTypeAttributes = true;

    /**
     * Whether or not to eagerly fetch the children
     * True, by default
     */
    private boolean fetchChildren = true;

    /**
     * Whether or not to include encrypted properties
     * False, by default
     */
    private boolean includeEncryptedProp = false;

    public boolean isIncludeTypeAttributes() {
        return includeTypeAttributes;
    }

    public void setIncludeTypeAttributes(boolean includeTypeAttributes) {
        this.includeTypeAttributes = includeTypeAttributes;
    }

    public boolean isFetchChildren() {
        return fetchChildren;
    }

    public void setFetchChildren(boolean fetchChildren) {
        this.fetchChildren = fetchChildren;
    }

    public boolean isIncludeEncryptedProp() {
        return includeEncryptedProp;
    }

    public void setIncludeEncryptedProp(boolean includeEncryptedProp) {
        this.includeEncryptedProp = includeEncryptedProp;
    }
}
