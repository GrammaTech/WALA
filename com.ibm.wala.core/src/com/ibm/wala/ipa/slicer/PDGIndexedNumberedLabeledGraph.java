package com.ibm.wala.ipa.slicer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;

/**
 * This class provides additional functionality to keep track of statements in the PDG:
 * HeapStatements indexed by PointerKey and non-HeapStatements. These indexes
 * avoid expensive recomputation of relevant statements in method 
 * {@link PDG#createHeapDataDependenceEdges(PointerKey) }
 * 
 * TODO there may be a better way to integrate this functionality into the inheritance
 * hierarchy than extending SlowSparseNumberedLabeledGraph.
 * 
 * TODO other indexing structures from {@link PDG} could be moved here, for example
 * {@link PDG#callerParamStatements}.
 * 
 */
public class PDGIndexedNumberedLabeledGraph<T,U> extends SlowSparseNumberedLabeledGraph<T, U> {

  Map<PointerKey,Set<Statement>> heapStatementsByKey;
  Set<Statement> nonHeapStatements;

  public PDGIndexedNumberedLabeledGraph(U defaultLabel){
    super(defaultLabel);
    heapStatementsByKey = new HashMap<PointerKey,Set<Statement>>();
    nonHeapStatements = new HashSet<Statement>();
  }

  @Override
  public void addNode(T n) {
    if (n instanceof HeapStatement){
      HeapStatement heapStatement = (HeapStatement) n;
      PointerKey pk = heapStatement.getLocation();
      Set<Statement> current = MapUtil.findOrCreateSet(heapStatementsByKey, pk);
      current.add(heapStatement);
    }
    else {
      nonHeapStatements.add((Statement) n);
    }
    getNodeManager().addNode(n);
  }

  public Set<Statement> getHeapStatementsForLocation(PointerKey pk){
    return MapUtil.findOrCreateSet(heapStatementsByKey, pk);
  }

  public Set<Statement> getNonHeapStatements(){
    return nonHeapStatements; 
  }
}