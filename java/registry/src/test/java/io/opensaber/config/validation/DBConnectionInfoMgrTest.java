package io.opensaber.config.validation;

import static org.junit.Assert.assertEquals;

import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.junit.Before;
import org.junit.Test;

public class DBConnectionInfoMgrTest {

	private final static String NOT_EMPTY = "not empty value";
	private final static String EMPTY = "";
	private final String[] NON_UNIQUE_SHARD_VALUES = { "shardval", "shardval" };
	// private final String[] NON_UNIQUE_SHARD_VALUES =
	// {"shardval1","shardval2"};

	private Validator validator;

	@Before
	public void setUp() throws Exception {
		ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@Test
	public void testEmptyUuidProperty() {

		DBConnectionInfoMgr mgr = new DBConnectionInfoMgr();
		mgr.setProvider(NOT_EMPTY);
		mgr.setUuidPropertyName(EMPTY);
		Set<ConstraintViolation<DBConnectionInfoMgr>> violations = validator.validate(mgr);
		assertEquals(1, violations.size());

	}

	@Test
	public void testEmptyProvider() {
		DBConnectionInfoMgr mgr = new DBConnectionInfoMgr();
		mgr.setProvider(EMPTY);
		mgr.setUuidPropertyName(NOT_EMPTY);
		Set<ConstraintViolation<DBConnectionInfoMgr>> violations = validator.validate(mgr);
		assertEquals(1, violations.size());

	}

	@Test
	public void testEmptyShardId() {
		List<DBConnectionInfo> connectionInfos = new ArrayList<>();
		DBConnectionInfo ci = new DBConnectionInfo();
		ci.setShardId(EMPTY);
		ci.setShardLabel(NOT_EMPTY);
		ci.setUri(NOT_EMPTY);
		connectionInfos.add(ci);
		DBConnectionInfoMgr mgr = new DBConnectionInfoMgr();
		mgr.setProvider(NOT_EMPTY);
		mgr.setUuidPropertyName(NOT_EMPTY);
		mgr.setConnectionInfo(connectionInfos);
		Set<ConstraintViolation<DBConnectionInfoMgr>> violations = validator.validate(mgr);
		assertEquals(1, violations.size());
	}

	@Test
	public void testDuplicateShardValue() {
		List<DBConnectionInfo> connectionInfosWithNonUniqueShardLabelValues = getDBConnectionInfoList(
				NON_UNIQUE_SHARD_VALUES);
		DBConnectionInfoMgr mgr = new DBConnectionInfoMgr();
		mgr.setConnectionInfo(connectionInfosWithNonUniqueShardLabelValues);
		mgr.setProvider(NOT_EMPTY);
		mgr.setUuidPropertyName(NOT_EMPTY);
		Set<ConstraintViolation<DBConnectionInfoMgr>> violations = validator.validate(mgr);
		assertEquals(1, violations.size());

	}

	private List<DBConnectionInfo> getDBConnectionInfoList(String[] values) {
		List<DBConnectionInfo> connectionInfos = new ArrayList<>();
		for (int i = 0; i < values.length; i++) {
			DBConnectionInfo ci = new DBConnectionInfo();
			ci.setShardId(values[i]);
			ci.setShardLabel(values[i]);
			ci.setUri(NOT_EMPTY);
			connectionInfos.add(ci);
		}
		return connectionInfos;

	}

}
