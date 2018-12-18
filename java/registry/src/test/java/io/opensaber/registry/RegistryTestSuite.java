package io.opensaber.registry;

import io.opensaber.registry.dao.impl.EncryptionDaoImplTest;
import io.opensaber.registry.dao.impl.RegistryDaoImplTest;
import io.opensaber.registry.dao.impl.SearchDaoImplTest;
import io.opensaber.registry.service.impl.EncryptionServiceImplTest;
import io.opensaber.registry.service.impl.RegistryServiceImplTest;
import io.opensaber.registry.service.impl.SearchServiceImplTest;
import io.opensaber.registry.util.EntityCacheTest;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@SuiteClasses({ RegistryDaoImplTest.class, RegistryServiceImplTest.class, EncryptionDaoImplTest.class,
		EncryptionServiceImplTest.class, SearchServiceImplTest.class, SearchDaoImplTest.class, EntityCacheTest.class })
@RunWith(Suite.class)
public class RegistryTestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite(RegistryTestSuite.class.getName());
		// $JUnit-BEGIN$

		// $JUnit-END$
		return suite;
	}

}
