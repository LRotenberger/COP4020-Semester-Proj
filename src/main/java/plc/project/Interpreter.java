package plc.project;


import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);
        List<Environment.PlcObject> args = new ArrayList<>();
        return (scope.lookupFunction("main", 0)).invoke(args);
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if (ast.getValue().isPresent())
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        else
            scope.defineVariable(ast.getName(), Environment.NIL);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        Scope outerScope = new Scope(scope);
        scope.defineFunction(ast.getName(), ast.getParameters().size(), func -> {
            try {
                scope = new Scope(outerScope);
                for (int i = 0; i < ast.getParameters().size(); i++)
                {
                    scope.defineVariable(ast.getParameters().get(i), func.get(i));
                }
                ast.getStatements().forEach(this::visit);
            } catch (Return e) {
                return e.value;
            } finally {
                scope = outerScope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expr.Access))
            throw new RuntimeException("Expected Ast.Expr.Access");
        Ast.Expr.Access currentast = (Ast.Expr.Access)ast.getReceiver();
        if (currentast.getReceiver().isPresent()) {
            Environment.PlcObject receiver = visit(currentast.getReceiver().get());
            receiver.setField(currentast.getName(), visit(ast.getValue()));
        }
        else
        {
            scope.lookupVariable(currentast.getName()).setValue(visit(ast.getValue()));
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        boolean stmt = requireType(Boolean.class, visit(ast.getCondition()));
        if (stmt)
        {
            try {
                scope = new Scope(scope);
                ast.getThenStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }
        else {
            try {
                scope = new Scope(scope);
                ast.getElseStatements().forEach(this::visit);
            } finally {
                    scope = scope.getParent();
                }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
        Iterator iter = requireType(Iterable.class, visit(ast.getValue())).iterator();
        while (iter.hasNext())
        {
            try {
                scope = new Scope(scope);
                scope.defineVariable(ast.getName(), (Environment.PlcObject)(iter.next()));
                ast.getStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;


    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while ( requireType(Boolean.class, visit(ast.getCondition())) ) {
            try {
                scope = new Scope(scope);

                ast.getStatements().forEach(this::visit);

            } finally {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() == null)
            return Environment.NIL;
        else
            return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        if (ast.getOperator().equals("AND"))
        {
            if (requireType(Boolean.class, visit(ast.getLeft())) && requireType(Boolean.class, visit(ast.getRight())))
                return Environment.create(true);
            else
                return Environment.create(false);
        }
        else if (ast.getOperator().equals("OR"))
        {
            if (requireType(Boolean.class, visit(ast.getLeft())) || requireType(Boolean.class, visit(ast.getRight())))
                return Environment.create(true);
            else
                return Environment.create(false);
        }
        else if (ast.getOperator().equals("<"))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left instanceof Comparable && right instanceof Comparable)
            {
                requireType(left.getClass(), Environment.create(right));
                if (((Comparable) left).compareTo(right) < 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
        }
        else if (ast.getOperator().equals("<="))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left instanceof Comparable && right instanceof Comparable)
            {
                requireType(left.getClass(), Environment.create(right));
                if (((Comparable) left).compareTo(right) <= 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
        }
        else if (ast.getOperator().equals(">"))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left instanceof Comparable && right instanceof Comparable)
            {
                requireType(left.getClass(), Environment.create(right));
                if (((Comparable) left).compareTo(right) > 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
        }
        else if (ast.getOperator().equals(">="))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left instanceof Comparable && right instanceof Comparable)
            {
                requireType(left.getClass(), Environment.create(right));
                if (((Comparable) left).compareTo(right) >= 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
        }
        else if (ast.getOperator().equals("=="))
        {
            if (visit(ast.getLeft()).getValue().equals(visit(ast.getRight()).getValue()))
                return Environment.create(true);
            else
                return Environment.create(false);
        }
        else if (ast.getOperator().equals("!="))
        {
            if (visit(ast.getLeft()).getValue().equals(visit(ast.getRight()).getValue()))
                return Environment.create(false);
            else
                return Environment.create(true);
        }
        else if (ast.getOperator().equals("+"))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left.getClass().equals(String.class) || right.getClass().equals(String.class))
            {
                return Environment.create((String)left + (String)right);
            }
            if (left.getClass().equals(BigInteger.class))
            {
                requireType(BigInteger.class, visit(ast.getRight()));
                return Environment.create(((BigInteger)left).add((BigInteger)right));
            }
            if (left.getClass().equals(BigDecimal.class))
            {
                requireType(BigDecimal.class, visit(ast.getRight()));
                return Environment.create(((BigDecimal)left).add((BigDecimal) right));
            }
        }
        else if (ast.getOperator().equals("-"))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left.getClass().equals(BigInteger.class))
            {
                requireType(BigInteger.class, visit(ast.getRight()));
                return Environment.create(((BigInteger)left).subtract((BigInteger)right));
            }
            if (left.getClass().equals(BigDecimal.class))
            {
                requireType(BigDecimal.class, visit(ast.getRight()));
                return Environment.create(((BigDecimal)left).subtract((BigDecimal) right));
            }
        }
        else if (ast.getOperator().equals("*"))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left.getClass().equals(BigInteger.class))
            {
                requireType(BigInteger.class, visit(ast.getRight()));
                return Environment.create(((BigInteger)left).multiply((BigInteger)right));
            }
            if (left.getClass().equals(BigDecimal.class))
            {
                requireType(BigDecimal.class, visit(ast.getRight()));
                return Environment.create(((BigDecimal)left).multiply((BigDecimal) right));
            }
        }
        else if (ast.getOperator().equals("/"))
        {
            Object left = visit(ast.getLeft()).getValue();
            Object right = visit(ast.getRight()).getValue();
            if (left.getClass().equals(BigInteger.class))
            {
                requireType(BigInteger.class, visit(ast.getRight()));
                if ((BigInteger)right == BigInteger.ZERO)
                    throw new RuntimeException("Cannot divide by zero.");
                return Environment.create(((BigInteger)left).divide((BigInteger)right));
            }
            if (left.getClass().equals(BigDecimal.class))
            {
                requireType(BigDecimal.class, visit(ast.getRight()));
                if ((BigDecimal)right == BigDecimal.ZERO)
                    throw new RuntimeException("Cannot divide by zero.");
                BigDecimal quotient = ((BigDecimal)left).divide((BigDecimal) right, RoundingMode.HALF_EVEN);
                return Environment.create(quotient);
            }
        }
        throw new RuntimeException("Unknown Value");
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent())
        {
            return visit(ast.getReceiver().get()).getField(ast.getName()).getValue();
        }
        else
            return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        List<Environment.PlcObject> objargs= new ArrayList<>();
        for (Ast.Expr args : ast.getArguments())
        {
            objargs.add(visit(args));
        }
       if (ast.getReceiver().isPresent())
       {
            return visit(ast.getReceiver().get()).callMethod(ast.getName(), objargs);
       }
       else
           return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(objargs);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    public static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
