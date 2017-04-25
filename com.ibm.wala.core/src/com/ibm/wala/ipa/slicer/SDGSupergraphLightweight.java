package com.ibm.wala.ipa.slicer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Table;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.intset.IntSet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class providing lightweight representation of an SDG for slicing. Every
 * Statement is represented by a Long, where the bits are allocated as follows,
 * indexing from the right:
 * 
 * bits 35-63 - 29 bits to encode the PDG id
 * 
 * bits 5-34 - 30 bits to encode the id of the Statement within the respective
 * PDG
 * 
 * bits 1-4 - 4 bits encodes the Statement.Kind (16 options)
 * 
 * bit 0 - result of SDGSupergraph.isCall (depends on control dependence
 * settings so must be stored separately)
 */
@JsonDeserialize(using = com.ibm.wala.ipa.slicer.json.SDGSupergraphLightweightDeserializer.class)
public class SDGSupergraphLightweight implements ISupergraph<Long, Integer> {

  /*
   * In successors we assume every node is listed as a key even if has no
   * successors/ In predecessors we make no such assumption to avoid wasting
   * space.
   */
  private final Map<Long, List<Long>> successors;
  private final Map<Long, List<Long>> predecessors;

  // map every procedure (PDG id) to the set of entry/exit nodes
  // see SDGSupergraph.getEntriesForProcedure() and getExitsForProcedure()
  private final Map<Integer, Long[]> procEntries;
  private final Map<Integer, Long[]> procExits;

  // maps statement to call site if stmt has one
  private final Map<Long, Integer> stmtsToCallSites;

  // pdg ID -> call site -> caller param and return statements
  private final Table<Integer, Integer, Set<Long>> callStmtsForSite;
  private final Table<Integer, Integer, Set<Long>> retStmtsForSite;

  public SDGSupergraphLightweight(Map<Long, List<Long>> successors, Map<Long, List<Long>> predecessors,
      Map<Integer, Long[]> procedureEntries, Map<Integer, Long[]> procedureExits, Map<Long, Integer> stmtsToCallIndexes,
      Table<Integer, Integer, Set<Long>> callStatementsForSite, Table<Integer, Integer, Set<Long>> returnStatementsForSite) {
    this.successors = successors;
    this.predecessors = predecessors;
    this.procEntries = procedureEntries;
    this.procExits = procedureExits;
    this.stmtsToCallSites = stmtsToCallIndexes;
    this.callStmtsForSite = callStatementsForSite;
    this.retStmtsForSite = returnStatementsForSite;
  }

  @Override
  public Iterator<Long> iterator() {
    return successors.keySet().iterator();
  }

  @Override
  public int getNumberOfNodes() {
    return successors.keySet().size();
  }

  @Override
  public boolean containsNode(Long n) {
    return successors.containsKey(n);
  }

  @Override
  public Iterator<Long> getPredNodes(Long n) {
    return predecessors.get(n).iterator();
  }

  @Override
  public Iterator<Long> getSuccNodes(Long n) {
    return successors.get(n).iterator();
  }

  @Override
  public boolean hasEdge(Long src, Long dst) {
    List<Long> srcSuccessors = successors.get(src);
    return (srcSuccessors != null && srcSuccessors.contains(dst));
  }

  @Override
  public Long[] getEntriesForProcedure(Integer procedure) {
    return procEntries.get(procedure);
  }

  @Override
  public Long[] getExitsForProcedure(Integer procedure) {
    return procExits.get(procedure);
  }

  public Set<Long> getCallSitesAsSet(Long ret, Integer callee) {
    Integer callIndex = stmtsToCallSites.get(ret);
    if (callIndex == null) { // not a return statement
      return null;
    }
    return callStmtsForSite.get(getProcOf(ret), callIndex);
  }

  public Set<Long> getReturnSitesAsSet(Long call, Integer callee) {
    Integer callIndex = stmtsToCallSites.get(call);
    if (callIndex == null) { // not a call statement
      return null;
    }
    return retStmtsForSite.get(getProcOf(call), callIndex);
  }

  @Override
  public Iterator<? extends Long> getReturnSites(Long call, Integer callee) {
    return getReturnSitesAsSet(call, callee).iterator();
  }

  @Override
  public Iterator<? extends Long> getCallSites(Long ret, Integer callee) {
    return getCallSitesAsSet(ret, callee).iterator();
  }

  @Override
  public Iterator<? extends Long> getCalledNodes(Long call) {
    int kind = getKind(call);
    if (kind == Statement.Kind.NORMAL.ordinal()) {
      Set<Long> result = new HashSet<Long>();
      Iterator<Long> succIter = getSuccNodes(call);
      while (succIter.hasNext()) {
        Long succ = succIter.next();
        if (isEntry(succ)) {
          result.add(succ);
        }
      }
      return result.iterator();
    } else if (kind == Statement.Kind.PARAM_CALLER.ordinal() || kind == Statement.Kind.HEAP_PARAM_CALLER.ordinal()) {
      return getSuccNodes(call);
    }
    Assertions.UNREACHABLE(Statement.Kind.values()[kind]);
    return null;
  }

  @Override
  public int getLocalBlockNumber(Long n) {
    return (int) ((n >> 5) & 1073741823); // 2^30-1, i.e. a string of 30 1's
  }

  @Override
  public Integer getProcOf(Long n) {
    return (int) ((n >> 35) & 536870911); // 2^29-1 i.e. a string of 29 1's
  }

  @Override
  public boolean isCall(Long n) {
    return (n & 1) == 1;
  }

  /**
   * Extract the Statement.Kind back from the statement encoding as a Long
   */
  private int getKind(Long stmt) {
    return (int) ((stmt & 30) >> 1);
  }

  @Override
  public boolean isReturn(Long n) {
    int kind = getKind(n);
    return (kind == Statement.Kind.EXC_RET_CALLER.ordinal() || kind == Statement.Kind.HEAP_RET_CALLER.ordinal()
        || kind == Statement.Kind.NORMAL_RET_CALLER.ordinal());
  }

  @Override
  public boolean isEntry(Long n) {
    int kind = getKind(n);
    return (kind == Statement.Kind.PARAM_CALLEE.ordinal() || kind == Statement.Kind.HEAP_PARAM_CALLEE.ordinal()
        || kind == Statement.Kind.METHOD_ENTRY.ordinal());
  }

  @Override
  public boolean isExit(Long n) {
    int kind = getKind(n);
    return (kind == Statement.Kind.EXC_RET_CALLEE.ordinal() || kind == Statement.Kind.HEAP_RET_CALLEE.ordinal()
        || kind == Statement.Kind.NORMAL_RET_CALLEE.ordinal() || kind == Statement.Kind.METHOD_EXIT.ordinal());
  }

  /**
   * TODO note that this does not actually recover the last five bits, so the
   * Long returned is not the same representation of the Statement as used in
   * the rest of the algorithm. It is not clear that those five bits are needed,
   * however. If they are needed, we can retrieve them by looping over the
   * keySet() of successors as in {@link #getNode(int)}.
   * 
   * The only caller is TabulationSolver$Result.getSupergraphNodesReached(),
   * which retrieves the results after slicing is complete.
   */
  @Override
  public Long getLocalBlock(Integer procedure, int i) {
    return ((long) procedure << 35) | ((long) i << 5);
  }

  /**
   * TODO For now we pack the pdgId and localStmtId into a single integer,
   * giving them 16 bits each. This gives a max limit of 2^16 for both the
   * number of pdgs and the number of nodes inside a PDG.
   * 
   * The cleaner way would be to change the return type of getNumber to long,
   * and the parameter type of getNode to long. However, this would require
   * making changes to the CallFlowEdges data structure. CallFlowEdges would
   * need to be indexed by Longs and not by Integers. Corresponding changes are
   * needed in TabulationSolver when these methods are invoked.
   */
  @Override
  public int getNumber(Long statement) {
    int localStmtId = getLocalBlockNumber(statement);
    int pdgId = getProcOf(statement);
    return (pdgId << 16) | localStmtId;
  }

  @Override
  public Long getNode(int number) {
    int pdgId = number >> 16;
    int localStmtId = number & 65535; // 2^16 - 1 i.e. a string with 16 1's.
    Long packed = getLocalBlock(pdgId, localStmtId);
    for (Long key : successors.keySet()) {
      System.out.println("key " + Long.toBinaryString(key));
      if ((key >> 5) == (packed >> 5)) {
        return key;
      }
    }
    throw new IllegalArgumentException("No node found for number " + number);
  }

  /**
   * The logic in SDGSupergraph for the backwards case seems unnecessary given
   * the way BackwardsSupergraph works
   */
  @Override
  public Iterator<Long> getNormalSuccessors(Long call) {
    return EmptyIterator.instance();
  }

  // Methods below here are never called in TabulationSolver
  @Override
  public int getMaxNumber() {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public IntSet getSuccNodeNumbers(Long node) {
    Assertions.UNREACHABLE();
    return null;
  }

  @Override
  public IntSet getPredNodeNumbers(Long node) {
    Assertions.UNREACHABLE();
    return null;
  }

  // Methods below here are not even implemented in SDGSupergraph
  @Override
  public Graph<? extends Integer> getProcedureGraph() {
    Assertions.UNREACHABLE();
    return null;
  }

  @Override
  public byte classifyEdge(Long src, Long dest) {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public int getNumberOfBlocks(Integer procedure) {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public void removeNodeAndEdges(Long n) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void addNode(Long n) {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeNode(Long n) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void addEdge(Long src, Long dst) {
    Assertions.UNREACHABLE();
  }

  @Override
  public int getPredNodeCount(Long n) {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public int getSuccNodeCount(Long N) {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public void removeAllIncidentEdges(Long node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeEdge(Long src, Long dst) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeIncomingEdges(Long node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeOutgoingEdges(Long node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public Iterator<Long> iterateNodes(IntSet s) {
    Assertions.UNREACHABLE();
    return null;
  }
}