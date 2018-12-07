package io.opensaber.registry.shard.advisory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class AdvisoryLoader {
	
	private Map<String,IShardAdvisory> advisors = new HashMap<String,IShardAdvisory>();
	
	public void registerAdvisory(String property, IShardAdvisory shardAdvisory){		
		advisors.put(property, shardAdvisory);		
		
	}
	/**
	 * Return ShardAdvice registered with the property
	 * @return
	 * @throws IOException 
	 */
	public IShardAdvisory getShardAdvisory(String property) throws IOException{
		IShardAdvisory advisory = null;
		if(advisors.keySet().contains(property))
			advisory = advisors.get(property);
		else
			throw new IOException("Not found advisory for given property. Cosider registering this property.");
		return advisory;
	}
	
	

}
