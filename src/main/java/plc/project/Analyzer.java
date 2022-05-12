package plc.project;

import javax.print.DocFlavor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.Math;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);
        requireAssignable(Environment.Type.INTEGER,scope.lookupFunction("main",0).getReturnType());
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        Environment.Type fieldType = stringToType(ast.getTypeName());

        if(ast.getValue().isPresent()){
            visit(ast.getValue().get());
            requireAssignable(fieldType,ast.getValue().get().getType());
        }
        scope.defineVariable(ast.getName(), ast.getName(),fieldType,Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        //Missing coordination with Return
        List<Environment.Type> parameterTypes = new ArrayList<>();
        for(int i =0; i < ast.getParameterTypeNames().size(); i++){
            parameterTypes.add(stringToType(ast.getParameters().get(i)));
        }
        Environment.Type returnType = Environment.Type.NIL;
        if(ast.getReturnTypeName().isPresent()){
            returnType = Environment.getType(ast.getReturnTypeName().get());
        }

        scope.defineFunction(ast.getName(),ast.getName(),parameterTypes,returnType,args-> Environment.NIL);

        ast.setFunction(scope.lookupFunction(ast.getName(),ast.getParameters().size()));
        try {
            scope = new Scope(scope);
            ast.getStatements().forEach(this::visit);
        }
        finally{
            scope = scope.getParent();
        }
        if (returnType != Environment.Type.NIL)
        {
            for (int i = 0; i < ast.getStatements().size(); i++)
            {
                if (ast.getStatements().get(i) instanceof Ast.Stmt.Return)
                {
                    Ast.Stmt.Return returnStmt = (Ast.Stmt.Return)ast.getStatements().get(i);
                    requireAssignable(returnType, returnStmt.getValue().getType());
                }
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        if(!(ast.getExpression() instanceof Ast.Expr.Function)){
            throw new RuntimeException("Expected Ast.Expr.Function");
        }
        visit(ast.getExpression());
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        if(ast.getTypeName().isPresent()){
            Environment.Type variableType = stringToType(ast.getTypeName().get());
            if(ast.getValue().isPresent()){
                visit(ast.getValue().get());
                requireAssignable(variableType,ast.getValue().get().getType());
                scope.defineVariable(ast.getName(),ast.getName(),ast.getValue().get().getType(), Environment.NIL);
            }
            else{
                scope.defineVariable(ast.getName(),ast.getName(),variableType,Environment.NIL);
            }
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }
        else if(ast.getValue().isPresent()){
            visit(ast.getValue().get());
            scope.defineVariable(ast.getName(),ast.getName(),ast.getValue().get().getType(), Environment.NIL);
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }
        else{
            throw new RuntimeException("Value and TypeName not present for variable");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException("Expected Ast.Expr.Access");
        }
        visit(ast.getReceiver());
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(),ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        visit(ast.getCondition());
        if(ast.getThenStatements().size() != 0 && ast.getCondition().getType() == Environment.Type.BOOLEAN){
            try {
                scope = new Scope(scope);
                ast.getThenStatements().forEach(this::visit);
            }
            finally {
                scope = scope.getParent();
            }

            try {
                scope = new Scope(scope);
                ast.getElseStatements().forEach(this::visit);
            }
            finally {
                scope = scope.getParent();
            }

        }else{
            throw new RuntimeException("Invalid condition or empty then statements");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        visit(ast.getValue());
        if(ast.getValue().getType() != Environment.Type.INTEGER_ITERABLE){
            throw new RuntimeException("Not type IntegerIterable for statement");
        }
        else if(ast.getStatements().size() != 0){
            throw new RuntimeException("0 Statements");
        }
        else{
            try{
                scope = new Scope(scope);
                scope.defineVariable(ast.getName(),ast.getName(), Environment.Type.INTEGER,Environment.NIL);
                ast.getStatements().forEach(this::visit);
            }
            finally{
                scope =scope.getParent();
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN,ast.getCondition().getType());
        try{
            scope = new Scope(scope);
            ast.getStatements().forEach(this::visit);
        }
        finally{
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        //Coordination with Method missing
        visit(ast.getValue());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        //Maybe missing types and see BigDecimal
        if(ast.getLiteral() == null){
            ast.setType(Environment.Type.NIL);
        }
        else if(ast.getLiteral() instanceof java.lang.Boolean){
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getLiteral() instanceof java.lang.Character){
            ast.setType(Environment.Type.CHARACTER);
        }
        else if(ast.getLiteral() instanceof java.lang.String){
            ast.setType(Environment.Type.STRING);
        }
        else if(ast.getLiteral() instanceof BigInteger){
            if(((BigInteger) ast.getLiteral()).bitCount() > 32){
                throw new RuntimeException("Invalid Integer (Literal)");
            }
            ast.setType(Environment.Type.INTEGER);
        }
        else if(ast.getLiteral() instanceof BigDecimal){
            double limit = ((BigDecimal) ast.getLiteral()).doubleValue();
            if (Double.POSITIVE_INFINITY == Math.abs(limit))
                throw new RuntimeException("Invalid Double (Literal)");
            ast.setType(Environment.Type.DECIMAL);
        }
        else{
            throw new RuntimeException("Type of Literal Doesn't Exist");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        if(!(ast.getExpression() instanceof Ast.Expr.Binary)){
            throw new RuntimeException("Group expression not a binary expression");
        }
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());

        if(ast.getOperator().equals("AND") || ast.getOperator().equals("OR")){
            requireAssignable(Environment.Type.BOOLEAN,ast.getLeft().getType());
            requireAssignable(Environment.Type.BOOLEAN,ast.getRight().getType());
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getOperator().equals("<") ||
                ast.getOperator().equals("<=") ||
                ast.getOperator().equals(">") ||
                ast.getOperator().equals(">=") ||
                ast.getOperator().equals("==") ||
                ast.getOperator().equals("!=")){
            requireAssignable(Environment.Type.COMPARABLE,ast.getLeft().getType());
            requireAssignable(Environment.Type.COMPARABLE,ast.getRight().getType());
            if(ast.getLeft().getType() != ast.getRight().getType()){
                throw new RuntimeException("Binary incompatibility");
            }
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getOperator().equals("+") ||
                ast.getOperator().equals("-") ||
                ast.getOperator().equals("/") ||
                ast.getOperator().equals("*")){
            if(     ast.getOperator().equals("+") &&
                    ast.getRight().getType() == Environment.Type.STRING ||
                    ast.getLeft().getType() == Environment.Type.STRING){
                ast.setType(Environment.Type.STRING);
            }
            else{
                if(ast.getLeft().getType() == Environment.Type.INTEGER){
                    if(ast.getRight().getType() != Environment.Type.INTEGER){
                        throw new RuntimeException("Both sides not Integers");
                    }
                    ast.setType(Environment.Type.INTEGER);
                }
                else if(ast.getLeft().getType() == Environment.Type.DECIMAL){
                    if(ast.getRight().getType() != Environment.Type.DECIMAL){
                        throw new RuntimeException("Both sides not Decimals");
                    }
                    ast.setType(Environment.Type.DECIMAL);
                }
                else{
                    throw new RuntimeException("Invalid left and right type");
                }
            }
        }
        else {
            throw new RuntimeException("Unknown Binary Operation");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if(ast.getReceiver().isPresent()){
            visit(ast.getReceiver().get());
            ast.setVariable(ast.getReceiver().get().getType().getField(ast.getName()));
        }
        else{
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {

        for(int i = 0; i < ast.getArguments().size();i++){
            visit(ast.getArguments().get(i));
        }

        if(ast.getReceiver().isPresent()){
            visit(ast.getReceiver().get());
            Environment.Function func = ast.getReceiver().get().getType().getMethod(ast.getName(),ast.getArguments().size());
            for (int g = 0; g < ast.getArguments().size(); g++) {
                requireAssignable(func.getParameterTypes().get(g+1),ast.getArguments().get(g).getType());
            }
            ast.setFunction(func);
        }
        else{
            Environment.Function func = scope.lookupFunction(ast.getName(),ast.getArguments().size());
            for (int g = 0; g < ast.getArguments().size(); g++) {
                requireAssignable(func.getParameterTypes().get(g),ast.getArguments().get(g).getType());
            }
            ast.setFunction(func);
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if(target != type) {
            if (target != Environment.Type.ANY) {
                if(target == Environment.Type.COMPARABLE){
                    if(     type != Environment.Type.INTEGER &&
                            type != Environment.Type.DECIMAL &&
                            type != Environment.Type.CHARACTER &&
                            type != Environment.Type.STRING){
                        throw new RuntimeException("Invalid Type Comparison");
                    }
                }
                else{
                    throw new RuntimeException("Invalid Type Comparison");
                }
            }
        }
    }

    public static Environment.Type stringToType(String convert){
        switch (convert) {
            case "String":
                return Environment.Type.STRING;
            case "Character":
                return Environment.Type.CHARACTER;
            case "Boolean":
                return Environment.Type.BOOLEAN;
            case "Decimal":
                return Environment.Type.DECIMAL;
            case "Integer":
                return Environment.Type.INTEGER;
            case "Any":
                return Environment.Type.ANY;
            case "Comparable":
                return Environment.Type.COMPARABLE;
            default:
                throw new RuntimeException("stringToType Error");
        }
    }

}
