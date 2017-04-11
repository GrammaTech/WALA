package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;

import java.io.IOException;

/**
 * Serializer for NormalStatements; includes info on whether the instruction is
 * an invocation, needed for computing the result of
 * {@link com.ibm.wala.ipa.slicer.SDGSupergraph#isCall}
 */
public class NormalStatementSerializer extends StdSerializer<NormalStatement> {

  protected NormalStatementSerializer(Class<NormalStatement> t) {
    super(t);
  }

  public NormalStatementSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(NormalStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, s.getClass());
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(NormalStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    JsonSerializer<Object> superclassSerializer = serializerProvider.findValueSerializer(s.getClass().getSuperclass());
    superclassSerializer.serialize(s, jsonGenerator, serializerProvider);
    jsonGenerator.writeBooleanField("isAbstractInvokeInstruction",
        s.getInstruction() instanceof SSAAbstractInvokeInstruction ? true : false);
  }
}
