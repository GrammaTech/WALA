/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.dataflow.IFDS;

import java.util.Iterator;
import java.util.HashMap;

import com.ibm.wala.util.intset.BasicNaturalRelation;
import com.ibm.wala.util.intset.IBinaryNaturalRelation;
import com.ibm.wala.util.intset.IntPair;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.math.LongUtil;

/**
 * A set of summary edges for a particular procedure.
 */
public class LocalSummaryEdges {

  /**
   * This code was modified by Lucja Kot in December 2016 after identifying it
   * as a heavy hotspot during profiling.
   * 
   * The old code used two maps, one taking (s_p, x) to an integer n and one
   * taking n to an IBinaryNonNegativeIntRelation. In the refactoring, both were
   * merged into a single java HashMap.
   * 
   * Let s_p be an entry to this procedure, and x be an exit. Let (s_p,x) be an
   * entry-exit pair, and let l := the long whose high word is s_p and low word
   * is x. summaryMap.getl(l) gives a relation R=(d1,d2) s.t. (<s_p, d1> ->
   * <x,d2>) is a summary edge.
   * 
   * Note that this representation is a little different from the representation
   * described in the PoPL 95 paper. We cache summary edges at the CALLEE, not
   * at the CALLER!!! This allows us to avoid eagerly installing summary edges
   * at all call sites to a procedure, which may be a win.
   * 
   * we don't technically need this class, since this information is redundantly
   * stored in LocalPathEdges. However, we're keeping it cached for now for more
   * efficient access when looking up summary edges.
   * 
   * TODO: more representation optimization.
   */
  
  private final HashMap<Long, IBinaryNaturalRelation> summaryMap = new HashMap<Long,IBinaryNaturalRelation>();

  /**
   * 
   */
  public LocalSummaryEdges() {
  }

  /**
   * Record a summary edge for the flow d1 -> d2 from an entry s_p to an exit x.
   * 
   * @param s_p local block number an entry
   * @param x local block number of an exit block
   * @param d1 source dataflow fact
   * @param d2 target dataflow fact
   */
  public void insertSummaryEdge(int s_p, int x, int d1, int d2) {
    long index = LongUtil.pack(s_p, x);
    IBinaryNaturalRelation R = summaryMap.get(index);
    if (R == null) {
      // we expect R to usually be sparse
      R = new BasicNaturalRelation(new byte[] { BasicNaturalRelation.SIMPLE_SPACE_STINGY }, BasicNaturalRelation.SIMPLE);
      summaryMap.put(index, R);
    }
    R.add(d1, d2);
//    if (TabulationSolver.DEBUG_LEVEL > 1) {
//      // System.err.println("recording summary edge, now n=" + n + " summarized by " + R);
//    }
  }

  /**
   * Does a particular summary edge exist?
   * 
   * @param s_p local block number an entry
   * @param x local block number of an exit block
   * @param d1 source dataflow fact
   * @param d2 target dataflow fact
   */
  public boolean contains(int s_p, int x, int d1, int d2) {
    long index = LongUtil.pack(s_p, x);
    IBinaryNaturalRelation R = summaryMap.get(index);
    if (R == null) {
      return false;
    } else {
      return R.contains(d1, d2);
    }
  }

  /**
   * @param s_p local block number an entry
   * @param x local block number of an exit block
   * @param d1 source dataflow fact
   * @return set of d2 s.t. d1->d2 recorded as a summary edge for (s_p,x), or null if none
   */
  public IntSet getSummaryEdges(int s_p, int x, int d1) {
    long index = LongUtil.pack(s_p, x);
    IBinaryNaturalRelation R = summaryMap.get(index);
    if (R == null) {
      return null;
    } else {
      return R.getRelated(d1);
    }
  }

  /**
   * Note: This is inefficient. Use with care.
   * 
   * @param s_p local block number an entry
   * @param x local block number of an exit block
   * @param d2 target dataflow fact
   * @return set of d1 s.t. d1->d2 recorded as a summary edge for (s_p,x), or null if none
   */
  public IntSet getInvertedSummaryEdgesForTarget(int s_p, int x, int d2) {
    long index = LongUtil.pack(s_p, x);
    IBinaryNaturalRelation R = summaryMap.get(index);
    if (R == null) {
      return null;
    } else {
      MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
      for (Iterator it = R.iterator(); it.hasNext();) {
        IntPair p = (IntPair) it.next();
        if (p.getY() == d2) {
          result.add(p.getX());
        }
      }
      return result;
    }
  }

}