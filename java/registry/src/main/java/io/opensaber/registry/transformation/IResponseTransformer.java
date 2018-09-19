package io.opensaber.registry.transformation;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.ResponseData;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

public interface IResponseTransformer<T> {

    ResponseData<T> transform(Data<T> data) throws TransformationException;

}
