package plc.project;



import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();
        while (tokens.has(0)) {
            if (match("LET")) {
                if (methods.size() > 0)
                {
                    throw new ParseException("Expected Method", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                Ast.Field field = parseField();
                fields.add(field);
            }
            else if (match("DEF")) {
                Ast.Method method = parseMethod();
                methods.add(method);
            }
            else if (!peek("DEF") || !peek("LET"))
                throw new ParseException("Expected Method", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        return new Ast.Source(fields,methods);
        }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        if (!match(Token.Type.IDENTIFIER))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();
        String typename = "";
        if (!match(":", Token.Type.IDENTIFIER))
        {
            throw new ParseException("Missing type annotation", tokens.get(0).getIndex());
        }
        else
            {
            typename = this.tokens.get(-1).getLiteral();
            }
        if (match("="))
        {
            Ast.Expr expr = parseExpression();
            if (match(";"))
                return new Ast.Field(name, typename, Optional.of(expr));
            else
            {
                if (!tokens.has(0))
                    throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                else
                    throw new ParseException("Expected ';'", tokens.get(0).getIndex());
            }
        }
        else if (match(";"))
            return new Ast.Field(name, typename, Optional.empty());
        else
        {
            if (!tokens.has(0))
                throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected ';'", tokens.get(0).getIndex());
        }
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        if (!match(Token.Type.IDENTIFIER))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();
        if (!match("("))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected '('", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected '('", tokens.get(0).getIndex());
        }
        List<String> params = new ArrayList<>();
        List<String> typenameparams = new ArrayList<>();
        Optional<String> returntypename = Optional.empty();
        if (!peek(Token.Type.IDENTIFIER) && !peek(")"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier or ')'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected Identifier or ')'", tokens.get(0).getIndex());
        }
        if (match(Token.Type.IDENTIFIER)) {
            String param = tokens.get(-1).getLiteral();
            params.add(param);
            if (!match(":", Token.Type.IDENTIFIER))
            {
                throw new ParseException("Missing type annotation", tokens.get(0).getIndex());
            }
            else
            {
                typenameparams.add(tokens.get(-1).getLiteral());
                match(Token.Type.IDENTIFIER);
            }
            while (match(",") && !peek(")")) {
                if (!match(Token.Type.IDENTIFIER)) {
                    if (!tokens.has(0))
                        throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    else
                        throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
                }
                param = tokens.get(-1).getLiteral();
                params.add(param);
                typenameparams.add(tokens.get(-1).getLiteral());
                match(Token.Type.IDENTIFIER);
            }
        }
        if (!match(")"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected ')'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected ')'", tokens.get(0).getIndex());
        }
        if (match(":"))
        {
            returntypename = Optional.of(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
        }
        if (!match("DO"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected \"DO\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected \"DO\"", tokens.get(0).getIndex());
        }
        List<Ast.Stmt> stmts = new ArrayList<>();
        while (tokens.has(0))
        {
            if (match("END"))
                return new Ast.Method(name, params, typenameparams, returntypename, stmts);
            Ast.Stmt stmt = parseStatement();
            stmts.add(stmt);
        }
        throw new ParseException("Expected \"END\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        if(match("LET"))
            return parseDeclarationStatement();
        else if (match("IF"))
            return parseIfStatement();
        else if (match("FOR"))
            return parseForStatement();
        else if (match("WHILE"))
            return parseWhileStatement();
        else if (match("RETURN"))
            return parseReturnStatement();
        else if (tokens.has(0))
            return parseAssignmentStatement();
        else
            throw new ParseException("Expected Token", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
       if (!match(Token.Type.IDENTIFIER))
       {
           if (!tokens.has(0))
               throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
           else
               throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
       }
       String name = tokens.get(-1).getLiteral();
       Optional<String> typename = Optional.empty();
       if (match(":"))
        {
            if (!peek(Token.Type.IDENTIFIER))
            {
                if (!tokens.has(0))
                    throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                else
                    throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
            }
            typename = Optional.of(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
        }
       if (match("="))
       {
           Ast.Expr expr = parseExpression();
           if (match(";"))
               return new Ast.Stmt.Declaration(name, typename, Optional.of(expr));
           else
           {
               if (!tokens.has(0))
                   throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
               else
                   throw new ParseException("Expected ';'", tokens.get(0).getIndex());
           }
       }
       if (match(";"))
           return new Ast.Stmt.Declaration(name, typename, Optional.empty());
       else {
           if (!tokens.has(0))
               throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
           else
               throw new ParseException("Expected ';'", tokens.get(0).getIndex());
       }
    }

    public Ast.Stmt parseAssignmentStatement() throws ParseException {
        Ast.Expr expr1 = parseExpression();
        if (match("="))
        {
            Ast.Expr expr2 = parseExpression();
            if (match(";"))
                return new Ast.Stmt.Assignment(expr1, expr2);
            else
            {
                if (!tokens.has(0))
                    throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                else
                    throw new ParseException("Expected ';'", tokens.get(0).getIndex());
            }
        }
        if (match(";"))
            return new Ast.Stmt.Expression(expr1);
        else {
            if (!tokens.has(0))
                throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected ';'", tokens.get(0).getIndex());
        }
    }
    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        Ast.Expr expr = parseExpression();
        if (!match("DO"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected \"DO\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected \"DO\"", tokens.get(0).getIndex());
        }
        List<Ast.Stmt> stmts = new ArrayList<>();
        List<Ast.Stmt> elsestmts = new ArrayList<>();
        while (tokens.has(0)) {
            if (match("ELSE"))
            {
                while (tokens.has(0))
                {
                    if (match("END"))
                        return new Ast.Stmt.If(expr, stmts, elsestmts);
                    Ast.Stmt elsestmt = parseStatement();
                    elsestmts.add(elsestmt);
                }
            }
            if (match("END"))
                return new Ast.Stmt.If(expr, stmts, elsestmts);
            Ast.Stmt stmt = parseStatement();
            stmts.add(stmt);
        }
        throw new ParseException("Expected \"END\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        if (!match(Token.Type.IDENTIFIER))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();
        if (!match("IN"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected \"IN\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected \"IN\"", tokens.get(0).getIndex());
        }
        Ast.Expr expr = parseExpression();
        if (!match("DO"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected \"DO\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected \"DO\"", tokens.get(0).getIndex());
        }
        List<Ast.Stmt> stmts = new ArrayList<>();
        while (tokens.has(0)) {
            if (match("END"))
                return new Ast.Stmt.For(name, expr, stmts);
            Ast.Stmt stmt = parseStatement();
            stmts.add(stmt);
        }
        throw new ParseException("Expected \"END\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        Ast.Expr expr = parseExpression();
        if (!match("DO"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected \"DO\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected \"DO\"", tokens.get(0).getIndex());
        }
        List<Ast.Stmt> stmts = new ArrayList<>();
        while (tokens.has(0)) {
            if (match("END"))
                return new Ast.Stmt.While(expr, stmts);
            Ast.Stmt stmt = parseStatement();
            stmts.add(stmt);
        }
        throw new ParseException("Expected \"END\"", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        Ast.Expr expr = parseExpression();
        if (match(";"))
            return new Ast.Stmt.Return(expr);
        else
        {
            if (!tokens.has(0))
                throw new ParseException("Expected ';'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected ';'", tokens.get(0).getIndex());
        }

    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        Ast.Expr compare = parseEqualityExpression();
        while (match("OR") || match("AND"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseEqualityExpression();
            compare = new Ast.Expr.Binary(operator, compare, right);
        }
        return compare;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        Ast.Expr addi = parseAdditiveExpression();
        while (match(">") || match(">=") || match("<") || match("<=") || match("==") || match("!="))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseAdditiveExpression();
            addi = new Ast.Expr.Binary(operator, addi, right);
        }
        return addi;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        Ast.Expr multi = parseMultiplicativeExpression();
        while (match("+") || match("-"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseMultiplicativeExpression();
            multi = new Ast.Expr.Binary(operator, multi, right);
        }
        return multi;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        Ast.Expr second = parseSecondaryExpression();
        while (match("*") || match("/"))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseSecondaryExpression();
            second = new Ast.Expr.Binary(operator, second, right);
        }
        return second;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    //secondary_expression
    //         ::= primary_expression ( '.' identifier ( '(' ( expression ( ',' expression )* )? ')' )? )*
    public Ast.Expr parseSecondaryExpression() throws ParseException {
        Ast.Expr primary = parsePrimaryExpression();
        while (peek(".", Token.Type.IDENTIFIER)) {
            if (peek(".", Token.Type.IDENTIFIER, "(")) {
                primary = collectFunctionReceivers(primary);
            } else {
                primary = new Ast.Expr.Access(Optional.of(primary), tokens.get(1).getLiteral());
                match(".", Token.Type.IDENTIFIER);
            }
        }
        if (peek("."))
        {
            if (!tokens.has(0))
                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
        }
        return primary;
    }

    public Ast.Expr collectFunctionReceivers(Ast.Expr receiver) throws ParseException
    {
        while(peek(".", Token.Type.IDENTIFIER, "(")) {
            String name = tokens.get(1).getLiteral();
            List<Ast.Expr> args = new ArrayList<>();
            match(".", Token.Type.IDENTIFIER, "(");
            while (tokens.has(0)) {
                if (match(")")){
                    receiver = new Ast.Expr.Function(Optional.of(receiver), name, args);
                    break;
                }
                if (match(",")) {
                    if (!tokens.has(0))
                        throw new ParseException("Expecting Expression", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    if (match(")")) {
                        throw new ParseException("Missing Expression", tokens.get(-1).getIndex());
                    }
                }
                else {
                    Ast.Expr expr = parseExpression();
                    args.add(expr);
                }
            }
            if (!tokens.has(0) && tokens.get(-1).getLiteral() != ")")
                throw new ParseException("Expecting ')'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        return receiver;
    }
    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if (match("FALSE"))
            return new Ast.Expr.Literal(Boolean.FALSE);
        else if (match("TRUE"))
            return new Ast.Expr.Literal(Boolean.TRUE);
        else if (match("NIL"))
            return new Ast.Expr.Literal(null);
        else if (match(Token.Type.INTEGER))
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        else if (match(Token.Type.DECIMAL))
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        else if (match(Token.Type.STRING)) {
            String token = tokens.get(-1).getLiteral();
            token = token.replaceAll("\"","");
            token = token.replaceAll("\\\\b", "\b");
            token = token.replaceAll("\\\\n", "\n");
            token = token.replaceAll("\\\\r", "\r");
            token = token.replaceAll("\\\\t", "\t");
            token = token.replaceAll("\\\\'", "'");
            token = token.replaceAll("\\\\\"", "\"");
            token = token.replaceAll("\\\\", "\\");
            return new Ast.Expr.Literal(token);
        }
        else if (match(Token.Type.CHARACTER)) {
            String token = tokens.get(-1).getLiteral();
            token = token.replaceAll("\'", "");
            token = token.replaceAll("\\\\b", "\b");
            token = token.replaceAll("\\\\n", "\n");
            token = token.replaceAll("\\\\r", "\r");
            token = token.replaceAll("\\\\t", "\t");
            token = token.replaceAll("\\\\'", "'");
            token = token.replaceAll("\\\\\"", "\"");
            token = token.replaceAll("\\\\", "\\");
            return new Ast.Expr.Literal(token.charAt(0));
        }
        else if (match("("))
        {
            Ast.Expr expr = parseExpression();
            if (!match(")")) {
                if (!tokens.has(0))
                    throw new ParseException("Expecting ')'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                else
                    throw new ParseException("Expecting ')'", tokens.get(0).getIndex());
            }
            else
                return new Ast.Expr.Group(expr);
        }
        else if (match(Token.Type.IDENTIFIER))
        {
            String name = tokens.get(-1).getLiteral();
            if (match("("))
            {
                List<Ast.Expr> arguments = new ArrayList<>();
                if (!peek(")") && !peek(",")) {
                    Ast.Expr expr = parseExpression();
                    arguments.add(expr);

                    while (match(",") && !peek(")")) {
                        expr = parseExpression();
                        arguments.add(expr);
                    }
                }
                if (!match(")")) {
                    if (!tokens.has(0))
                        throw new ParseException("Expecting ')'", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    else
                        throw new ParseException("Expecting ')'", tokens.get(0).getIndex());
                }
                else
                    return new Ast.Expr.Function(Optional.empty(), name, arguments);
            }
            else
                return new Ast.Expr.Access(Optional.empty(),name);
        }
        else
        {
            if (!tokens.has(0))
                throw new ParseException("Invalid Primary Expression", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            else
                throw new ParseException("Invalid Primary Expression", tokens.get(0).getIndex());
        }

    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    public boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++)
        {
            if (!tokens.has(i))
                return false;
            else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType())
                    return false;
            }
            else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            }
            else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    public boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if (peek) {
            for (int i=0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
