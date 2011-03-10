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
package com.ibm.wala.util.graph.impl;

/**
 * A utility class for use by clients.  Use with care ... this will be slow and a space hog.
 */
public class ExplicitEdge {
  
  final private Object src;
  final private Object dest;
  
  public ExplicitEdge(Object src, Object dest) {
    if (src == null) {
      throw new IllegalArgumentException("null src");
    }
    if (dest == null) {
      throw new IllegalArgumentException("null dest");
    }
    this.src = src;
    this.dest = dest;
  }
  @Override
  public String toString() {
    return "<" + src + "->" + dest + ">";
  }
  @Override
  public int hashCode() {
    return src.hashCode() * 947 + dest.hashCode();
  }
  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass().equals(obj.getClass())) {
      ExplicitEdge other = (ExplicitEdge)obj;
      return src.equals(other.src) && dest.equals(other.dest);
    } else {
      return false;
    }
  }
}