package com.ibm.wala.dataflow.IFDS;

import com.ibm.wala.ipa.slicer.Statement.Kind;

/**
 * Interface to allow using either a SDGSupergraph or a SDGSupergraphLightweight
 * for slicing.
 *
 * @param <T>
 *          a type that corresponds to a Statement
 * @param
 *          <P>
 *          a type that corresponds to a procedure or PDG
 */
public interface ISDGSupergraph<T, P> extends ISupergraph<T, P> {

  /**
   * Obtain from the underlying SDG the exit node for the method containing s
   */
  T getMethodExitNodeForStatement(T s);

  /**
   * Obtain from the underlying SDG the entry node for the method containing s
   */
  T getMethodEntryNodeForStatement(T s);

  Kind getKind(T s);

  /**
   * Check whether the two HeapStatements first and second have the same
   * location
   */
  public boolean haveSameLocation(T first, T second);
  

}
