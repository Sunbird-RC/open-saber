package io.opensaber.registry.sink.shard;

import io.opensaber.registry.model.DBConnectionInfoMgr;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShardAdvisor {
	private static Logger logger = LoggerFactory.getLogger(ShardAdvisor.class);
	private DBConnectionInfoMgr dbConnectionInfoMgr;

	public ShardAdvisor(DBConnectionInfoMgr dbConnectionInfoMgr) {
		this.dbConnectionInfoMgr = dbConnectionInfoMgr;
	}

	/**
	 * Return ShardAdvice invoked by ShardAdvisorClassName
	 *
	 * @return
	 * @throws IOException
	 */
	public IShardAdvisor getInstance() {
		
		String advisorClassName = dbConnectionInfoMgr.getShardAdvisorClassName();
		IShardAdvisor advisor = new DefaultShardAdvisor(dbConnectionInfoMgr);
		try {
			advisor = instantiateAdvisor(advisorClassName);
			logger.info("Invoked shard advisor class with classname: " + advisorClassName);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			logger.error("Shard advisor class {} cannot be instantiate with exception:", advisorClassName, e);
		}

		return advisor;
	}
	
	private IShardAdvisor instantiateAdvisor(String advisorClassName)
			throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Class<?> advisorClass = Class.forName(advisorClassName);
		IShardAdvisor advisor = (IShardAdvisor) advisorClass.getConstructor(DBConnectionInfoMgr.class)
				.newInstance(dbConnectionInfoMgr);
		return advisor;

	}

}
