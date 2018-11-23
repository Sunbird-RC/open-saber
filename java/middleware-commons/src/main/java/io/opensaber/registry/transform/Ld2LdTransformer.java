package io.opensaber.registry.transform;

import java.io.IOException;
import java.util.List;

public class Ld2LdTransformer implements ITransformer<Object> {

	@Override
	public Data<Object> transform(Data<Object> data) throws TransformationException, IOException {
		return data;
	}

	@Override
	public void setPurgeData(List<String> keyToPurge) {
		// Nothing to purge
	}

}
