package grep_Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class G1{
    public static void main(String[] args){

        //make sure there is a string in command line args
        if (args.length != 1){
            System.err.println("Wrong number cmd line args");  
            System.exit(1);
        }
        try{
            //generate tokenizer/parser objects
            G1TokenManager tm = new G1TokenManager(args[0], true);
            G1Parser p = new G1Parser(tm, true);

            //parse regular expresison string
            p.parse();
            System.out.println("Expression Image List: " + Arrays.toString(tm.Generated_List.toArray()) + "\n");
        }catch(RuntimeException e){
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}

class G1TokenManager implements G1Constants{
    public List<String> Generated_List = new ArrayList<>(); //This isn't really necessary, however it is great for visualizing the token types generated after parsing.
	private boolean debug;
    private boolean at_end = false; //very important for detecting end of expression. Cannot rely on EOF or any characters for this context.
	private char currentChar;
	private int currentColumnNumber;
	private String inputLine; 
	private Token token; 

	// -----------------------------------------

	public G1TokenManager(String expression, boolean debug) {
		this.inputLine = expression;
        this.debug = debug;
		currentChar = expression.charAt(0);
	}

    // ---------------------------------------

	public Token getNextToken() {
		// skip whitespace
		while (Character.isWhitespace(currentChar))
			getNextChar();

        
		// construct token to be returned to parser
		token = new Token();
		token.next = null;
		token.beginColumn = currentColumnNumber;

        if(debug) System.out.println("PARSING "+ currentChar + " IN getNextToken");
        
        //look ahead to see if we are at the end of the expression
		if(currentColumnNumber >= inputLine.length() || at_end == true){
			token.image = "<EORE>";
			token.endColumn = currentColumnNumber;
			token.kind = EORE;
            Generated_List.add("<EORE>");
        }

        //Check for period. Used to represent the wildcard character
		else if (currentChar == '.') {
            token.image =  "\".\"";
            token.endColumn = currentColumnNumber;
            token.kind = PERIOD;
            Generated_List.add(".");
		}

		//Check for LeftParen used in enclosures.
		else if (currentChar == '(') {
            token.image =  "\"(\"";
            token.endColumn = currentColumnNumber;
            token.kind = LEFTPAREN;
            Generated_List.add("(");
		}

        //Check for RightParen used in enclosures.
		else if (currentChar == ')') {
            token.image =  "\")\"";
            token.endColumn = currentColumnNumber;
            token.kind = RIGHTPAREN;
            Generated_List.add(")");
		}

        //Check for OR character
		else if (currentChar == '|') {
            token.image =  "\"|\"";
            token.endColumn = currentColumnNumber;
            token.kind = OR;
            Generated_List.add("|");
		}

        //Check for star operator. Used to indicated zero or more of a character or sequence. 
		else if (currentChar == '*') {
            token.image =  "\"*\"";
            token.endColumn = currentColumnNumber;
            token.kind = STAR;
            Generated_List.add("*");
		}

        //Check for backslash. Used to explictly declare next character in expression as a unique char token.
        else if (currentChar == '\\'){
            Generated_List.add("\\");
            getNextChar();
            Generated_List.add("<CHAR>");
            token.image = "<CHAR>";
            token.endColumn = currentColumnNumber-1;
            token.kind = CHAR;
            currentColumnNumber -= 1;
            getNextChar();
        }

        //Check for character. Can be any ascii character as long as it is defined.
		else if (Character.isDefined(currentChar)) {
            if(debug) System.out.println(currentChar);
            token.image = "<CHAR>";
            token.endColumn = currentColumnNumber;
            token.kind = CHAR;
            Generated_List.add("<CHAR>");
		}

        //If none of these checks qualify, build error token,
        else{
            token.image =  "<ERROR>";
            token.endColumn = currentColumnNumber;
            token.kind = ERROR;
        }

        //lookahead to see if we are at the end of the expression
        if(currentColumnNumber + 1 >= inputLine.length()){
            at_end = true;
        }

        //move forward in expression. 
        getNextChar();

		//token trace 
		if (debug)
			System.out.printf(
					"kind=%3d col=%3d image=%s%n",
					token.kind, currentColumnNumber, token.image);

        //return token to parser
		return token; 
	}

	// -----------------------------------------
	private void getNextChar() {
        //if we are not at the end of the expression, increment index. Otherwise return end of regular expression image.
		if(!at_end){
            currentColumnNumber += 1;
            currentChar = inputLine.charAt(currentColumnNumber);
        }else{
            currentChar = EORE;
        }
	}
}

class G1Parser  implements G1Constants{
    private boolean debug;
    private G1TokenManager tm;
    private Token currentToken;
    private Token previousToken; // This will be important in the future maybe

    //-----------------------------------------
    public G1Parser(G1TokenManager tm, boolean debug){
      this.tm = tm;
      this.debug = debug;
      currentToken = tm.getNextToken(); 
      previousToken = null;
    }
    
    //-----------------------------------------
    //Custom runtime exception
    private RuntimeException genEx(String errorMessage)
    {
      return new RuntimeException("Encountered \"" + 
        currentToken.image + "\" on line " + 
        currentToken.beginLine + ", column " + 
        currentToken.beginColumn + "." +
        System.getProperty("line.separator") + 
        errorMessage);
    }

    //-----------------------------------------
    //Advance currentToken to next token.
    private void advance(){
      previousToken = currentToken; 
      if (currentToken.next != null)
        currentToken = currentToken.next;
      else
        currentToken = 
                    currentToken.next = tm.getNextToken();
    }

    //-----------------------------------------
    //Advance currentToken to next token if token matches argument.
    private void consume(int expected)
    {
      if (currentToken.kind == expected)
        advance();
      else
        throw genEx("Expecting " + tokenImage[expected]);
    }

    //-----------------------------------------
    /*
    *   START OF RECURSIVE DESENT PARSER
    *   Here is the grammar for our qualified regular expression 
    *   Thank you Anthony J. Dos Reis :)
    *   
    *              Productions                                    predict sets
    *   
    *   program       -->     statementList                 {<CHAR>, <PERIOD>, "("}
    *   statementList -->     expr                          {<CHAR>, <PERIOD>, "("}
    *   expr          -->     term termList                 {<CHAR>, <PERIOD>, "("}
    *   termList      -->     "|" term termList             {"|"}
    *   termList      -->     lambda                        {<EORE>}
    *   term          -->     factor factorList             {<CHAR>, <PERIOD>, "("}
    *   factorList    -->     factor factorList             {<CHAR>, <PERIOD>, "("}
    *   factorList    -->     lambda                        {"|", <EORE>}
    *   factor        -->     <CHAR> factorTail             {<CHAR>}
    *   factor        -->     <PERIOD> factorTail           {<PERIOD>}
    *   factor        -->     "(" expr ")" factorTail       {"("}
    *   factorTail    -->     "*" factorTail                {"*"}
    *   factorTail    -->     lambda                        {<CHAR>, <PERIOD>, "(", <EORE>}
    *
    *   This could code be cleaned up but whatever... sue me! I don't want to break anything.
    */

    //-----------------------------------------
    //Driver function for parser.
    public void parse()
    {
        if (debug) System.out.println("PARSING");
        program();
    }

    //-----------------------------------------
    private void program(){
        System.out.println("\n\nSTARTING PARSE");
        if (debug) System.out.println("IN PROGRAM...");
        statementList();
        if (currentToken.kind != EORE)  //garbage at end?
            throw genEx("Expecting <EORE>");
        System.out.println("PARSING SUCCESS!");
    }

    //-----------------------------------------
    private void statementList(){
        if (debug) System.out.println("IN STATEMENTLIST");
        switch(currentToken.kind){
            case CHAR:
                if (debug) System.out.println("PARSING CHAR IN STATEMENTLIST");
                expr();
                break;
            case PERIOD:
                if (debug) System.out.println("PARSING PERIOD IN STATEMENTLIST");
                expr();
                break;
            case LEFTPAREN:
                if (debug) System.out.println("PARSING LEFTPAREN IN STATEMENTLIST");
                expr();
                break;
            default:
                throw genEx("Illegal start. Expecting <CHAR>, \"(\", or <PERIOD>");
        }
    }

    //-----------------------------------------
    private void expr(){
        if (debug) System.out.println("IN EXPRESSION...");
        term();
        termList();
    }

    //-----------------------------------------
    private void termList(){
        if (debug) System.out.println("IN TERMLIST...");
        switch(currentToken.kind){
        case OR:
            if (debug) System.out.println("PARSING OR");
            consume(OR);
            term();
            termList();
            break;
        case EORE:
            if (debug) System.out.println("PARSING EORE IN TERMLIST");
            ;
            break;
        default:
            throw genEx("Expecting \"+\", \")\", or \";\"");
      }
    }

    //-----------------------------------------
    private void term(){
        if (debug) System.out.println("IN TERM...");
        switch(currentToken.kind){
            case CHAR:
                if (debug) System.out.println("PARSING CHAR IN TERM");
                factor();
                factorList();
                break;
            case PERIOD:
                if (debug) System.out.println("PARSING PERIOD IN TERM");
                factor();
                factorList();
                break;
            case LEFTPAREN:
                if (debug) System.out.println("PARSING LEFTPAREN IN TERM");
                factor();
                factorList();
                break;
            default:
                throw genEx("Illegal start. Expecting <CHAR>, \"(\", or <PERIOD>");
        }
    }

    //-----------------------------------------
    private void factorList(){
        if (debug) System.out.println("IN FACTORLIST...");
        switch(currentToken.kind){
        case CHAR:
            if (debug) System.out.println("PARSING CHAR IN FACTORLIST");
            factor();
            factorList();
            break;
        case PERIOD:
            if (debug) System.out.println("PARSING PERIOD IN FACTORLIST");
            factor();
            factorList();
            break;
        case LEFTPAREN:
            if (debug) System.out.println("PARSING LEFTPAREN IN FACTORLIST");
            factor();
            factorList();
            break;
        case OR:
            if (debug) System.out.println("PARSING OR IN FACTORLIST");
            ;
            break;
        case EORE:
            if (debug)System.out.println("PARSING EORE IN FACTORLIST");
            ;
            break;
        default:
            throw genEx("Expecting op, \")\", or \";\"");
        }
    }

    //-----------------------------------------
    private void factor()
    {  
        if (debug) System.out.println("IN FACTOR");
        switch(currentToken.kind){
        case CHAR:
            if (debug) System.out.println("PARSING CHAR IN FACTOR");
            consume(CHAR);
            factorTail();
            break;
        case PERIOD:
            if (debug) System.out.println("PARSING PERIOD IN FACTOR");
            consume(PERIOD);
            factorTail();
            break;
        case LEFTPAREN:
            if (debug) System.out.println("PARSING LEFT RIGHT PAREN IN FACTOR");
            consume(LEFTPAREN);
            expr();
            consume(RIGHTPAREN);
            factorTail();
            break;
        case EORE:
            if (debug) System.out.println("PARSING EORE IN FACTOR");
            ;
            break;
        default:
            throw genEx("Expecting factor");
        }
    }

    //-----------------------------------------
    private void factorTail(){
        if (debug) System.out.println("IN FACTOR TAIL...");
        switch(currentToken.kind){
            case STAR:
                if (debug) System.out.println("PARSING STAR IN FACTORTAIL");
                consume(STAR);
                factorTail();
                break;
            case CHAR:
                if (debug) System.out.println("PARSING CHAR IN FACTOR TAIL");
                ;
                break;
            case PERIOD:
                if (debug) System.out.println("PARSING PERIOD IN FACTOR TAIL");
                ;
                break;
            case LEFTPAREN:
                if (debug) System.out.println("PARSING ( IN FACTOR TAIL");
                ;
                break;
            case OR:
                if (debug) System.out.println("PARSING | IN FACTOR TAIL");
                ;
                break;
            case EORE:
                if (debug) System.out.println("PARSING EORE IN FACTOR TAIL");
                ;
                break;
            default:
                throw genEx("Expecting factor tail");
        }
    }
}

//This token class was generated through JavaCC. Also provided by Anthony J. Dos Reis :3
class Token {
    public int kind;
    public int beginLine;
    public int beginColumn;
    public int endLine;
    public int endColumn;
    public String image;
    public Token next;
    public Token specialToken;

    //-----------------------------------------
    public Object getValue() {
      return null;
    }
  
    //-----------------------------------------
    public Token() {}

    //-----------------------------------------
    public Token(int kind){
       this(kind, null);
    }
  
    //-----------------------------------------
    public Token(int kind, String image){
       this.kind = kind;
       this.image = image;
    }

    //-----------------------------------------
    public String toString(){
       return image;
    }

    //-----------------------------------------
    public static Token newToken(int ofKind, String image){
       switch(ofKind)
       {
         default : return new Token(ofKind, image);
       }
    }
  
    //-----------------------------------------
    public static Token newToken(int ofKind){
       return newToken(ofKind, null);
    }
  }  


  //This is the interface used in enforcing the structure of inputs that could occur in our regular expressions.
  //Inmcluded an indexed mapping for each token type as well.
  interface G1Constants{
    int EORE = 0;
    int CHAR = 1;
    int PERIOD = 2;
    int LEFTPAREN = 3;
    int RIGHTPAREN = 4;
    int OR = 5;
    int STAR = 6;
    int ERROR = 7;
    int CONCAT = 8;

    String[] tokenImage = {
        "<EORE>",
        "<CHAR>",
        "\".\"",
        "\"(\"",
        "\")\"",
        "\"|\"",
        "\"*\"",
        "<ERROR>"
    };
}


/*Ignore this... this is an attempted refactor for the tokenizer steps
switch(currentChar){
            case '.':
                Single_Char = true;
                token.image =  "\".\"";
                token.endColumn = currentColumnNumber;
                token.kind = PERIOD;
                Generated_List.add(".");
                validated = true;
            case '(':
                Single_Char = true;
                token.image =  "\"(\"";
                token.endColumn = currentColumnNumber;
                token.kind = LEFTPAREN;
                Generated_List.add("(");
                validated = true;
            case ')':
                Single_Char = true;
                token.image =  "\")\"";
                token.endColumn = currentColumnNumber;
                token.kind = RIGHTPAREN;
                Generated_List.add(")");
                validated = true;
            case '|':
                Single_Char = true;
                token.image =  "\"|\"";
                token.endColumn = currentColumnNumber;
                token.kind = OR;
                Generated_List.add("|");
                validated = true;
            case '*':
                Single_Char = true;
                token.image =  "\"*\"";
                token.endColumn = currentColumnNumber;
                token.kind = STAR;
                Generated_List.add("*");
                validated = true;
            case '\\':
                Generated_List.add("\\");
                getNextChar();
                Generated_List.add("<CHAR>");
                Single_Char = true;
                token.image = "<CHAR>";
                token.endColumn = currentColumnNumber-1;
                token.kind = CHAR;
                currentColumnNumber -= 1;
                validated = true;
        }
        
		if(currentColumnNumber >= inputLine.length() || at_end == true){
            Single_Char = false;
			token.image = "<EORE>";
			token.endColumn = currentColumnNumber;
			token.kind = EORE;
            Generated_List.add("<EORE>");
            validated = true;
        }

        //<CHAR>
		else if (Character.isLetterOrDigit(currentChar)) {
            if(debug) System.out.println(currentChar);
            Single_Char = true;
            token.image = "<CHAR>";
            token.endColumn = currentColumnNumber;
            token.kind = CHAR;
            Generated_List.add("<CHAR>");
            validated = true;
		}

        if(!validated){
            Single_Char = false;
            token.image =  "<ERROR>";
            token.endColumn = currentColumnNumber;
            token.kind = ERROR;
        }

        if(currentColumnNumber + 1 >= inputLine.length()){
            at_end = true;
        }
 */
