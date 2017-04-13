package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;

import java.io.IOException;

/**
 * Serializer for NormalReturnCaller statements, includes the call
 * instruction index.
 */
public class NormalReturnCallerSerializer extends StdSerializer<NormalReturnCaller> {

  protected NormalReturnCallerSerializer(Class<NormalReturnCaller> t) {
    super(t);
  }

  public NormalReturnCallerSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(NormalReturnCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, NormalReturnCaller.class);
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(NormalReturnCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeNumberField("callIndex", s.getInstructionIndex());
  }

}
