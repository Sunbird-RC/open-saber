package io.opensaber.registry.util;

import java.util.ArrayList;
import java.util.List;
/**
 * Holds _osconfig properties for a schema  
 *
 */
public class OsConfigProperties {
    
    private String comment;
    private List<String> privateFields =  new ArrayList<>();
    private List<String> signedFields =  new ArrayList<>();

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<String> getPrivateFields() {
        return privateFields;
    }

    public void setPrivateFields(List<String> privateFields) {
        this.privateFields = privateFields;
    }

    public List<String> getSignedFields() {
        return signedFields;
    }

    public void setSignedFields(List<String> signedFields) {
        this.signedFields = signedFields;
    }
    
}
