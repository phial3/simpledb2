package simpledb.parse;

import java.io.IOException;

@SuppressWarnings("serial")
public class BadSyntaxException extends RuntimeException {
    public BadSyntaxException() {
        super();
    }
    public BadSyntaxException(Throwable e) {
        super(e);
    }
}
