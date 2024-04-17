package simpledb.jdbc.embedded;

import simpledb.server.SimpleDB;

import java.sql.SQLException;
import java.util.Properties;

public class EmbeddedDriver extends DriverAdapter {
    @Override
    public EmbeddedConnection connect(String url, Properties info) throws SQLException {
        String dbName = url.replace("jdbc:simpledb:", "");
        String dbDir = info.getProperty("DB_DIR", "dbdir").trim().replaceAll("/", "");
        SimpleDB db = new SimpleDB(dbDir + "/" + dbName);
        return new EmbeddedConnection(db);
    }
}
