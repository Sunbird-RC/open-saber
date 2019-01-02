package io.opensaber.registry.sink.shard;

import io.opensaber.registry.model.DBConnectionInfo;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class ShardValidator implements ConstraintValidator<ValidConnectionInfo, Object> {

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {

		if (value instanceof DBConnectionInfo) {
			DBConnectionInfo info = (DBConnectionInfo) value;
			if (info.getShardId() != null && info.getShardLabel() != null && info.getUri() != null) {
				return true;
			}
		}
		return false;
	}

}
