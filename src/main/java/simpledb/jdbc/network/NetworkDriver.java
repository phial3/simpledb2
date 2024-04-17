package simpledb.jdbc.network;

import simpledb.jdbc.DriverAdapter;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class NetworkDriver extends DriverAdapter {
    public Connection connect(String url, Properties prop) throws SQLException {
        try {
            // jdbc:simpledb://host:port/database?
            String replace = url.replace("jdbc:simpledb://", "");
            String[] split = replace.split("/");
            String[] hostAndPort = split[0].split(":");
            String host = hostAndPort[0];
            int port = Integer.parseInt(hostAndPort[1]);
            String databaseAndProperties = split[1];
            Registry reg = LocateRegistry.getRegistry(host, port);
            RemoteDriver rDrv = (RemoteDriver) reg.lookup("simpledb");
            RemoteConnection rConn = rDrv.connect();
            return new NetworkConnection(rConn);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
