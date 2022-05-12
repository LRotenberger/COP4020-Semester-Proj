package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(0);
        for (int i = 0; i < ast.getFields().size(); i++)
        {
            if (i == 0)
                newline(++indent);
            else
                newline(indent);
            print(ast.getFields().get(i));
            if (i == ast.getFields().size()-1)
                newline(--indent);
        }

        newline(++indent);
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");
        newline(--indent);
        for(int i = 0; i < ast.getMethods().size(); i++)
        {
            newline(++indent);
            print(ast.getMethods().get(i));
            newline(--indent);
        }
        newline(0);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());
        if (ast.getValue().isPresent())
        {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");
        for (int i = 0; i <ast.getParameterTypeNames().size(); i++)
        {
            print(Environment.getType(ast.getParameterTypeNames().get(i)).getJvmName(), " ", ast.getParameters().get(i));
            if (i != ast.getParameterTypeNames().size()-1)
                print(", ");
        }
        print(") {");
        if(!ast.getStatements().isEmpty())
        {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++)
            {
                if(i != 0)
                    newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getName());
        if (ast.getValue().isPresent())
        {
            print(" = ", ast.getValue().get());
        }
        print (";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (", ast.getCondition(), ") {");
        newline(++indent);
        for (int i = 0; i < ast.getThenStatements().size(); i++)
        {
            if(i != 0)
                newline(indent);
            print(ast.getThenStatements().get(i));
        }
        newline(--indent);
        print("}");
        if (!ast.getElseStatements().isEmpty())
        {
            print(" else {");
            newline(++indent);
            for (int i = 0; i < ast.getElseStatements().size(); i++)
            {
                if(i != 0)
                    newline(indent);
                print(ast.getElseStatements().get(i));
            }
            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (int ", ast.getName(), " : ", ast.getValue(), ") {");
        if (!ast.getStatements().isEmpty())
        {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        print("while (", ast.getCondition(), ") {");
        if (!ast.getStatements().isEmpty())
        {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() == null)
        {
            print("null");
        }
        else if (ast.getLiteral() instanceof Character)
        {
            print("'");
            print(ast.getLiteral());
            print("'");
        }
        else if (ast.getLiteral() instanceof String)
        {
            print("\"");
            print(ast.getLiteral());
            print("\"");
        }
        else
            print(ast.getLiteral());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(",ast.getExpression(),")");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        print(ast.getLeft());
        switch (ast.getOperator())
        {
            case "AND":
                print(" && ");
                break;
            case "OR":
                print(" || ");
                break;
            default:
                print(" ", ast.getOperator(), " ");
        }
        print(ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent())
            print(ast.getReceiver().get(), ".");
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if (ast.getReceiver().isPresent())
            print(ast.getReceiver().get(), ".");
        print(ast.getFunction().getJvmName(), "(");
        for (int i = 0; i < ast.getArguments().size(); i++)
        {
            if (i != ast.getArguments().size()-1)
                print(ast.getArguments().get(i), ", ");
            else
                print(ast.getArguments().get(i));
        }
        print(")");
        return null;
    }

}
