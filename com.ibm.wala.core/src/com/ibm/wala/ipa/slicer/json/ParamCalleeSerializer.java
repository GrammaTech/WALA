package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.ParamCallee;
import java.io.IOException;

/**
 * Serializer for ParamCallee statements, includes the value number
 */
public class ParamCalleeSerializer extends StdSerializer<ParamCallee> {

  protected ParamCalleeSerializer(Class<ParamCallee> t) {
    super(t);
  }

  public ParamCalleeSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(ParamCallee s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, ParamCallee.class);
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(ParamCallee s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeNumberField("value", s.getValueNumber());
  }
}
