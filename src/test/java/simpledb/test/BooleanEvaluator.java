package simpledb.test;

import simpledb.test.ast.BooleanExpression;
import simpledb.test.lexer.Lexerer;
import simpledb.test.parser.RecursiveDescentParser;
import java.io.ByteArrayInputStream;
import java.util.Scanner;

/**
 * A boolean expression evaluator project using a Recursive Descent Parser and the Interpreter Pattern
 * example usage:
 * $ cat test | java test/BooleanEvaluator -f
 * (true & ((true | false) & !(true & false)))
 * AST: (true & ((true | false) & !(true & false)))
 * RES: true
 */
public class BooleanEvaluator {
    public static void main(String[] args) throws InterruptedException {
        Scanner sc = new Scanner((System.in));
        String expression = "";
        if (args.length > 0 && args[0].equals("-f")) {
            while (sc.hasNextLine()) {
                expression += sc.nextLine();
            }
            System.out.println(expression);
        } else {
            System.out.println("Insert an expression:");
            expression = sc.nextLine();
        }

        Lexerer lexer = new Lexerer(new ByteArrayInputStream(expression.getBytes()));
        RecursiveDescentParser parser = new RecursiveDescentParser(lexer);
        BooleanExpression ast = parser.build();
        System.out.println(String.format("AST: %s", ast));
        System.out.println(String.format("RES: %s", ast.interpret()));
    }
}
