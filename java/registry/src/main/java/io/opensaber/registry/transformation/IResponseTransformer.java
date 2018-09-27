package io.opensaber.registry.transformation;

import java.io.IOException;
import java.util.List;

import io.opensaber.registry.middleware.transform.commons.Data;
import io.opensaber.registry.middleware.transform.commons.TransformationException;

public interface IResponseTransformer<T> {

    Data<T> transform(Data<T> data, List<String> keyToTrim) throws TransformationException, IOException;

}
