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
package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.io.IOException;
import java.util.Iterator;

/**
 * Class to serialize a PDG; respects the node numbering in the PDG itself.
 */
public class PDGSerializer extends StdSerializer<PDG<? extends InstanceKey>> {

  protected PDGSerializer(Class<PDG<? extends InstanceKey>> t) {
    super(t);
  }

  protected PDGSerializer() {
    this(null);
  }

  @Override
  public void serialize(PDG<? extends InstanceKey> pdg, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeStartObject();
    // write metadata about CG Node
    CGNode cgNode = pdg.getCallGraphNode();
    jsonGenerator.writeStringField("method", cgNode.getMethod().getSignature());
    jsonGenerator.writeStringField("context", cgNode.getContext().toString());
    jsonGenerator.writeBooleanField("isUninformativeForReflection", isUninformativeForReflection(cgNode));
    // write nodes
    jsonGenerator.writeFieldName("nodes");
    jsonGenerator.writeStartArray();
    Iterator<Statement> it = pdg.iterator();
    // print each node with the node id that it was assigned in the PDG
    while (it.hasNext()) {
      Statement node = it.next();
      jsonGenerator.writeStartObject();
      jsonGenerator.writeNumberField("id", pdg.getNumber(node));
      jsonGenerator.writeObjectField("statement", node);
      jsonGenerator.writeEndObject();
    }
    jsonGenerator.writeEndArray();
    // write edges
    jsonGenerator.writeFieldName("edges");
    jsonGenerator.writeStartArray();
    it = pdg.iterator();
    while (it.hasNext()) {
      Statement src = it.next();
      int srcNum = pdg.getNumber(src);
      Iterator<Statement> succIterator = pdg.getSuccNodes(src);
      while (succIterator.hasNext()) {
        int dstNum = pdg.getNumber(succIterator.next());
        jsonGenerator.writeStartObject();
        jsonGenerator.writeNumberField("src", srcNum);
        jsonGenerator.writeNumberField("dst", dstNum);
        jsonGenerator.writeEndObject();
      }
    }
    jsonGenerator.writeEndArray();
    jsonGenerator.writeEndObject();
  }

  /**
   * Should we cut off flow into node t when processing reflection? Copied from
   * {@link com.ibm.wala.ipa.slicer.SDG}.
   */
  private boolean isUninformativeForReflection(CGNode t) {
    if (t.getMethod().getDeclaringClass().getReference().equals(TypeReference.JavaLangReflectMethod)) {
      return true;
    }
    if (t.getMethod().getDeclaringClass().getReference().equals(TypeReference.JavaLangReflectConstructor)) {
      return true;
    }
    if (t.getMethod().getSelector().equals(MethodReference.equalsSelector)) {
      return true;
    }
    return false;
  }
}
