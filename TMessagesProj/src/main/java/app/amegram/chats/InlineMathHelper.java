package app.amegram.chats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InlineMathHelper {

    private static final Pattern MATH_PATTERN = Pattern.compile("([0-9+\\-*/^%().\\s]+)=\\s*$");

    private InlineMathHelper() {
    }

    public static String evaluateIfEquation(CharSequence text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        String str = text.toString().trim();
        Matcher matcher = MATH_PATTERN.matcher(str);
        if (!matcher.find()) {
            return null;
        }
        String expr = matcher.group(1).trim();
        if (expr.isEmpty() || !containsOperator(expr)) {
            return null;
        }
        try {
            double res = eval(expr);
            if (Double.isNaN(res) || Double.isInfinite(res)) {
                return null;
            }
            BigDecimal bd = BigDecimal.valueOf(res).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
            return bd.toPlainString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean containsOperator(String expr) {
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%') {
                return true;
            }
        }
        return false;
    }

    private static double eval(String str) {
        List<String> tokens = tokenize(str);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException();
        }
        List<String> rpn = toRpn(tokens);
        return evaluateRpn(rpn);
    }

    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        boolean expectUnary = true;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                num.append(c);
                expectUnary = false;
            } else {
                if (num.length() > 0) {
                    tokens.add(num.toString());
                    num.setLength(0);
                }
                if (c == '-' && expectUnary) {
                    num.append('-');
                    expectUnary = false;
                } else if (c == '(') {
                    tokens.add("(");
                    expectUnary = true;
                } else if (c == ')') {
                    tokens.add(")");
                    expectUnary = false;
                } else if (isOp(c)) {
                    tokens.add(String.valueOf(c));
                    expectUnary = true;
                } else {
                    throw new IllegalArgumentException("Unknown character: " + c);
                }
            }
        }
        if (num.length() > 0) {
            tokens.add(num.toString());
        }
        return tokens;
    }

    private static boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%';
    }

    private static int precedence(String op) {
        switch (op) {
            case "+":
            case "-":
                return 1;
            case "*":
            case "/":
            case "%":
                return 2;
            case "^":
                return 3;
            default:
                return 0;
        }
    }

    private static List<String> toRpn(List<String> tokens) {
        List<String> out = new ArrayList<>();
        Stack<String> stack = new Stack<>();

        for (String t : tokens) {
            if ("(".equals(t)) {
                stack.push(t);
            } else if (")".equals(t)) {
                while (!stack.isEmpty() && !"(".equals(stack.peek())) {
                    out.add(stack.pop());
                }
                if (!stack.isEmpty() && "(".equals(stack.peek())) {
                    stack.pop();
                }
            } else if (t.length() == 1 && isOp(t.charAt(0))) {
                while (!stack.isEmpty() && precedence(stack.peek()) >= precedence(t)) {
                    out.add(stack.pop());
                }
                stack.push(t);
            } else {
                out.add(t);
            }
        }
        while (!stack.isEmpty()) {
            out.add(stack.pop());
        }
        return out;
    }

    private static double evaluateRpn(List<String> rpn) {
        Stack<Double> stack = new Stack<>();
        for (String token : rpn) {
            if (token.length() == 1 && isOp(token.charAt(0)) && stack.size() >= 2) {
                double b = stack.pop();
                double a = stack.pop();
                switch (token.charAt(0)) {
                    case '+': stack.push(a + b); break;
                    case '-': stack.push(a - b); break;
                    case '*': stack.push(a * b); break;
                    case '/':
                        if (b == 0) throw new ArithmeticException("Division by zero");
                        stack.push(a / b);
                        break;
                    case '^': stack.push(Math.pow(a, b)); break;
                    case '%': stack.push(a % b); break;
                }
            } else {
                stack.push(Double.parseDouble(token));
            }
        }
        if (stack.size() != 1) {
            throw new IllegalArgumentException();
        }
        return stack.pop();
    }
}
