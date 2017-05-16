package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Table;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.ibm.wala.ipa.slicer.SDGSupergraphLightweight;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SDGSupergraphLightweightDeserializer extends StdDeserializer<SDGSupergraphLightweight> {

  // data structures needed in SDGSupergraphLightweight
  private Map<Long, Set<Long>> successors;
  private Map<Long, Set<Long>> predecessors;
  private Map<Integer, Long[]> procEntries;
  private Map<Integer, Long[]> procExits;
  private Table<Integer, Integer, Set<Long>> callStmtsForSite;
  private Table<Integer, Integer, Set<Long>> retStmtsForSite;
  private Map<Long, Integer> stmtsToCallSites;
  private Map<Long, Integer> locationHashCodes;
  private Map<Long, Integer> locationToStringHashCodes;
  private Table<Integer, Integer, Byte> kindInfoMap;

  private ControlDependenceOptions cOptions;

  // relevant groups of statement kinds
  static final Set<Integer> ENTRY_STMT_TYPES = Sets.newHashSet(Statement.Kind.METHOD_ENTRY.ordinal(),
      Statement.Kind.PARAM_CALLEE.ordinal(), Statement.Kind.HEAP_PARAM_CALLEE.ordinal());
  static final Set<Integer> EXIT_STMT_TYPES = Sets.newHashSet(Statement.Kind.METHOD_EXIT.ordinal(),
      Statement.Kind.NORMAL_RET_CALLEE.ordinal(), Statement.Kind.HEAP_RET_CALLEE.ordinal(),
      Statement.Kind.EXC_RET_CALLEE.ordinal());
  static final Set<Integer> CALL_STMT_TYPES = Sets.newHashSet(Statement.Kind.NORMAL.ordinal(),
      Statement.Kind.PARAM_CALLER.ordinal(), Statement.Kind.HEAP_PARAM_CALLER.ordinal());
  static final Set<Integer> RET_STMT_TYPES = Sets.newHashSet(Statement.Kind.NORMAL_RET_CALLER.ordinal(),
      Statement.Kind.HEAP_RET_CALLER.ordinal(), Statement.Kind.EXC_RET_CALLER.ordinal());
  static final Set<Integer> HEAP_STMT_TYPES = Sets.newHashSet(Statement.Kind.HEAP_PARAM_CALLEE.ordinal(),
      Statement.Kind.HEAP_PARAM_CALLER.ordinal(), Statement.Kind.HEAP_RET_CALLEE.ordinal(),
      Statement.Kind.HEAP_RET_CALLER.ordinal());

  public final static boolean VERBOSE = false;

  protected SDGSupergraphLightweightDeserializer(Class<SDGSupergraphLightweight> t) {
    super(t);
  }

  public SDGSupergraphLightweightDeserializer() {
    this(null);
  }

  /**
   * Top-level deserializing method. The deserialization mostly uses the Jackson
   * streaming API, i.e. reads the file token by token. The only exception is
   * {@link #parsePDGs(JsonParser)}, which internally reads each PDG into memory
   * using the tree model.
   */
  @Override
  public SDGSupergraphLightweight deserialize(JsonParser parser, DeserializationContext deserializationContext)
      throws IOException, JsonProcessingException {

    successors = new HashMap<Long,Set<Long>>();
    predecessors = new HashMap<Long,Set<Long>>();
    procEntries = new HashMap<Integer, Long[]>();
    procExits = new HashMap<Integer, Long[]>();
    callStmtsForSite = HashBasedTable.create();
    retStmtsForSite = HashBasedTable.create();
    stmtsToCallSites = new HashMap<Long, Integer>();
    locationHashCodes = new HashMap<Long, Integer>();
    locationToStringHashCodes = new HashMap<Long, Integer>();
    kindInfoMap = HashBasedTable.create();

    long startTime = System.currentTimeMillis();

    parseControlDepOptions(parser);
    parsePDGs(parser);
    parseEdges(parser);

    long endTime = System.currentTimeMillis();

    if (VERBOSE) {
      System.out.println("Time to deserialize was " + (endTime - startTime) / 1000 + " seconds.");
      System.out.println("Number of nodes: " + kindInfoMap.cellSet().size());
    }

    return new SDGSupergraphLightweight(successors, predecessors, procEntries, procExits, stmtsToCallSites, callStmtsForSite,
        retStmtsForSite, locationHashCodes, locationToStringHashCodes, kindInfoMap);
  }

  private void parseControlDepOptions(JsonParser parser) throws IOException {
    parser.nextToken();
    parser.nextToken();
    cOptions = ControlDependenceOptions.valueOf(parser.getValueAsString());
  }

  /**
   * Parses PDGs one at a time. Current implementation reads each individual PDG
   * completely into memory; this may not scale if the PDGs get large.
   * 
   * @param parser
   *          the JsonParser for the file
   * @throws IOException
   */
  private void parsePDGs(JsonParser parser) throws IOException {
    parser.nextToken();
    parser.nextToken(); // start of PDG array
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      // set up running list of entries and exits for this PDG's procedure
      List<Long> entries = new ArrayList<Long>();
      List<Long> exits = new ArrayList<Long>();
      // read PDG into memory
      JsonNode jsonNode = parser.readValueAsTree();
      int pdgId = jsonNode.get("cgNodeId").intValue();
      // process nodes
      JsonNode statements = jsonNode.get("pdg").get("nodes");
      Iterator<JsonNode> stmtIter = statements.iterator();
      while (stmtIter.hasNext()) {
        JsonNode stmt = stmtIter.next();
        processStatement(pdgId, stmt, entries, exits);
      }
      procEntries.put(pdgId, entries.toArray(new Long[entries.size()]));
      procExits.put(pdgId, exits.toArray(new Long[exits.size()]));
    }
  }

  /**
   * Deserialize one Statement (node inside a PDG) and update all appropriate
   * data structures.
   * 
   * @param pdgId
   *          number of PDG statement belongs to
   * @param stmt
   *          serialized version of statement
   * @param entries
   *          current list of entries for the procedure (CG Node) being
   *          processed
   * @param exits
   *          current list of exits for the procedure (CG Node) being processed
   */
  private void processStatement(int pdgId, JsonNode stmt, List<Long> entries, List<Long> exits) {
    // obtain info needed to generate Long id and compute it
    int localId = stmt.get("id").intValue();
    String stmtType = stmt.get("statement").get("type").textValue();
    stmtType = stmtType.substring(stmtType.lastIndexOf(".") + 1);
    boolean isCall = false;
    if (stmtType.equals("ParamCaller") || stmtType.equals("HeapStatement$HeapParamCaller")) {
      isCall = true;
    } else if (!cOptions.equals(ControlDependenceOptions.NONE) && stmtType.equals("NormalStatement")) {
      JsonNode isAbstractInvoke = stmt.get("statement").get("isAbstractInvokeInstruction");
      if (isAbstractInvoke != null) {
        isCall = isAbstractInvoke.asBoolean();
      }
    }
    Long longId = generateLongId(pdgId, localId, stmtType, isCall);
    int stmtKind = getKind(longId);
    // now update data structures with info about this statement
    kindInfoMap.put(pdgId, localId, (byte) (stmtKind << 1 | (isCall ? 1 : 0)));

    if (ENTRY_STMT_TYPES.contains(stmtKind)) {
      entries.add(longId);
    } else if (EXIT_STMT_TYPES.contains(stmtKind)) {
      exits.add(longId);
    }

    JsonNode callSiteJsonNode = stmt.get("statement").get("callSite");
    if (callSiteJsonNode != null) {
      int callSite = callSiteJsonNode.asInt();
      stmtsToCallSites.put(longId, callSite);
      // update maps
      if (CALL_STMT_TYPES.contains(stmtKind)) {
        Set<Long> current = callStmtsForSite.get(pdgId, callSite);
        if (current == null) {
          current = new HashSet<Long>();
          callStmtsForSite.put(pdgId, callSite, current);
        }
        current.add(longId);
      } else if (RET_STMT_TYPES.contains(stmtKind)) {
        Set<Long> current = retStmtsForSite.get(pdgId, callSite);
        if (current == null) {
          current = new HashSet<Long>();
          retStmtsForSite.put(pdgId, callSite, current);
        }
        current.add(longId);
      }
    }
    if (HEAP_STMT_TYPES.contains(stmtKind)) {
      locationHashCodes.put(longId, stmt.get("statement").get("locationHashCode").asInt());
      locationToStringHashCodes.put(longId, stmt.get("statement").get("locationToStringHashCode").asInt());
    }
  }

  /**
   * Generate encoding of statement info as a Long, as required by
   * {@link SDGSupergraphLightweight}.
   */
  private long generateLongId(int pdgNumber, int statementNumber, String type, Boolean isCall) {
    int kind = 0;
    /*
     * TODO it may be possible to serialize the ordinal of the Kind into the
     * Json representation, which would remove need for the below switch
     * statement. This would require more knowledge of Jackson APIs (notably how
     * to write a custom TypeIdResolver).
     */
    switch (type) {
    case "ExceptionalReturnCallee":
      kind = Statement.Kind.EXC_RET_CALLEE.ordinal();
      break;
    case "ExceptionalReturnCaller":
      kind = Statement.Kind.EXC_RET_CALLER.ordinal();
      break;
    case "GetCaughtExceptionStatement":
      kind = Statement.Kind.CATCH.ordinal();
      break;
    case "HeapStatement$HeapParamCallee":
      kind = Statement.Kind.HEAP_PARAM_CALLEE.ordinal();
      break;
    case "HeapStatement$HeapParamCaller":
      kind = Statement.Kind.HEAP_PARAM_CALLER.ordinal();
      break;
    case "HeapStatement$HeapReturnCallee":
      kind = Statement.Kind.HEAP_RET_CALLEE.ordinal();
      break;
    case "HeapStatement$HeapReturnCaller":
      kind = Statement.Kind.HEAP_RET_CALLER.ordinal();
      break;
    case "MethodEntryStatement":
      kind = Statement.Kind.METHOD_ENTRY.ordinal();
      break;
    case "MethodExitStatement":
      kind = Statement.Kind.METHOD_EXIT.ordinal();
      break;
    case "NormalReturnCallee":
      kind = Statement.Kind.NORMAL_RET_CALLEE.ordinal();
      break;
    case "NormalReturnCaller":
      kind = Statement.Kind.NORMAL_RET_CALLER.ordinal();
      break;
    case "NormalStatement":
      kind = Statement.Kind.NORMAL.ordinal();
      break;
    case "ParamCallee":
      kind = Statement.Kind.PARAM_CALLEE.ordinal();
      break;
    case "ParamCaller":
      kind = Statement.Kind.PARAM_CALLER.ordinal();
      break;
    case "PhiStatement":
      kind = Statement.Kind.PHI.ordinal();
      break;
    case "PiStatement":
      kind = Statement.Kind.PI.ordinal();
      break;
    default:
      throw new IllegalArgumentException("Invalid statement type " + type);
    }
    return ((long) pdgNumber << 35) | ((long) statementNumber << 5) | (long) kind << 1 | (isCall ? 1 : 0);
  }

  private void parseEdges(JsonParser parser) throws IOException {
    parser.nextToken();
    parser.nextToken(); // start of edges array
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      JsonNode jsonNode = parser.readValueAsTree();
      Long src = SDGSupergraphLightweight.getLocalBlock(kindInfoMap, jsonNode.get("node").get(0).asInt(),
          jsonNode.get("node").get(1).asInt());
      if (jsonNode.get("successors") != null) {
        Iterator<JsonNode> succIterator = jsonNode.get("successors").iterator();
        while (succIterator.hasNext()) {
          JsonNode succ = succIterator.next();
          Long succId = SDGSupergraphLightweight.getLocalBlock(kindInfoMap,succ.get(0).asInt(), succ.get(1).asInt());
          Set<Long> currentSuccessors = MapUtil.findOrCreateSet(successors, src);
          currentSuccessors.add(succId);
        }
      }
      if (jsonNode.get("predecessors") != null) {
        Iterator<JsonNode> predIterator = jsonNode.get("predecessors").iterator();
        while (predIterator.hasNext()) {
          JsonNode pred = predIterator.next();
          Long predId = SDGSupergraphLightweight.getLocalBlock(kindInfoMap,pred.get(0).asInt(), pred.get(1).asInt());
          Set<Long> currentPredecessors = MapUtil.findOrCreateSet(predecessors, src);
          currentPredecessors.add(predId);
        }
      }
    }
  }

  /**
   * Extract the Statement.Kind back from the statement encoding as a Long
   */
  private int getKind(Long stmt) {
    return (int) ((stmt & 30) >> 1);
  }

}
