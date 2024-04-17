package simpledb.query;

public interface Scan {
    public void beforeFirst();
    public boolean next();
    public int getInt(String fieldName);
    public String getString(String fieldName);
    public boolean hasField(String fieldName);
    public void close();
    public Constant getVal(String fieldName);
}
