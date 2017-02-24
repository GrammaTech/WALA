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
package com.ibm.wala.analysis.reflection;

import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.util.json.JSONObject;


public class IllegalArgumentExceptionContext implements Context {

  @Override
  public ContextItem get(ContextKey name) {
    return null;
  }

  @Override
  public JSONObject toJSON() {
    JSONObject res = new JSONObject();
    res.put("class",this.getClass().toString());
    res.put("TODO","not implemented");
    return res;
  }
}
