package io.opensaber.registry.util;

import java.util.List;

/**
 * Pojo to hold defination nodes Http request must get marshal to
 * DefinationNodes Object
 *
 */
public class DefinitionNodes {

	private List<DefinationNode> definationNodes;

	public List<DefinationNode> getDefinationNodes() {
		return definationNodes;
	}

	public void setDefinationNodes(List<DefinationNode> definationNodes) {
		this.definationNodes = definationNodes;
	}
	
	
	// TODO: to add all the other key of json request.

}
