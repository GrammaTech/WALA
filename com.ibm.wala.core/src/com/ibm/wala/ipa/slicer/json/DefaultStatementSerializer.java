package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.Statement;

import java.io.IOException;

/**
 * Default serialization class for those Statement types where we don't need any
 * info beyond the Statement type and the relevant data from the superclass(es),
 * such as CGNode etc.
 */
public class DefaultStatementSerializer extends StdSerializer<Statement> {

  protected DefaultStatementSerializer(Class<Statement> t) {
    super(t);
  }

  public DefaultStatementSerializer() {
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
    JsonSerializer<Object> superclassSerializer = serializerProvider.findValueSerializer(s.getClass().getSuperclass());
    superclassSerializer.serialize(s, jsonGenerator, serializerProvider);
  }

}
