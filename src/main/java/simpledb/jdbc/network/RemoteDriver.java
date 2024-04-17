package simpledb.jdbc.network;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteDriver extends Remote {
    RemoteConnection connect() throws RemoteException;
}
