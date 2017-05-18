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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;

import java.io.IOException;

/**
 * Serializer for statements where we need a call site and no other information,
 * namely ExceptionalReturnCaller, NormalReturnCaller and ParamCaller.
 */
public class CallSiteStatementSerializer extends StdSerializer<Statement> {

  protected CallSiteStatementSerializer(Class<Statement> t) {
    super(t);
  }

  public CallSiteStatementSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(Statement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, s.getClass());
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(Statement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    if (!(s instanceof StatementWithInstructionIndex)) {
      throw new IllegalArgumentException("StatementWithCallSiteSerializer called on statement of type " + s.getKind());
    }
    StatementWithInstructionIndex si = (StatementWithInstructionIndex) s;
    SSAInstruction instruction = si.getInstruction();
    if (!(instruction instanceof SSAAbstractInvokeInstruction)) {
      throw new IllegalArgumentException("StatementWithCallSiteSerializer called on statement with instruction " + instruction);
    }
    SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) instruction;
    jsonGenerator.writeNumberField("callSite", invoke.getProgramCounter());
  }
}