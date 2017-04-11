package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.Statement;
import java.io.IOException;

/**
 * Class for serializing information about locations in statements that extend
 * {@link com.ibm.wala.ipa.slicer.HeapStatement}.
 * {@link #serialize(HeapStatement, JsonGenerator, SerializerProvider)} is only
 * intended to be called by the serialize() method of a subtype.
 *
 */
public class HeapStatementSerializer extends StdSerializer<HeapStatement> {

  public HeapStatementSerializer(Class<HeapStatement> t) {
    super(t);
  }

  public HeapStatementSerializer() {
    this(null);
  }

  @Override
  public void serialize(HeapStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    // serialize CGNode info
    JsonSerializer<Object> superclassSerializer = serializerProvider.findValueSerializer(Statement.class);
    superclassSerializer.serialize(s, jsonGenerator, serializerProvider);
    jsonGenerator.writeStringField("loc", s.getLocation().toString());
  }

}
