package io.opensaber.registry.sink.shard;

import io.opensaber.registry.model.DBConnectionInfoMgr;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShardAdvisor {
	private static Logger logger = LoggerFactory.getLogger(ShardAdvisor.class);
	private DBConnectionInfoMgr dbConnectionInfoMgr;
	private Map<String, IShardAdvisor> advisors = new HashMap<String, IShardAdvisor>();


	public ShardAdvisor(DBConnectionInfoMgr dbConnectionInfoMgr) {
		this.dbConnectionInfoMgr = dbConnectionInfoMgr;
		try {
			registerShardAdvisor();
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			logger.error("While registering shard advisor with name {}, exception occured: {}",
					dbConnectionInfoMgr.getShardAdvisor(), e);
		}
	}

	private void registerShardAdvisor() throws ClassNotFoundException, NoSuchMethodException, SecurityException,
			InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String advisorClassName = dbConnectionInfoMgr.getShardAdvisor();
		Class<?> clazz = Class.forName("io.opensaber.registry.sink.shard." + advisorClassName);
		IShardAdvisor advisor = (IShardAdvisor) clazz.getConstructor(DBConnectionInfoMgr.class)
				.newInstance(dbConnectionInfoMgr);
		logger.info("Registered shard advisor class: "+advisorClassName);
		advisors.put(advisorClassName, advisor);
		
	}

	/**
	 * Return ShardAdvice registered with the property
	 *
	 * @return
	 * @throws IOException
	 */

	public IShardAdvisor getShardAdvisor() {
		IShardAdvisor advisory = advisors.getOrDefault(dbConnectionInfoMgr.getShardAdvisor(),
				new DefaultShardAdvisor(dbConnectionInfoMgr));
		return advisory;
	}

}
