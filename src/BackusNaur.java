import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Extended Backus-Naur Form Implementation
 * 
 *    |   - OR operator: Either the LHS or the RHS occurs
 * 
 * Grouping Expressions:
 *    {}  - Enclosed expression can occur 0 or more times
 *    {}* - Enclosed expression can occur 0 or more times
 *    {}+ - Enclosed expression can occur 1 or more times
 *    {}? - Enclosed expression can occur 0 or 1 times
 *    []  - Enclosed expression can occur 0 or 1 times
 * 
 * Quantifiers:
 *    * - The preceding item can occur 0 or more times
 *    + - The preceding item can occur 1 or more times
 *    ? - The preceding item can occur 0 or 1 times
 */

public class BackusNaur {
  
  static void db(Object o) { if (true) System.err.println(o); }
  
  //Where all the definitions are stored - HashMap<symbol, expression>
  public TreeMap<String, BranchExpr> defs = new TreeMap<String, BranchExpr>();
  
  /****************** CONSTRUCTION ******************/
  
  static String fileToString(File f) throws Exception {
    StringBuilder sb = new StringBuilder();
    String line;
    try {
      BufferedReader in = new BufferedReader(new FileReader(f));
      while ((line = in.readLine()) != null) sb.append(line + "\n");
    } catch (Exception e) {
      throw new Exception("Error loading Backus-Naur definition file." +
                          "\tCannot load file: " + f.getName());
    }
    return sb.toString();
  }
  
  //constructor from file
  public BackusNaur(File f) throws Exception {
    this(fileToString(f));
  }
  
  //constructor from String
  public BackusNaur(String s) throws Exception {
    BufferedReader in;
    in = new BufferedReader(new StringReader(s));
    int line_num = 0;
    String line;
    Vector<String> tokens = new Vector<String>();
    
    while ((line = in.readLine()) != null) {
      line_num++;
      if ((line = line.trim()).isEmpty()) continue;
      
      Vector<String> lineTokens = getTokens(line);
      //db(lineTokens);
      
      if (lineTokens.size() > 1 && lineTokens.get(1).equals("::=")) {
        if (!lineTokens.isEmpty()) {
          try {
            parseDefinition(lineTokens);
          } catch (Exception e) {
            throw new Exception("Line " + line_num + ": " + e.getMessage());
          }
        }
        tokens = lineTokens;
      } else {
        tokens.addAll(lineTokens);
      }
    }
    if (defs.isEmpty())
      throw new Exception("Error: No definitions were recognized.");
    this.validate();
  }
  
  //Preprocesses a Backus-Naur, also splitting it into tokens
  private static Vector<String> getTokens(String s) {
    //Surround '{', '}', '[', ']', characters with spaces
    //Convert [] to {}?, which is the same representation
    s = s.replace("{", " { ").replace("}", " } ");
    s = s.replace("[", " { ").replace("]", " } ? ");
    //replace | signs not in quotes
    s = s.replaceAll("[^\\w\"<>']+\\|[^\\w\"<>']+", " | ");
    
    Vector<String> tokens = new Vector<String>();
    
    boolean quoteOpened = false;
    String curr = "";
    for (int i = 0; i < s.length(); i++) {
      if ((!quoteOpened && s.charAt(i) == ' ') ||
          (!quoteOpened && s.charAt(i) == '"')) {
        if (curr.length() > 0) tokens.add(curr);
        curr = "";
        if (s.charAt(i) == '"') {
          quoteOpened = true;
        }
      } else {
        if (s.charAt(i) == '"') {
          if (quoteOpened) {
            quoteOpened = false;
            tokens.add(curr); //can be zero length
            curr = "";
          } else {
            if (curr.length() > 0) tokens.add(curr);
            curr = "";
            quoteOpened = true;
          }
        } else {
          curr += s.charAt(i);
        }
      }
    }
    if (curr.length() > 0) tokens.add(curr);
    return tokens;
  }
  
  //parses the Vector of tokens and appends this to the list of tokens
  private void parseDefinition(Vector<String> tokens) throws Exception {   
    //check to make sure the LHS of the line is in angled brackets
    if (!validVariable(tokens.get(0)))
      throw new Exception("1st token on each line must be enclosed in angle brackets.");
    if (!tokens.get(1).equals("::="))
      throw new Exception("2nd token on each line must be \"::=\".");
    //take off the angle brackets
    String symbol = tokens.get(0).substring(1, tokens.get(0).length() - 1);
    if (defs.containsKey(symbol))
      throw new Exception("Symbol <" + symbol + "> already declared.");
    
    //Current do not support recursive definitions
    //or quantifiers as in Extended Backus-Naur form
    for (int i = 2; i < tokens.size(); i++) {
      if (tokens.get(i).equals(tokens.get(0)))
        throw new Exception("Recursive definitions are currently unsupported.");
      if (tokens.get(i).equals("{") || tokens.get(i).equals("}") ||
          tokens.get(i).equals("{") || tokens.get(i).equals("}"))
        throw new Exception("Brace {} quantifiers are currently unsupported.");
    }
    if (tokens.size() < 2)
      throw new Exception("Too few tokens on the line!");
    defs.put(symbol, parseBranchExpr(tokens, 2, tokens.size()));
  }
  
  /**
   * @params: tokens, and range of tokens [lo, hi) to consider
   */
  private BranchExpr parseBranchExpr(Vector<String> tokens,
                                     int lo, int hi) throws Exception {
    if (lo >= hi) return null; // ???
    //db(lo + " " + hi + ": " + tokens.subList(lo, hi));
    
    //find the last index of the OR symbol, before index hi
    //this is because OR operators works like ((A | B) | C), not (A | (B | C))
    int idx = tokens.lastIndexOf("|", hi - 1);
    if (idx == -1 || idx < lo) { //no OR signs in the range
      BranchExpr be = new BranchExpr(false);
     
      int curr = lo;
      
      while (curr < hi) {
        if (tokens.get(curr).equals("{")) { //does an open curly bracket exist?
          //find right bracket
          int lidx = curr, ridx = tokens.indexOf("}", lidx);
          
          //check for existence and in range
          if (ridx >= hi || ridx == -1)
            throw new Exception("Mismatched brace quantifier {}.");
          
          //currently no support for multiple level brackets, e.g. {a{b}}
          if (tokens.indexOf("{", lidx + 1) != -1 &&
              tokens.indexOf("{", lidx + 1) < ridx) {
            throw new Exception("Currently only 1 level of brace quantifiers {} are supported.");
          }
          
          ConcatExpr ce = parseConcatExpr(tokens, lidx + 1, ridx);
          if (ridx < tokens.size()) {
            if (tokens.get(ridx).equals("*")) ce.quantifier = '*';
            else if (tokens.get(ridx).equals("+")) ce.quantifier = '+';
            else if (tokens.get(ridx).equals("?")) ce.quantifier = '?';
            else {
              ce.quantifier = '*';
              ridx--;
            }
            ridx++;
          }
          be.add(ce);
          
          curr = ridx + 1; //skip the current position to after the close brace
          continue; //keep parsing!!!!!
        }
        //just parse a normal expression, up to the next open brace { in range
        int ridx = tokens.indexOf("{", curr + 1);
        if (ridx == -1 || ridx > hi) ridx = hi;
        be.add(parseConcatExpr(tokens, curr, ridx));
        curr = ridx; //move on to the next ConcatExpr to be parsed
      }
      
      return be;
    }
    BranchExpr be = new BranchExpr(true,
                                   parseBranchExpr(tokens, lo, idx),
                                   parseBranchExpr(tokens, idx + 1, hi));
    return be;
  }
  
  /**
   * @params: tokens, and range of tokens [lo, hi) to consider
   */
  private ConcatExpr parseConcatExpr(Vector<String> tokens,
                                     int lo, int hi) {
    //db("concat: " + tokens.subList(lo, hi));
  
    ConcatExpr expr = new ConcatExpr();
    for (int i = lo; i < hi; i++) {
      String v = tokens.get(i);
      if (v.length() >= 2 && v.charAt(0) == '<' && v.charAt(v.length() - 1) == '>') {
        expr.addItem(v.substring(1, v.length() - 1), false);
      } else {
        expr.addItem(v, true); //string literal
      }
    }
    return expr;
  }
  
  private static boolean validVariable(String s) {
    return (s.charAt(0) == '<') &&
           (s.charAt(s.length() - 1) == '>');
  }
  
  /******************* VALIDATION *******************/
  
  private HashSet<String> invalidSymbols = new HashSet<String>();
  
  private boolean validate(ConcatExpr ce) {
    boolean valid = true;
    for (int i = 0; i < ce.items.size(); i++) {
      if (ce.items.get(i).isLiteral) continue;
      //make sure the symbol is already defined
      if (!defs.containsKey(ce.items.get(i).value)) {
        valid = false;
        invalidSymbols.add(ce.items.get(i).value);
      }
    }
    return valid;
  }
  
  private boolean validate(BranchExpr be) {
    if (be.hasRHS)
      return validate(be.lhs) && validate(be.rhs);
    boolean valid = true;
    for (int i = 0; i < be.expr.size(); i++) {
      if (!validate(be.expr.get(i))) valid = false;
    }
    return valid;
  }
  
  //validate the Backus-Naur form to make sure everything is defined.
  private void validate() throws Exception {
    boolean valid = true;
    //loops through all definitions
    for (BranchExpr be : defs.values()) {
      //recurse the tree to ensure that all "symbols" are defined
      if (!validate(be)) valid = false;
    }
    if (!valid) 
      throw new Exception("Undefined symbol(s): " + invalidSymbols + "\n" +
                          "Maybe you should load some dictionaries?");
  }
  
  /****************** (Public) MATCHING INPUTS *******************/
  
  private HashMap<String, TreeSet<String>> results; //temporary store results to be displayed
  
  private static String vectorToStr(Vector<String> v, int lo, int hi) {
    if (lo < 0 || hi > v.size()) return "";
    String res = "[ ";
    for (int i = lo; i < hi; i++) res += v.get(i) + " ";
    return res + "]";
  }
  
  private static final int MAX_DEPTH = 50;
  private boolean recursedTooDeep;
  
  //returns 1 more than the index of the location up to where matched
  private int match(ConcatExpr ce, Vector<String> tokens, int lo, int depth) {
    int id = lo;
    //db("CE " + lo + " " + depth);
    for (int i = 0; i < ce.items.size(); i++) {
      if (id >= tokens.size()) return -1;
      if (ce.items.get(i).isLiteral) {
        if (!tokens.get(id).toLowerCase().
              equals(ce.items.get(i).value.toLowerCase())) {
          return -1;
        }
        id++; //move to next token
      } else {
        //try to match the symbol
        int prev = id;
        id = match(defs.get(ce.items.get(i).value), tokens, id, depth + 1);
        if (id < 0) return id;
        if (!results.containsKey(ce.items.get(i).value))
          results.put(ce.items.get(i).value, new TreeSet<String>());
        results.get(ce.items.get(i).value).add(vectorToStr(tokens, prev, id));
      }
    }
    return id;
  }
  
  private int match(BranchExpr be, Vector<String> tokens, int lo, int depth) {
    if (lo == tokens.size()) return lo;
    //db("BE " + lo + " " + depth);
    if (depth > MAX_DEPTH) return -1;
    if (!be.hasRHS) {
      int id = lo;
      for (int i = 0; i < be.expr.size(); i++) {
        if (id >= tokens.size()) return -1;
        id = match(be.expr.get(i), tokens, id, depth + 1);
        if (id < 0) return id;
      }
      return id;
    }
    return Math.max(match(be.lhs, tokens, lo, depth + 1),
                    match(be.rhs, tokens, lo, depth + 1));
  }
  
  //returns whether the symbol s accepts a text t
  public boolean matches(String s, String t, HashMap<String, TreeSet<String>> m) throws Exception {
    if (!defs.containsKey(s))
      throw new Exception("Error: symbol " + s + " not defined.");
    
    //remove all non-word characters 0-9, a-z, A-Z, then split into tokens
    Vector<String> tTokens = new Vector<String>(Arrays.asList(t.replace("[\\W]+", "").split("\\s+")));
    
    recursedTooDeep = false;
    results = m;
    int endidx = match(defs.get(s), tTokens, 0, 0);
    if (recursedTooDeep)
      throw new Exception("Text cannot be matched - recursion too deep!");
    //db(endidx);
    return endidx == tTokens.size();
  }
  
  /******************* Back-end Tests ******************/

  public static void main(String[] args) throws Exception {
    //System.out.println(getTokens("<opt-suffix-part> ::= \"Sr.\" | \"Jr.\" | <roman-numeral> | \"\""));
    BackusNaur bn = new BackusNaur(new File("resources/test.bn"));
    if (false) {      
      HashMap<String, TreeSet<String>> res = null;
      boolean matched = bn.matches("article", "1 + 1", res);
      for (String symbol : res.keySet()) {
        System.out.printf("Matches for <%s>:\n", symbol);
        TreeSet<String> matches = res.get(symbol);
        for (String match : matches) {
          System.out.println(">>> " + match);
        }
        System.out.println();
      }
    }
  }
}
