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
import com.ibm.wala.ipa.slicer.ParamCaller;

import java.io.IOException;

/**
 * Serializer for ParamCaller statements, includes the value number and call
 * instruction index.
 */
public class ParamCallerSerializer extends StdSerializer<ParamCaller> {

  protected ParamCallerSerializer(Class<ParamCaller> t) {
    super(t);
  }

  public ParamCallerSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(ParamCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, ParamCaller.class);
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(ParamCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeNumberField("value", s.getValueNumber());
    jsonGenerator.writeNumberField("callIndex", s.getInstructionIndex());
  }
}