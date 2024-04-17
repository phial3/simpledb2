package simpledb;

import simpledb.jdbc.embedded.EmbeddedDriver;
import simpledb.jdbc.network.NetworkDriver;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Properties;
import java.util.Scanner;

public class SimpleClient {

    public void start() {
        Properties properties = new Properties();
        properties.put("DB_DIR", System.getProperty("DB_DIR", "dbdir"));

        Scanner sc = new Scanner(System.in);
        System.out.print("Connect> ");
        String line = sc.nextLine();
        // jdbc:simpledb://localhost:1999/database?
        Driver driver = line.contains("jdbc:simpledb://") ? new NetworkDriver() :
                (line.matches("^[a-zA-Z0-9_\\-]+$") ? new EmbeddedDriver() : null);
        if (driver == null) {
            throw new IllegalArgumentException("Invalid connection string: " + line);
        }
        try {
            try (java.sql.Connection conn = driver.connect(line, properties)) {
                try (Statement stmt = conn.createStatement()) {
                    System.out.print("\nSQL> ");
                    while (sc.hasNextLine()) {
                        // process one line of input
                        String cmd = sc.nextLine().trim();
                        if (cmd.startsWith("exit")) {
                            break;
                        } else if (cmd.startsWith("select")) {
                            doQuery(stmt, cmd);
                        } else {
                            doUpdate(stmt, cmd);
                        }
                        System.out.print("\nSQL> ");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        sc.close();
    }

    private void doQuery(Statement stmt, String cmd) {
        try {
            try (java.sql.ResultSet rs = stmt.executeQuery(cmd)) {
                java.sql.ResultSetMetaData md = rs.getMetaData();
                int numcols = md.getColumnCount();
                int totalwidth = 0;

                // print header
                for (int i = 1; i <= numcols; i++) {
                    String fldname = md.getColumnName(i);
                    int width = md.getColumnDisplaySize(i);
                    totalwidth += width;
                    System.out.format("%" + width + "s", fldname);
                }
                System.out.println();
                for (int i = 0; i < totalwidth; i++) System.out.print("-");
                System.out.println();

                // print records
                while (rs.next()) {
                    for (int i = 1; i <= numcols; i++) {
                        String fldname = md.getColumnName(i);
                        int fldtype = md.getColumnType(i);
                        int width = md.getColumnDisplaySize(i);
                        if (fldtype == Types.INTEGER) {
                            int ival = rs.getInt(fldname);
                            System.out.format("%" + width + "d", ival);
                        } else {
                            String sval = rs.getString(fldname);
                            System.out.format("%" + width + "s", sval);
                        }
                    }
                    System.out.println();
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL Exception: " + e.getMessage());
        }
    }

    private void doUpdate(Statement stmt, String cmd) {
        try {
            int howmany = stmt.executeUpdate(cmd);
            System.out.println(howmany + " records processed");
        } catch (SQLException e) {
            System.out.println("SQL Exception: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new SimpleClient().start();
    }
}

