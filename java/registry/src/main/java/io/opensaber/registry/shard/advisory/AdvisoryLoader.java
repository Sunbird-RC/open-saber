package io.opensaber.registry.shard.advisory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class AdvisoryLoader {
	
	private Map<String,IShardAdvisor> advisors = new HashMap<String,IShardAdvisor>();
	
	public void registerAdvisory(String property, IShardAdvisor shardAdvisory){		
		advisors.put(property, shardAdvisory);		
		
	}
	/**
	 * Return ShardAdvice registered with the property
	 * @return
	 * @throws IOException 
	 */
	public IShardAdvisor getShardAdvisory(String property) throws IOException{
		IShardAdvisor advisory = null;
		if(advisors.keySet().contains(property))
			advisory = advisors.get(property);
		else
			throw new IOException("Not found advisory for given property. Cosider registering this property.");
		return advisory;
	}
	
	

}
