package io.opensaber.registry.sink.shard;

import io.opensaber.registry.model.DBConnectionInfoMgr;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShardAdvisor {
	private static Logger logger = LoggerFactory.getLogger(ShardAdvisor.class);
	private Map<String, IShardAdvisor> advisors = new HashMap<String, IShardAdvisor>();
	@Autowired
	private DefaultShardAdvisor defaultShardAdvisor;

	public void registerShardAdvisor(String advisorClassName, DBConnectionInfoMgr dbConnectionInfoMgr)
			throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Class<?> clazz = Class.forName(advisorClassName);
		IShardAdvisor advisor = (IShardAdvisor) clazz.getConstructor(DBConnectionInfoMgr.class)
				.newInstance(dbConnectionInfoMgr);
		logger.info("Registered shard advisor class: " + advisorClassName);
		advisors.put(advisorClassName, advisor);

	}

	/**
	 * Return ShardAdvice registered with the property
	 *
	 * @return
	 * @throws IOException
	 */

	public IShardAdvisor getShardAdvisor(String advisorClassName) {
		IShardAdvisor advisory = advisors.getOrDefault(advisorClassName, defaultShardAdvisor);
		return advisory;
	}

}
