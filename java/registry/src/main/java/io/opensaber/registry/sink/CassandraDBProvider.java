package io.opensaber.registry.sink;

import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.sink.DatabaseProvider;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;

public class CassandraDBProvider extends DatabaseProvider {
    private static boolean isConnection = false;
    private static CassandraOperation cassandraOperation = null;
    private static CassandraConnectionManager cassandraConnectionManager = null;
    @Override
    public void shutdown() throws Exception {

    }

    @Override
    public OSGraph getOSGraph() {
        return null;
    }

    static void checkCassandraDbConnections(DBConnectionInfoMgr dbConnectionInfoMgr, String keySpace) {
        String uri = dbConnectionInfoMgr.getConnectionInfo().get(0).getUri();
        String[] urlDetail = uri.split(":");
        String host = urlDetail[0];
        String port = urlDetail[1];
        cassandraOperation = ServiceFactory.getInstance();
        cassandraConnectionManager =
                CassandraConnectionMngrFactory.getObject("standalone");
        isConnection =
                cassandraConnectionManager.createConnection(host, port, null, null, keySpace);
    }

    public CassandraOperation getCassandraOperation(DBConnectionInfoMgr dbConnectionInfoMgr, String keySpace) {
        if(!isConnection && cassandraOperation == null) {
            checkCassandraDbConnections(dbConnectionInfoMgr, keySpace);
        }
        return cassandraOperation;
    }
}
