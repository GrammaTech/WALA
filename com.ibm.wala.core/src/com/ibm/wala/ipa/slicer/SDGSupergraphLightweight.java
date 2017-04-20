package com.ibm.wala.ipa.slicer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Table;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.intset.IntSet;

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
    // TODO
    // Can be computed from the Statement.Kind and successors of call, see
    // SDGSupergraph.getCalledNodes()
    return null;
  }

  @Override
  public int getLocalBlockNumber(Long n) {
    // TODO
    return 0;
  }

  @Override
  public Integer getProcOf(Long n) {
    // TODO
    return null;
  }

  @Override
  public boolean isCall(Long n) {
    // TODO
    return false;
  }

  @Override
  public boolean isReturn(Long n) {
    // TODO
    return false;
  }

  @Override
  public boolean isEntry(Long n) {
    // TODO
    return false;
  }

  @Override
  public boolean isExit(Long n) {
    // TODO
    return false;
  }

  @Override
  public Long getLocalBlock(Integer procedure, int i) {
    /*
     * TODO we don't know the last 4 bits. We could iterate over all keys in the
     * successors map and see if it matches, but there may be a better
     * workaround: change the return type of the only caller, which is
     * TabulationSolver$Result.getSupergraphNodesReached(). Then getLocalBlock
     * should not need to be invoked at all.
     */
    return null;
  }

  /*
   * TODO the next two methods don't need to be implemented here, but require
   * changes to the CallFlowEdges data structure. CallFlowEdges needs to be
   * indexed by Longs and not by Integers. Corresponding changes are needed in
   * TabulationSolver when these methods are invoked.
   */

  @Override
  public int getNumber(Long N) {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public Long getNode(int number) {
    Assertions.UNREACHABLE();
    return null;
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
  public int getNumberOfNodes() {
    Assertions.UNREACHABLE();
    return 0;
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