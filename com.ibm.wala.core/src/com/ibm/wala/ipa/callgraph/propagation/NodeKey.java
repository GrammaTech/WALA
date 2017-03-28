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
package com.ibm.wala.ipa.callgraph.propagation;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.json.JSONObject;

/**
 * A key which represents a set corresponding to a call graph node.
 */
public abstract class NodeKey extends AbstractLocalPointerKey {
  private final CGNode node;
  protected NodeKey(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("null node");
    }
    this.node = node;
  }
  protected boolean internalEquals(Object obj) {
    if (obj instanceof NodeKey) {
      NodeKey other = (NodeKey) obj;
      return node.equals(other.node);
    } else {
      return false;
    }
  }
  protected int internalHashCode() {
    return node.hashCode() * 1621;
  }
  
  @Override
  public JSONObject toJSON() {
    JSONObject res = new JSONObject();
    res.put("class",this.getClass().getName());
    res.put("node",node.toJSON());
    return res;
  }
  
  /**
   * @return the node this key represents
   */
  @Override
  public CGNode getNode() {
    return node;
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

}
