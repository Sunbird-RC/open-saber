package io.opensaber.registry.interceptor.request.transform;

import java.io.IOException;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

public interface IRequestTransformer<T> {

    Data<T> transform(Data<T> data) throws TransformationException, IOException;

}
