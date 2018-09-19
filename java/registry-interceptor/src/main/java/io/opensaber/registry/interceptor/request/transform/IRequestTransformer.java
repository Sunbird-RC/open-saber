package io.opensaber.registry.interceptor.request.transform;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

public interface IRequestTransformer<T> {

    ResponseData<T> transform(Data<T> data) throws TransformationException;

}
