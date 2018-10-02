package io.opensaber.registry.transformation;

import java.io.IOException;
import java.util.List;

import io.opensaber.registry.middleware.transform.commoms.Data;
import io.opensaber.registry.middleware.transform.commoms.TransformationException;



public interface IResponseTransformer<T> {

    Data<T> transform(Data<Object> data, List<String> keyToTrim) throws TransformationException, IOException;

}
