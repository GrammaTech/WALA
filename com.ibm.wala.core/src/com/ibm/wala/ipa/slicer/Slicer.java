/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.slicer;

import java.util.Collection;
import java.util.Collections;

import com.ibm.wala.dataflow.IFDS.BackwardsSDGSupergraph;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISDGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dataflow.IFDS.UnorderedDomain;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;

/**
 * A demand-driven context-sensitive slicer.
 * 
 * This computes a context-sensitive slice, building an SDG and finding realizable paths to a statement using tabulation.
 * 
 * This implementation uses a preliminary pointer analysis to compute data dependence between heap locations in the SDG.
 */
public class Slicer {

  public final static boolean DEBUG = false;

  public final static boolean VERBOSE = false;

  /**
   * options to control data dependence edges in the SDG
   */
  public static enum DataDependenceOptions {
    FULL("full", false, false, false, false), NO_BASE_PTRS("no_base_ptrs", true, false, false, false), NO_BASE_NO_HEAP(
        "no_base_no_heap", true, true, false, false), NO_BASE_NO_EXCEPTIONS("no_base_no_exceptions", true, false, false, true), NO_BASE_NO_HEAP_NO_EXCEPTIONS(
        "no_base_no_heap_no_exceptions", true, true, false, true), NO_HEAP("no_heap", false, true, false, false), NO_HEAP_NO_EXCEPTIONS(
        "no_heap_no_exceptions", false, true, false, true), NO_EXCEPTIONS("no_exceptions", false, false, false, true), NONE("none",
        true, true, true, true), REFLECTION("no_base_no_heap_no_cast", true, true, true, true);

    private final String name;

    /**
     * Ignore data dependence edges representing base pointers? e.g for a statement y = x.f, ignore the data dependence edges for x
     */
    private final boolean ignoreBasePtrs;

    /**
     * Ignore all data dependence edges to or from the heap?
     */
    private final boolean ignoreHeap;

    /**
     * Ignore outgoing data dependence edges from a cast statements? [This is a special case option used for reflection processing]
     */
    private final boolean terminateAtCast;

    /**
     * Ignore data dependence manifesting throw exception objects?
     */
    private final boolean ignoreExceptions;

    DataDependenceOptions(String name, boolean ignoreBasePtrs, boolean ignoreHeap, boolean terminateAtCast, boolean ignoreExceptions) {
      this.name = name;
      this.ignoreBasePtrs = ignoreBasePtrs;
      this.ignoreHeap = ignoreHeap;
      this.terminateAtCast = terminateAtCast;
      this.ignoreExceptions = ignoreExceptions;
    }

    public final boolean isIgnoreBasePtrs() {
      return ignoreBasePtrs;
    }

    public final boolean isIgnoreHeap() {
      return ignoreHeap;
    }

    public final boolean isIgnoreExceptions() {
      return ignoreExceptions;
    }

    /**
     * Should data dependence chains terminate at casts? This is used for reflection processing ... we only track flow into casts
     * ... but not out.
     */
    public final boolean isTerminateAtCast() {
      return terminateAtCast;
    }

    public final String getName() {
      return name;
    }
  }

  /**
   * options to control control dependence edges in the sdg
   */
  public static enum ControlDependenceOptions {
    FULL("full"), NONE("none"), NO_EXCEPTIONAL_EDGES("no_exceptional_edges");

    private final String name;

    ControlDependenceOptions(String name) {
      this.name = name;
    }

    public final String getName() {
      return name;
    }
  }

  /**
   * @param s a statement of interest
   * @return the backward slice of s.
   * @throws CancelException
   */
  public static <U extends InstanceKey> Collection<Statement> computeBackwardSlice(Statement s, CallGraph cg, PointerAnalysis<U> pa,
      Class<U> instanceKeyClass, DataDependenceOptions dOptions, ControlDependenceOptions cOptions) throws IllegalArgumentException, CancelException {
    return computeBackwardSlice(s,cg,pa,instanceKeyClass,dOptions,cOptions, -1, -1);
  }
  
  /**
   * @param s a statement of interest
   * @return the backward slice of s.
   * @throws CancelException
   */
  public static <U extends InstanceKey> Collection<Statement> computeBackwardSlice(Statement s, CallGraph cg, PointerAnalysis<U> pa,
      Class<U> instanceKeyClass, DataDependenceOptions dOptions, ControlDependenceOptions cOptions, int threshold, long timeoutSec)
          throws IllegalArgumentException, CancelException {
    SDGBuilder<U> sdgBuilder = new SDGBuilder<U>();
    sdgBuilder.setCg(cg);
    sdgBuilder.setPa(pa);
    sdgBuilder.setModRef(ModRef.make(instanceKeyClass));
    sdgBuilder.setcOptions(cOptions);
    sdgBuilder.setdOptions(dOptions);
    return computeSlice(sdgBuilder.build(), Collections.singleton(s), true, threshold, timeoutSec);
  }

  /**
   * @param s a statement of interest
   * @return the forward slice of s.
   * @throws CancelException
   */
  public static <U extends InstanceKey> Collection<Statement> computeForwardSlice(Statement s, CallGraph cg,
      PointerAnalysis<U> pa, Class<U> instanceKeyClass,
      DataDependenceOptions dOptions, ControlDependenceOptions cOptions) throws IllegalArgumentException, CancelException {
    return computeForwardSlice(s,cg,pa,instanceKeyClass,dOptions,cOptions, -1, -1);
  }
  
  /**
   * @param s a statement of interest
   * @return the forward slice of s.
   * @throws CancelException
   */
  public static <U extends InstanceKey> Collection<Statement> computeForwardSlice(Statement s, CallGraph cg, PointerAnalysis<U> pa,
      Class<U> instanceKeyClass, DataDependenceOptions dOptions, ControlDependenceOptions cOptions, int threshold, long timeoutSec)
          throws IllegalArgumentException, CancelException {
    SDGBuilder<U> sdgBuilder = new SDGBuilder<U>();
    sdgBuilder.setCg(cg);
    sdgBuilder.setPa(pa);
    sdgBuilder.setModRef(ModRef.make(instanceKeyClass));
    sdgBuilder.setcOptions(cOptions);
    sdgBuilder.setdOptions(dOptions);
    return computeSlice(sdgBuilder.build(), Collections.singleton(s), false, threshold, timeoutSec);
  }

  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeBackwardSlice(SDG sdg, Statement s, int threshold, long timeoutSec) throws IllegalArgumentException, CancelException {
    return computeSlice(sdg, Collections.singleton(s), true, threshold, timeoutSec);
  }
  
  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeBackwardSlice(SDG sdg, Statement s) throws IllegalArgumentException, CancelException {
    return computeBackwardSlice(sdg,s,-1,-1);
  }

  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeForwardSlice(SDG sdg, Statement s, int threshold, long timeoutSec) throws IllegalArgumentException, CancelException {
    return computeSlice(sdg, Collections.singleton(s), false, threshold, timeoutSec);
  }
  
  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeForwardSlice(SDG sdg, Statement s) throws IllegalArgumentException, CancelException {
    return computeForwardSlice(sdg,s,-1,-1);
  }

  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeForwardSlice(SDG sdg, Collection<Statement> ss, int threshold, long timeoutSec) throws IllegalArgumentException, CancelException {
    return computeSlice(sdg, ss, false, threshold, timeoutSec);
  }
  
  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeForwardSlice(SDG sdg, Collection<Statement> ss) throws IllegalArgumentException,
  CancelException {
    return computeForwardSlice(sdg,ss,-1,-1);
  }
  
  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeBackwardSlice(SDG sdg, Collection<Statement> ss, int threshold, long timeoutSec) throws IllegalArgumentException,
      CancelException {
    return computeSlice(sdg, ss, true, threshold, timeoutSec);
  }
  
  /**
   * Use the passed-in SDG
   * 
   * @throws CancelException
   */
  public static Collection<Statement> computeBackwardSlice(SDG sdg, Collection<Statement> ss) throws IllegalArgumentException,
  CancelException {
    return computeBackwardSlice(sdg,ss,-1, -1);
  }

  /**
   * @param ss a collection of statements of interest
   * @throws CancelException
   */
  protected static Collection<Statement> computeSlice(SDG sdg, Collection<Statement> ss, boolean backward, int threshold, long timeoutSec) throws CancelException {
    if (sdg == null) {
      throw new IllegalArgumentException("sdg cannot be null");
    }
    return new Slicer().slice(sdg, ss, backward,threshold, timeoutSec);
  }



  /**
   * Main driver logic.
   * 
   * @param sdg governing system dependence graph
   * @param roots set of roots to slice from
   * @param backward do a backwards slice?
   * @param threshold should be -1 for a normal slice, and some positive integer for a bounded slice. The higher the number, the larger the slice.
   * @param timeoutSec timeout in seconds
   * @return the {@link Statement}s found by the slicer
   * @throws CancelException
   */
  public Collection<Statement> slice(SDG sdg, Collection<Statement> roots, boolean backward, int threshold, long timeoutSec) throws CancelException {
    if (sdg == null) {
      throw new IllegalArgumentException("sdg cannot be null");
    }

    
    SliceProblem p = makeSliceProblem(roots, sdg, backward);

    PartiallyBalancedTabulationSolver<Statement, PDG<?>, Object> solver = PartiallyBalancedTabulationSolver
        .createPartiallyBalancedTabulationSolver(p, null);
    TabulationResult<Statement, PDG<?>, Object> tr = solver.solve(threshold, timeoutSec);

    Collection<Statement> slice = tr.getSupergraphNodesReached();

    if (VERBOSE) {
      System.err.println("Slicer done.");
    }

    return slice;
  }
 
  /** 
   * same as the other slice method, but without threshold
   * @param sdg
   * @param roots
   * @param backward
   * @return
   * @throws CancelException
   */
  public Collection<Statement> slice(SDG sdg, Collection<Statement> roots, boolean backward) throws CancelException {
    return slice(sdg,roots,backward,-1,-1);
  }
  
  /**
   * Slice method for SDGSupergraphLightweight
   */
  public Collection<Long> slice(SDGSupergraphLightweight sdg, Collection<Long> roots, boolean backward, int threshold,
      long timeoutSec) throws CancelException {
    if (sdg == null) {
      throw new IllegalArgumentException("sdg cannot be null");
    }
    SliceProblem<Long, Integer> p = new SliceProblem<Long, Integer>(roots, sdg, backward);
    PartiallyBalancedTabulationSolver<Long, Integer, Object> solver = PartiallyBalancedTabulationSolver
        .createPartiallyBalancedTabulationSolver(p, null);
    TabulationResult<Long, Integer, Object> tr = solver.solve(threshold, timeoutSec);
    Collection<Long> slice = tr.getSupergraphNodesReached();
    return slice;
  }

  public Collection<Long> slice(SDGSupergraphLightweight sdg, Collection<Long> roots, boolean backward) throws CancelException {
    return slice(sdg, roots, backward, -1, -1);
  }

  /**
   * Return an object which encapsulates the tabulation logic for the slice problem. Subclasses can override this method to
   * implement special semantics.
   */
  protected SliceProblem makeSliceProblem(Collection<Statement> roots, ISDG sdgView, boolean backward) {
    return new SliceProblem<Statement,PDG<? extends InstanceKey>>(roots, new SDGSupergraph(sdgView, backward), backward);
  }

  /**
   * @param s a statement of interest
   * @return the backward slice of s.
   * @throws IllegalArgumentException 
   * @throws CancelException
   */
  public static Collection<Statement> computeBackwardSlice(Statement s, CallGraph cg, PointerAnalysis<InstanceKey> pointerAnalysis) throws IllegalArgumentException, CancelException {
    return computeBackwardSlice(s,cg,pointerAnalysis,-1, -1);
  }

  /**
   * @param s a statement of interest
   * @return the backward slice of s.
   * @throws IllegalArgumentException 
   * @throws CancelException
   */
  public static Collection<Statement> computeBackwardSlice(Statement s, CallGraph cg, PointerAnalysis<InstanceKey> pointerAnalysis, int threshold, long timeoutSec)
      throws IllegalArgumentException, CancelException {
    return computeBackwardSlice(s, cg, pointerAnalysis, InstanceKey.class, DataDependenceOptions.FULL, ControlDependenceOptions.FULL, threshold, timeoutSec);
  }

  /**
   * Tabulation problem representing slicing
   * 
   */
  public static class SliceProblem<T, P> implements PartiallyBalancedTabulationProblem<T, P, Object> {

    private final Collection<T> roots;

    private final ISDGSupergraph<T, P> supergraph;

    private final SliceFunctions<T> f;

    private final boolean backward;

    private final TabulationDomain<Object, T> domain;

    public SliceProblem(Collection<T> roots, ISDGSupergraph<T, P> supergraph, boolean backward) {
      this.roots = roots;
      this.backward = backward;
      this.supergraph = backward ? BackwardsSDGSupergraph.make(supergraph) : supergraph;
      f = new SliceFunctions<T>(supergraph);
      domain = new UnorderedDomain<Object, T>();
    }

    /*
     * @see com.ibm.wala.dataflow.IFDS.TabulationProblem#getDomain()
     */
    @Override
    public TabulationDomain<Object, T> getDomain() {
      // a dummy
      return domain;
    }

    /*
     * @see com.ibm.wala.dataflow.IFDS.TabulationProblem#getFunctionMap()
     */
    @Override
    public IPartiallyBalancedFlowFunctions<T> getFunctionMap() {
      return f;
    }

    /*
     * @see com.ibm.wala.dataflow.IFDS.TabulationProblem#getMergeFunction()
     */
    @Override
    public IMergeFunction getMergeFunction() {
      return null;
    }

    /*
     * @see com.ibm.wala.dataflow.IFDS.TabulationProblem#getSupergraph()
     */
    @Override
    public ISupergraph<T, P> getSupergraph() {
      return supergraph;
    }

    @Override
    public Collection<PathEdge<T>> initialSeeds() {
      if (backward) {
        Collection<PathEdge<T>> result = HashSetFactory.make();
        for (T st : roots) {
          PathEdge<T> seed = PathEdge.createPathEdge(supergraph.getMethodExitNodeForStatement(st), 0, st, 0);
          result.add(seed);
        }
        return result;
      } else {
        Collection<PathEdge<T>> result = HashSetFactory.make();
        for (T st : roots) {
          PathEdge<T> seed = PathEdge.createPathEdge(supergraph.getMethodEntryNodeForStatement(st), 0, st, 0);
          result.add(seed);
        }
        return result;
      }
    }

    @Override
    public T getFakeEntry(T node) {
      return backward ? supergraph.getMethodExitNodeForStatement(node) : supergraph.getMethodEntryNodeForStatement(node);
    }

  }

}
