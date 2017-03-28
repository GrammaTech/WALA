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
package com.ibm.wala.ipa.modref;

import com.ibm.wala.ipa.callgraph.propagation.AbstractFieldPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.util.json.JSONObject;

/**
 * A {@link PointerKey} that represents an array length location
 */
public class ArrayLengthKey extends AbstractFieldPointerKey {

  public ArrayLengthKey(InstanceKey i) {
    super(i);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass().equals(obj.getClass())) {
      ArrayLengthKey other = (ArrayLengthKey)obj;
      return getInstanceKey().equals(other.getInstanceKey());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getInstanceKey().hashCode() * 19;
  }

  @Override
  public String toString() {
    return "arraylength:" + getInstanceKey();
  }
  
  @Override
  public JSONObject toJSON() {
    JSONObject res = new JSONObject();
    res.put("class",this.getClass().getName());
    res.put("TODO","not implemented");
    return res;
  }
}
