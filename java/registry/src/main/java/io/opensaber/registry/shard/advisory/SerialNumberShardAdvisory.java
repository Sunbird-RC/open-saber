package io.opensaber.registry.shard.advisory;

import org.springframework.stereotype.Component;

import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;

@Component
public class SerialNumberShardAdvisory implements IShardAdvisory {

	private DBConnectionInfoMgr dBConnectionInfoMgr;
	
	public SerialNumberShardAdvisory(DBConnectionInfoMgr dBConnectionInfoMgr){
		this.dBConnectionInfoMgr = dBConnectionInfoMgr;
	}
	
	@Override
	public DBConnectionInfo connectionInfo(String subject) {
		
		DBConnectionInfo connectionInfo = null;
		if (subject.length() % 2 == 0) {
			connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(1);
		} else {
			connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(0);
		}
		
		return connectionInfo;
	}

}
