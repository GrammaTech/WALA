package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.Statement;

import java.io.IOException;

/**
 * Top-level serializer for {@link com.ibm.wala.ipa.slicer.Statement} objects;
 * contains logic for serializing info about the call graph node.
 * {@link #serialize(Statement, JsonGenerator, SerializerProvider)} is only
 * intended to be called by the serialize() method of a subtype.
 *
 */
public class StatementSerializer extends StdSerializer<Statement> {

  public StatementSerializer(Class<Statement> t) {
    super(t);
  }

  public StatementSerializer() {
    this(null);
  }

  @Override
  public void serialize(Statement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeFieldName("cgNode");
    jsonGenerator.writeStartObject();
    jsonGenerator.writeStringField("method", s.getNode().getMethod().getSignature());
    jsonGenerator.writeStringField("context", s.getNode().getContext().toString());
    jsonGenerator.writeEndObject();
  }
}
