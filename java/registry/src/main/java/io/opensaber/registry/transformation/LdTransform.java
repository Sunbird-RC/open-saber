package io.opensaber.registry.transformation;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import io.opensaber.registry.middleware.transform.Data;
import io.opensaber.registry.middleware.transform.ITransformer;
import io.opensaber.registry.middleware.transform.TransformationException;
import io.opensaber.registry.middleware.util.CommunicationType;

@Component
public class LdTransform implements ITransformer<Object> {

	// The incoming data is a String and we need to convert to JSON.
	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException, IOException {
		return new Data<>(data);
	}

	@Override
	public void setPurgeData(List<String> keyToPurge) {
		// Nothing to purge
	}

	@Override
	public Data<Object> transform(Data<Object> data, CommunicationType communicationType)
			throws TransformationException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
