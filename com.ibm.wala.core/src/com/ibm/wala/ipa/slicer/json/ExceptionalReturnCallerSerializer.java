package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.ExceptionalReturnCaller;

import java.io.IOException;

/**
 * Serializer for ExceptionalReturnCaller statements, includes the call
 * instruction index.
 */
public class ExceptionalReturnCallerSerializer extends StdSerializer<ExceptionalReturnCaller> {

  protected ExceptionalReturnCallerSerializer(Class<ExceptionalReturnCaller> t) {
    super(t);
  }

  public ExceptionalReturnCallerSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(ExceptionalReturnCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, ExceptionalReturnCaller.class);
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(ExceptionalReturnCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeNumberField("callSite", s.getInstruction().getProgramCounter());
  }

}
