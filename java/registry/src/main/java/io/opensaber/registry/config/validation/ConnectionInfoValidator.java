package io.opensaber.registry.config.validation;

import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import java.util.List;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class ConnectionInfoValidator implements ConstraintValidator<ValidConnectionInfo, DBConnectionInfoMgr> {

	@Override
	public boolean isValid(DBConnectionInfoMgr mgr, ConstraintValidatorContext context) {

		boolean isValid = true;
		String message = null;
		if (mgr.getProvider().isEmpty() || mgr.getUuidPropertyName().isEmpty()) {
			isValid = false;
			message = "database.provider or database.uuidPropertyName is empty";
		}
		if (mgr.getConnectionInfo().size() < 1) {
			isValid = false;
			message = "database.connectioninfo[0] not found";
		}
		for (DBConnectionInfo info : mgr.getConnectionInfo()) {
			if (info.getShardId().isEmpty() || info.getShardLabel().isEmpty() || info.getUri().isEmpty()) {
				isValid = false;
				message = "database.connectionInfo.shardId or database.connectionInfo.shardLabel or database.connectionInfo.uri is empty";
				break;
			}
			if (notUnique(mgr.getConnectionInfo(), info.getShardId(), info.getShardLabel())) {
				isValid = false;
				message = "database.connectionInfo.shardId and database.connectionInfo.shardLabe must be unique";
				break;
			}
		}

		if (!isValid)
			setMessage(context, message);

		return isValid;
	}

	private void setMessage(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
	}

	private boolean notUnique(final List<DBConnectionInfo> list, final String shardId, String shardLabel) {
		boolean shardIdNotUnique = list.stream().filter(o -> o.getShardId().equals(shardId)).count() > 1;
		boolean shardLabelNotUnique = list.stream().filter(o -> o.getShardLabel().equals(shardLabel)).count() > 1;
		return (shardIdNotUnique || shardLabelNotUnique);
	}

}
