package io.opensaber.registry.middleware.transform;

import java.io.IOException;
import java.util.List;

import io.opensaber.registry.middleware.util.CommunicationType;

public interface ITransformer<T> {

	public Data<T> transform(Data<Object> data) throws TransformationException, IOException;
	public Data<T> transform(Data<Object> data, CommunicationType communicationType) throws TransformationException, IOException;
	public void setPurgeData(List<String> keyToPruge);

}
