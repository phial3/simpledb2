package simpledb.test.ast;

public class False extends Terminal {
	public False() {
		super(false);
	}

	public boolean interpret() {
		return value;
	}
}
