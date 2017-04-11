package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;

import java.io.IOException;

/**
 * Class for serializing information about instruction indexes in Statements
 * that extend {@link com.ibm.wala.ipa.slicer.StatementWithInstructionIndex}.
 * {@link #serialize(StatementWithInstructionIndex, JsonGenerator, SerializerProvider)}
 * is only intended to be called by the serialize() method of a subtype.
 *
 */
public class StatementWithInstructionIndexSerializer extends StdSerializer<StatementWithInstructionIndex> {

  public StatementWithInstructionIndexSerializer(Class<StatementWithInstructionIndex> t) {
    super(t);
  }

  public StatementWithInstructionIndexSerializer() {
    this(null);
  }

  @Override
  public void serialize(StatementWithInstructionIndex s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    // serialize CGNode info
    JsonSerializer<Object> superclassSerializer = serializerProvider.findValueSerializer(Statement.class);
    superclassSerializer.serialize(s, jsonGenerator, serializerProvider);
    jsonGenerator.writeNumberField("instructionIndex", s.getInstructionIndex());
  }
}
