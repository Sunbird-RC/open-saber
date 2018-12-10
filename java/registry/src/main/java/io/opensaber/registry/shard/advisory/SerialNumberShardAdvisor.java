package io.opensaber.registry.shard.advisory;

import org.springframework.stereotype.Component;

import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;

@Component
public class SerialNumberShardAdvisor implements IShardAdvisor {

	private String shardId;
	private DBConnectionInfoMgr dBConnectionInfoMgr;

	public SerialNumberShardAdvisor(DBConnectionInfoMgr dBConnectionInfoMgr) {
		this.dBConnectionInfoMgr = dBConnectionInfoMgr;
	}

	@Override
	public DBConnectionInfo getShard(Object serialNumber) {

		DBConnectionInfo connectionInfo = null;
		if (serialNumber != null) {
			if (serialNumber.toString().length() % 2 == 0) {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(1);
			} else {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(0);
			}
		} else if (serialNumber instanceof Integer) {
			Integer serNo = (Integer) serialNumber;
			if (serNo % 2 == 0) {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(1);
			} else {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(0);
			}

		}
		shardId = connectionInfo.getShardId();
		return connectionInfo;
	}

	@Override
	public String shardId() {
		return shardId;
	}

}
