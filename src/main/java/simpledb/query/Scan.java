package simpledb.query;

public interface Scan {
    void beforeFirst();
    boolean next();
    int getInt(String fieldName);
    String getString(String fieldName);
    boolean hasField(String fieldName);
    void close();
    Constant getVal(String fieldName);
}
