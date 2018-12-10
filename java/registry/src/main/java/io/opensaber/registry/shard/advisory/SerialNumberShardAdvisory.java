package io.opensaber.registry.shard.advisory;

import org.springframework.stereotype.Component;

import io.opensaber.registry.model.DBConnectionInfo;
import io.opensaber.registry.model.DBConnectionInfoMgr;

@Component
public class SerialNumberShardAdvisory implements IShardAdvisor {

	private DBConnectionInfoMgr dBConnectionInfoMgr;
	
	public SerialNumberShardAdvisory(DBConnectionInfoMgr dBConnectionInfoMgr){
		this.dBConnectionInfoMgr = dBConnectionInfoMgr;
	}
	
	@Override
	public DBConnectionInfo getShard(Object serialNumber ) {
		
		DBConnectionInfo connectionInfo = null;
		if(serialNumber instanceof Double){
			Double serNo = ((Double) serialNumber).doubleValue();
			if (serNo % 2 == 0) {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(1);
			} else {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(0);
			}
		}else {
			if (serialNumber.toString().length() % 2 == 0) {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(1);
			} else {
				connectionInfo = dBConnectionInfoMgr.getConnectionInfo().get(0);
			}			
		}
		return connectionInfo;
	}

}
