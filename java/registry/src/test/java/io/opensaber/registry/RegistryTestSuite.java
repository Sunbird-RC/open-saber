package io.opensaber.registry;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import junit.framework.Test;
import junit.framework.TestSuite;

@SuiteClasses({ /*RegistryDaoImplTest.class,*/ /*RegistryServiceImplTest.class,*//* EncryptionDaoImplTest.class,*/
		/*EncryptionServiceImplTest.class,*//* SearchServiceImplTest.class*//*, SearchDaoImplTest.class*/ })
@RunWith(Suite.class)
public class RegistryTestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite(RegistryTestSuite.class.getName());
		// $JUnit-BEGIN$

		// $JUnit-END$
		return suite;
	}

}
