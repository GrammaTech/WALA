/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.slicer;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;

/**
 * Class that implements the builder pattern for SDGs. This is needed since the
 * SDG constructor takes a lot of arguments, some of which may be null or
 * computed on-demand.
 */
public class SDGBuilder<T extends InstanceKey> {

  /**
   * How many PDGs to cache by default
   */
  private final static int MAX_PDGS_TO_CACHE = 1000;

  private CallGraph cg;
  private PointerAnalysis<T> pa;
  private ModRef<T> modRef;
  private DataDependenceOptions dOptions;
  private ControlDependenceOptions cOptions;
  private HeapExclusions heapExclude = null;
  private boolean allowCacheEviction = false;
  private int maxCacheSize = MAX_PDGS_TO_CACHE;

  public SDG<T> build() {
    return new SDG<T>(cg, pa, modRef, dOptions, cOptions, heapExclude, allowCacheEviction, maxCacheSize);
  }

  public SDGBuilder<T> setCg(CallGraph cg) {
    this.cg = cg;
    return this;
  }

  public SDGBuilder<T> setPa(PointerAnalysis<T> pa) {
    this.pa = pa;
    return this;
  }

  public SDGBuilder<T> setModRef(ModRef<T> modRef) {
    this.modRef = modRef;
    return this;
  }

  public SDGBuilder<T> computeAndSetModRef(Class<T> instanceKeyClass) {
    this.modRef = ModRef.make(instanceKeyClass);
    return this;
  }

  public SDGBuilder<T> setdOptions(DataDependenceOptions dOptions) {
    this.dOptions = dOptions;
    return this;
  }

  public SDGBuilder<T> setcOptions(ControlDependenceOptions cOptions) {
    this.cOptions = cOptions;
    return this;
  }

  public SDGBuilder<T> setHeapExclude(HeapExclusions heapExclude) {
    this.heapExclude = heapExclude;
    return this;
  }

  public SDGBuilder<T> setAllowCacheEviction(boolean allowCacheEviction) {
    this.allowCacheEviction = allowCacheEviction;
    return this;
  }

  public SDGBuilder<T> setMaxCacheSize(int maxCacheSize) {
    this.maxCacheSize = maxCacheSize;
    return this;
  }

}
