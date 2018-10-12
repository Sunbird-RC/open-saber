package io.opensaber.registry.service;

import io.opensaber.registry.exception.SignatureException;
import io.opensaber.registry.model.SignRequest;
import io.opensaber.registry.model.VerifyRequest;

public interface SignatureService {

    Object sign(Object propertyValue) throws SignatureException.UnreachableException,
            SignatureException.CreationException;

    Object verify(Object propertyValue) throws SignatureException.UnreachableException,
            SignatureException.VerificationException;

    String getKey(String keyId) throws SignatureException.UnreachableException,
            SignatureException.KeyNotFoundException;

    boolean isServiceUp() throws SignatureException.UnreachableException;
    
    Object sign(SignRequest signatureClaim) throws SignatureException.UnreachableException,
	SignatureException.CreationException;

	Object verify(VerifyRequest verifyRequest) throws SignatureException.UnreachableException,
	SignatureException.VerificationException;

}
