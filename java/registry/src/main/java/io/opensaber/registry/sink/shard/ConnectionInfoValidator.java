package io.opensaber.registry.sink.shard;

import io.opensaber.registry.model.DBConnectionInfo;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class ConnectionInfoValidator implements ConstraintValidator<ValidConnectionInfo, Object> {

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {

		if (value instanceof DBConnectionInfo) {
			DBConnectionInfo info = (DBConnectionInfo) value;
			if (info.getShardId().isEmpty() || info.getShardLabel().isEmpty() || info.getUri().isEmpty()) {
				return false;
			}
		}
		return true;
	}

}
