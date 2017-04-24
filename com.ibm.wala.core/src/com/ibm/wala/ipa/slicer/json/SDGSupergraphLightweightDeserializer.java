package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.ibm.wala.ipa.slicer.SDGSupergraphLightweight;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.util.collections.MapUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SDGSupergraphLightweightDeserializer extends StdDeserializer<SDGSupergraphLightweight> {

  // data structures needed in SDGSupergraphLightweight
  private Map<Long, List<Long>> successors;
  private Map<Long, List<Long>> predecessors;
  private Map<Integer, Long[]> procEntries;
  private Map<Integer, Long[]> procExits;
  private Table<Integer, Integer, Set<Long>> callStmtsForSite;
  private Table<Integer, Integer, Set<Long>> retStmtsForSite;
  private Map<Long, Integer> stmtsToCallSites;

  // internal data structures
  private ControlDependenceOptions cOptions;
  private DataDependenceOptions dOptions;
  // pdgIds for those CG Nodes that are uninformative for reflection
  private Set<Integer> cgNodesUninfForReflection;
  // pdgId -> call site -> pdgIds of call targets
  private Table<Integer, Integer, Set<Integer>> callTargets;
  // pdgId -> call site -> id of statement with call instruction
  private Table<Integer, Integer, Long> callInstructions;

  // id of statement with call instruction that is a dispatch -> receiver
  private Map<Long, Integer> receiverInfo;
  // id of statement with call instruction -> set of params
  private Map<Long, List<Integer>> invokeInstructionParams;
  // id of statement that is a PARAM_CALLER or CALLEE -> value number
  private Map<Long, Integer> paramValueNumbers;

  // maps used to compare locations for HeapStatements
  private Map<Long, Integer> locationHashCodes;
  private Map<Long, Integer> locationToStringHashCodes;

  // relevant groups of statement kinds
  static final Set<Integer> entryStmtTypes = Sets.newHashSet(Statement.Kind.METHOD_ENTRY.ordinal(),
      Statement.Kind.PARAM_CALLEE.ordinal(), Statement.Kind.HEAP_PARAM_CALLEE.ordinal());
  static final Set<Integer> exitStmtTypes = Sets.newHashSet(Statement.Kind.METHOD_EXIT.ordinal(),
      Statement.Kind.NORMAL_RET_CALLEE.ordinal(), Statement.Kind.HEAP_RET_CALLEE.ordinal(),
      Statement.Kind.EXC_RET_CALLEE.ordinal());
  static final Set<Integer> callStmtTypes = Sets.newHashSet(Statement.Kind.NORMAL.ordinal(), Statement.Kind.PARAM_CALLER.ordinal(),
      Statement.Kind.HEAP_PARAM_CALLER.ordinal());
  static final Set<Integer> retStmtTypes = Sets.newHashSet(Statement.Kind.NORMAL_RET_CALLER.ordinal(),
      Statement.Kind.HEAP_RET_CALLER.ordinal(), Statement.Kind.EXC_RET_CALLER.ordinal());
  static final Set<Integer> heapStmtTypes = Sets.newHashSet(Statement.Kind.HEAP_PARAM_CALLEE.ordinal(),
      Statement.Kind.HEAP_PARAM_CALLER.ordinal(), Statement.Kind.HEAP_RET_CALLEE.ordinal(),
      Statement.Kind.HEAP_RET_CALLER.ordinal());

  int interprocEdgeCounter; // for diagnostics

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

    successors = new HashMap<Long, List<Long>>();
    predecessors = new HashMap<Long, List<Long>>();
    procEntries = new HashMap<Integer, Long[]>();
    procExits = new HashMap<Integer, Long[]>();
    callStmtsForSite = HashBasedTable.create();
    retStmtsForSite = HashBasedTable.create();
    stmtsToCallSites = new HashMap<Long, Integer>();
    callTargets = HashBasedTable.create();
    cgNodesUninfForReflection = new HashSet<Integer>();
    callInstructions = HashBasedTable.create();
    receiverInfo = new HashMap<Long, Integer>();
    invokeInstructionParams = new HashMap<Long, List<Integer>>();
    paramValueNumbers = new HashMap<Long, Integer>();
    locationHashCodes = new HashMap<Long, Integer>();
    locationToStringHashCodes = new HashMap<Long, Integer>();

    parseControlAndDataDeps(parser);
    parsePDGs(parser);
    int intraprocEdgeCounter = 0;
    for (long s : successors.keySet()) {
      intraprocEdgeCounter += successors.get(s).size();
    }
    interprocEdgeCounter = 0;
    parseCallTargetInfo(parser);
    populateInterproceduralEdges();

    System.out.println("Number of nodes: " + successors.keySet().size());
    System.out.println("Number of edges: intraprocedural " + intraprocEdgeCounter + ", interprocedural " + interprocEdgeCounter);

    return new SDGSupergraphLightweight(successors, predecessors, procEntries, procExits, stmtsToCallSites, callStmtsForSite,
        retStmtsForSite);
  }

  private void parseControlAndDataDeps(JsonParser parser) throws IOException {
    parser.nextToken();
    parser.nextToken();
    cOptions = ControlDependenceOptions.valueOf(parser.getValueAsString());
    parser.nextToken();
    parser.nextToken();
    dOptions = DataDependenceOptions.valueOf(parser.getValueAsString());
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
      // set up data structures needed for each PDG
      List<Long> entries = new ArrayList<Long>();
      List<Long> exits = new ArrayList<Long>();
      // stmtIdMap maps each statement's in-PDG local id to its Long id
      HashMap<Integer, Long> stmtIdMap = new HashMap<Integer, Long>();

      // read PDG into memory
      JsonNode jsonNode = parser.readValueAsTree();
      int pdgId = jsonNode.get("nodeId").intValue();

      if (jsonNode.get("pdg").get("isUninformativeForReflection").asBoolean()) {
        cgNodesUninfForReflection.add(pdgId);
      }

      // process nodes
      JsonNode statements = jsonNode.get("pdg").get("nodes");
      Iterator<JsonNode> stmtIter = statements.iterator();
      while (stmtIter.hasNext()) {
        JsonNode stmt = stmtIter.next();
        processStatement(pdgId, stmt, entries, exits, stmtIdMap);
      }
      procEntries.put(pdgId, entries.toArray(new Long[entries.size()]));
      procExits.put(pdgId, exits.toArray(new Long[exits.size()]));

      // process intraprocedural edges
      JsonNode edges = jsonNode.get("pdg").get("edges");
      Iterator<JsonNode> edgeIter = edges.iterator();
      while (edgeIter.hasNext()) {
        JsonNode edgeInfo = edgeIter.next();
        Long src = stmtIdMap.get(edgeInfo.get("node").asInt());
        LinkedList<Long> succList = new LinkedList<Long>();
        Iterator<JsonNode> successorIter = edgeInfo.get("successors").iterator();
        while (successorIter.hasNext()) {
          Long dst = stmtIdMap.get(successorIter.next().asInt());
          succList.add(dst);
          // update predecessor info as well
          List<Long> currentPredecessors = MapUtil.findOrCreateList(predecessors, dst);
          currentPredecessors.add(src);
        }
        successors.put(src, succList);
      }
    }
  }

  private void processStatement(int pdgId, JsonNode stmt, List<Long> entries, List<Long> exits,
      HashMap<Integer, Long> statementIdMap) {
    // obtain info needed to generate Long id and compute it
    int localId = stmt.get("id").intValue();
    String stmtType = stmt.get("statement").get("type").textValue();
    stmtType = stmtType.substring(stmtType.lastIndexOf(".") + 1);
    boolean isCall = false;
    if (!cOptions.equals(ControlDependenceOptions.NONE) && stmtType.equals("NormalStatement")) {
      JsonNode isAbstractInvoke = stmt.get("statement").get("isAbstractInvokeInstruction");
      if (isAbstractInvoke != null) {
        isCall = isAbstractInvoke.asBoolean();
      }
    }
    Long longId = generateLongId(pdgId, localId, stmtType, isCall);
    int stmtKind = getKind(longId);
    statementIdMap.put(localId, longId);

    // now update data structures with info about this statement
    if (entryStmtTypes.contains(stmtKind)) {
      entries.add(longId);
    } else if (exitStmtTypes.contains(stmtKind)) {
      exits.add(longId);
    }

    JsonNode callSiteJsonNode = stmt.get("statement").get("callSite");
    if (callSiteJsonNode != null) {
      int callSite = callSiteJsonNode.asInt();
      stmtsToCallSites.put(longId, callSite);
      // update maps
      if (callStmtTypes.contains(stmtKind)) {
        Set<Long> current = callStmtsForSite.get(pdgId, callSite);
        if (current == null) {
          current = new HashSet<Long>();
          callStmtsForSite.put(pdgId, callSite, current);
        }
        current.add(longId);
        if (stmtKind == Statement.Kind.NORMAL.ordinal()) {
          callInstructions.put(pdgId, callSite, longId);
          boolean isDispatch = stmt.get("statement").get("isDispatch").asBoolean();
          if (isDispatch) {
            receiverInfo.put(longId, stmt.get("statement").get("receiver").asInt());
          }
          Iterator<JsonNode> paramIter = stmt.get("statement").get("parameters").iterator();
          List<Integer> params = new ArrayList<Integer>();
          while (paramIter.hasNext()) {
            params.add(paramIter.next().asInt());
          }
          invokeInstructionParams.put(longId, params);
        }
      } else if (retStmtTypes.contains(stmtKind)) {
        Set<Long> current = retStmtsForSite.get(pdgId, callSite);
        if (current == null) {
          current = new HashSet<Long>();
          retStmtsForSite.put(pdgId, callSite, current);
        }
        current.add(longId);
      }
    }

    if (heapStmtTypes.contains(stmtKind)) {
      locationHashCodes.put(longId, stmt.get("statement").get("locationHashCode").asInt());
      locationToStringHashCodes.put(longId, stmt.get("statement").get("locationToStringHashCode").asInt());
    }

    if (stmtKind == Statement.Kind.PARAM_CALLEE.ordinal() || stmtKind == Statement.Kind.PARAM_CALLER.ordinal()) {
      paramValueNumbers.put(longId, stmt.get("statement").get("value").asInt());
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

  /**
   * Populates data structure callSiteTargets which is needed for computing
   * interprocedural edges
   */

  private void parseCallTargetInfo(JsonParser parser) throws IOException {
    parser.nextToken();
    parser.nextToken(); // start of callTargetInfo array
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      JsonNode jsonNode = parser.readValueAsTree();
      int callerId = jsonNode.get("nodeId").asInt();
      Iterator<JsonNode> callTargetIterator = jsonNode.get("callTargets").iterator();
      while (callTargetIterator.hasNext()) {
        JsonNode callTarget = callTargetIterator.next();
        int callSite = callTarget.get("callSite").asInt();
        Iterator<JsonNode> targetNodeIdIterator = callTarget.get("targetNodeIds").iterator();
        Set<Integer> targetNodeIdSet = new HashSet<Integer>();
        while (targetNodeIdIterator.hasNext()) {
          targetNodeIdSet.add(targetNodeIdIterator.next().asInt());
        }
        if (targetNodeIdSet.size() > 0) {
          callTargets.put(callerId, callSite, targetNodeIdSet);
        }
      }
    }
  }

  /**
   * Compute edges between PDGs. The logic is split into two parts to allow the
   * use of the data structures procEntries, procExits, callStmtsForSite and
   * retStmtsForSite. Most of the actual work is done in
   * {@link #computeInterproceduralSuccessors(Long, Long[])} and
   * {@link #computeInterproceduralPredecessors(Long, Long[])}.
   */
  private void populateInterproceduralEdges() {
    for (Cell<Integer, Integer, Set<Long>> cell : callStmtsForSite.cellSet()) {
      int pdgId = cell.getRowKey();
      int callSite = cell.getColumnKey();
      Set<Long> callStatements = cell.getValue();
      for (Long callStatement : callStatements) {
        Set<Integer> targets = callTargets.get(pdgId, callSite);
        if (targets != null) {
          for (int target : targets) {
            if (getKind(callStatement) == Statement.Kind.PARAM_CALLER.ordinal() && !dOptions.equals(DataDependenceOptions.NONE)) {
              Long callInstruction = callInstructions.get(pdgId, callSite);
              boolean uninfForReflection = cgNodesUninfForReflection.contains(target);
              computeParamCallerSuccessors(callStatement, procEntries.get(target), callInstruction, uninfForReflection);
            } else {
              computeInterproceduralSuccessors(callStatement, procEntries.get(target));
            }
          }
        }
      }
    }
    // all remaining edges only exist if data dependence options are not NONE
    if (!dOptions.equals(DataDependenceOptions.NONE)) {
      for (Cell<Integer, Integer, Set<Long>> cell : retStmtsForSite.cellSet()) {
        int pdgId = cell.getRowKey();
        int callSite = cell.getColumnKey();
        Set<Long> retStatements = cell.getValue();
        for (Long retStatement : retStatements) {
          Set<Integer> targets = callTargets.get(pdgId, callSite);
          if (targets != null) {
            for (int target : targets) {
              computeInterproceduralPredecessors(retStatement, procExits.get(target));
            }
          }
        }
      }
    }
  }

  /**
   * Add all appropriate interprocedural edges from call to any entry statement
   * in procedureEntries.
   * 
   * @precondition the method whose entries are in procedureEntries is a
   *               possible call target for the call site associated with call.
   * 
   * @precondition !dOptions.equals(DataDependenceOptions.NONE)
   * 
   * @see com.ibm.wala.ipa.slicer.SDG.Edges#hasEdge(Statement, Statement)
   * 
   * @param call
   *          a PARAM_CALLER statement
   * @param procedureEntries
   *          entry points (PARAM_CALLEE, HEAP_PARAM_CALLEE, METHOD_ENTRY) for a
   *          specific procedure
   * @param uninfForReflection
   *          whether callee node is uninformative for reflection
   * @param callInstruction
   *          long id of actual invoke instruction for the call
   */
  private void computeParamCallerSuccessors(Long call, Long[] procedureEntries, Long callInstruction, boolean uninfForReflection) {
    for (Long stmt : procedureEntries) {
      if (getKind(stmt) == Statement.Kind.PARAM_CALLEE.ordinal()) {
        if (dOptions.isTerminateAtCast()) {
          Integer receiver = receiverInfo.get(callInstruction);
          if (receiver != null && receiver.equals(paramValueNumbers.get(call)))
            continue;
          if (uninfForReflection)
            continue;
        }
        List<Integer> callerParams = invokeInstructionParams.get(callInstruction);
        for (int i = 0; i < callerParams.size(); i++) {
          if (callerParams.get(i) == paramValueNumbers.get(call)) {
            if (paramValueNumbers.get(stmt) == i + 1) {
              addEdge(call, stmt);
            }
          }
        }
      }
    }
  }

  /**
   * Add all appropriate interprocedural edges from call to any entry statement
   * in procedureEntries.
   * 
   * @precondition the method whose entries are in procedureEntries is a
   *               possible call target for the call site associated with call.
   * 
   * @see com.ibm.wala.ipa.slicer.SDG.Edges#hasEdge(Statement, Statement)
   * 
   * @param call
   *          a call statement other than PARAM_CALLER (HEAP_PARAM_CALLER or
   *          NORMAL with invoke instruction)
   * @param procedureEntries
   *          entry points (PARAM_CALLEE, HEAP_PARAM_CALLEE, METHOD_ENTRY) for a
   *          specific procedure
   */
  private void computeInterproceduralSuccessors(Long call, Long[] procedureEntries) {
    switch (Statement.Kind.values()[getKind(call)]) {
    case NORMAL:
      if (!cOptions.equals(ControlDependenceOptions.NONE)) {
        for (Long stmt : procedureEntries) {
          if (getKind(stmt) == Statement.Kind.METHOD_ENTRY.ordinal()) {
            addEdge(call, stmt);
          }
        }
      }
      break;
    case HEAP_PARAM_CALLER:
      if (!dOptions.equals(DataDependenceOptions.NONE)) {
        for (Long stmt : procedureEntries) {
          if (getKind(stmt) == Statement.Kind.HEAP_PARAM_CALLEE.ordinal() && haveSameLocation(stmt, call)) {
            addEdge(call, stmt);
          }
        }
      }
      break;
    default:
      throw new IllegalArgumentException("Invalid statement kind: " + getKind(call));
    }
  }

  /**
   * Add all appropriate interprocedural edges from any procedure exit statement
   * statement in procedureExits to ret.
   * 
   * @precondition the method whose exits are in procedureExits is a possible
   *               call target for the call site associated with ret.
   * 
   * @precondition !dOptions.equals(DataDependenceOptions.NONE)
   * 
   * @see com.ibm.wala.ipa.slicer.SDG.Edges#hasEdge(Statement, Statement)
   * @param ret
   *          a return statement (NORMAL_RET_CALLER, HEAP_RET_CALLER,
   *          EXC_RET_CALLER)
   * @param procedureExits
   *          exit points (NORMAL_RET_CALLEE, HEAP_RET_CALLEE, EXC_RET_CALLEE,
   *          METHOD_EXIT) for a specific procedure
   */
  private void computeInterproceduralPredecessors(Long ret, Long[] procedureExits) {
    switch (Statement.Kind.values()[getKind(ret)]) {
    case NORMAL_RET_CALLER:
      for (Long stmt : procedureExits) {
        if (getKind(stmt) == Statement.Kind.NORMAL_RET_CALLEE.ordinal()) {
          addEdge(stmt, ret);
        }
      }
      break;
    case EXC_RET_CALLER:
      for (Long stmt : procedureExits) {
        if (getKind(stmt) == Statement.Kind.EXC_RET_CALLEE.ordinal()) {
          addEdge(stmt, ret);
        }
      }
      break;
    case HEAP_RET_CALLER:
      for (Long stmt : procedureExits) {
        if (getKind(stmt) == Statement.Kind.HEAP_RET_CALLEE.ordinal() && haveSameLocation(stmt, ret)) {
          addEdge(stmt, ret);
        }
      }
      break;
    default:
      throw new IllegalArgumentException("Invalid statement kind: " + getKind(ret));
    }
  }

  /**
   * Helper method to update successors and predecessors when edge is added
   */
  private void addEdge(Long src, Long dst) {
    interprocEdgeCounter++;
    List<Long> currentSuccessors = MapUtil.findOrCreateList(successors, src);
    currentSuccessors.add(dst);
    List<Long> currentPredecessors = MapUtil.findOrCreateList(predecessors, dst);
    currentPredecessors.add(src);
  }

  /**
   * Helper method to check if two HeapStatments refer to the same location.
   * Currently the comparison is based on hashCodes of the location and its
   * toString representation, but this could be refined in the future e.g. to
   * HeapGraph node ids.
   */
  private boolean haveSameLocation(Long stmt1, Long stmt2) {
    return locationHashCodes.get(stmt1).equals(locationHashCodes.get(stmt2))
        && locationToStringHashCodes.get(stmt1).equals(locationToStringHashCodes.get(stmt2));
  }

  /**
   * Extract the Statement.Kind back from the statement encoding as a Long
   */
  private int getKind(Long stmt) {
    return (int) ((stmt & 30) >> 1);
  }

}
