package simpledb.test.parser;

import simpledb.test.ast.And;
import simpledb.test.ast.BooleanExpression;
import simpledb.test.ast.False;
import simpledb.test.ast.Not;
import simpledb.test.ast.Or;
import simpledb.test.ast.True;
import simpledb.test.lexer.Lexerer;

public class RecursiveDescentParser {
	private Lexerer lexer;
	private int symbol;
	private BooleanExpression root;

	private final True t = new True();
	private final False f = new False();

	public RecursiveDescentParser(Lexerer lexer) {
		this.lexer = lexer;
	}

	public BooleanExpression build() {
		expression();
		return root;
	}

	private void expression() {
		term();
		while (symbol == Lexerer.OR) {
			Or or = new Or();
			or.setLeft(root);
			term();
			or.setRight(root);
			root = or;
		}
	}

	private void term() {
		factor();
		while (symbol == Lexerer.AND) {
			And and = new And();
			and.setLeft(root);
			factor();
			and.setRight(root);
			root = and;
		}
	}

	private void factor() {
		symbol = lexer.nextSymbol();
		if (symbol == Lexerer.TRUE) {
			root = t;
			symbol = lexer.nextSymbol();
		} else if (symbol == Lexerer.FALSE) {
			root = f;
			symbol = lexer.nextSymbol();
		} else if (symbol == Lexerer.NOT) {
			Not not = new Not();
			factor();
			not.setChild(root);
			root = not;
		} else if (symbol == Lexerer.LEFT) {
			expression();
			symbol = lexer.nextSymbol(); // we don't care about ')'
		} else {
			throw new RuntimeException("Expression Malformed");
		}
	}
}
