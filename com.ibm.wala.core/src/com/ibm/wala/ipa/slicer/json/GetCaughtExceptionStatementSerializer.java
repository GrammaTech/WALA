package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.GetCaughtExceptionStatement;
import java.io.IOException;

/**
 * Serializer for GetCaughtExceptionStatement objects, includes the
 * SSAGetCaughtExceptionInstruction.
 */
public class GetCaughtExceptionStatementSerializer extends StdSerializer<GetCaughtExceptionStatement> {

  protected GetCaughtExceptionStatementSerializer(Class<GetCaughtExceptionStatement> t) {
    super(t);
  }

  public GetCaughtExceptionStatementSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(GetCaughtExceptionStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, GetCaughtExceptionStatement.class);
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(GetCaughtExceptionStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    JsonSerializer<Object> superclassSerializer = serializerProvider.findValueSerializer(s.getClass().getSuperclass());
    superclassSerializer.serialize(s, jsonGenerator, serializerProvider);
    jsonGenerator.writeStringField("SSAGetCaughtExceptionInstruction", s.getInstruction().toString());
  }
}
