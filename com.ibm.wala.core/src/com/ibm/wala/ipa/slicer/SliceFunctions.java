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

import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISDGSupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.util.debug.Assertions;

/**
 * flow functions for flow-sensitive context-sensitive slicer
 * 
 * @param <T>
 *          a type that corresponds to a Statement, typically either a Statement
 *          or a Long
 */
public class SliceFunctions<T> implements IPartiallyBalancedFlowFunctions<T> {

  private ReachabilityFunctions<T> reachabilityFunctions;
  private ISDGSupergraph<T, ?> supergraph;

  public SliceFunctions(ISDGSupergraph<T, ?> supergraph) {
    reachabilityFunctions = ReachabilityFunctions.createReachabilityFunctions();
    this.supergraph = supergraph;
  }

  @Override
  public IUnaryFlowFunction getCallFlowFunction(T src, T dest, T ret) {
    return reachabilityFunctions.getCallFlowFunction(src, dest, ret);
  }

  @Override
  public IUnaryFlowFunction getCallNoneToReturnFlowFunction(T src, T dest) {
    if (src == null) {
      throw new IllegalArgumentException("src is null");
    }
    T s = src;
    switch (supergraph.getKind(s)) {
    case NORMAL_RET_CALLER:
    case PARAM_CALLER:
    case EXC_RET_CALLER:
      // uh oh. anything that flows into the missing function will be killed.
      return ReachabilityFunctions.KILL_FLOW;
    case HEAP_PARAM_CALLEE:
    case HEAP_PARAM_CALLER:
    case HEAP_RET_CALLEE:
    case HEAP_RET_CALLER:
      Kind destKind = supergraph.getKind(dest);
      if (destKind.equals(Kind.HEAP_PARAM_CALLEE) || destKind.equals(Kind.HEAP_PARAM_CALLER)
          || destKind.equals(Kind.HEAP_RET_CALLEE) || destKind.equals(Kind.HEAP_RET_CALLER)) {
        if (supergraph.haveSameLocation(src, dest)) {
          return IdentityFlowFunction.identity();
        } else {
          return ReachabilityFunctions.KILL_FLOW;
        }
      } else {
        return ReachabilityFunctions.KILL_FLOW;
      }
    case NORMAL:
      // only control dependence flows into the missing function.
      // this control dependence does not flow back to the caller.
      return ReachabilityFunctions.KILL_FLOW;
    default:
      Assertions.UNREACHABLE(supergraph.getKind(s).toString());
      return null;
    }
  }

  @Override
  public IUnaryFlowFunction getCallToReturnFlowFunction(T src, T dest) {
    return reachabilityFunctions.getCallToReturnFlowFunction(src, dest);
  }

  @Override
  public IUnaryFlowFunction getNormalFlowFunction(T src, T dest) {
    return reachabilityFunctions.getNormalFlowFunction(src, dest);
  }

  @Override
  public IFlowFunction getReturnFlowFunction(T call, T src, T dest) {
    return reachabilityFunctions.getReturnFlowFunction(call, src, dest);
  }

  public IFlowFunction getReturnFlowFunction(T src, T dest) {
    return reachabilityFunctions.getReturnFlowFunction(src, dest);
  }

  @Override
  public IFlowFunction getUnbalancedReturnFlowFunction(T src, T dest) {
    return getReturnFlowFunction(src, dest);
  }

}
