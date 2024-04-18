package simpledb.test.ast;

public class MainTest {
	public static void main(String[] args) {
		True t = new True();
		False f = new False();

		Or or = new Or();
		or.setLeft(t);
		or.setRight(f);

		Not not = new Not();
		not.setChild(f);

		And and = new And();
		and.setLeft(or);
		and.setRight(not);

		BooleanExpression root = and;

		System.out.println(root);
		System.out.println(root.interpret());
	}
}
