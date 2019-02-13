package io.opensaber.registry;

import com.googlecode.junittoolbox.SuiteClasses;
import com.googlecode.junittoolbox.WildcardPatternSuite;
import io.opensaber.registry.dao.impl.EncryptionDaoImplTest;
import io.opensaber.registry.dao.impl.RegistryDaoImplTest;
import io.opensaber.registry.dao.impl.SearchDaoImplTest;
import io.opensaber.registry.service.impl.EncryptionServiceImplTest;
import io.opensaber.registry.service.impl.RegistryServiceImplTest;
import io.opensaber.registry.service.impl.SearchServiceImplTest;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
@RunWith(WildcardPatternSuite.class)
@SuiteClasses("**/*Test.class")
public class RegistryTestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite(RegistryTestSuite.class.getName());
		// $JUnit-BEGIN$

		// $JUnit-END$
		return suite;
	}

}
