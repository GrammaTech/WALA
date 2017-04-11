package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapReturnCaller;

import java.io.IOException;

/**
 * Serializer for HeapParamCallers, prints out the call index.
 */
public class HeapReturnCallerSerializer extends StdSerializer<HeapReturnCaller> {

  protected HeapReturnCallerSerializer(Class<HeapReturnCaller> t) {
    super(t);
  }

  public HeapReturnCallerSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(HeapReturnCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, s.getClass());
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(HeapReturnCaller s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    JsonSerializer<Object> superclassSerializer = serializerProvider.findValueSerializer(s.getClass().getSuperclass());
    superclassSerializer.serialize(s, jsonGenerator, serializerProvider);
    jsonGenerator.writeNumberField("callIndex", s.getCallIndex());
  }

}
