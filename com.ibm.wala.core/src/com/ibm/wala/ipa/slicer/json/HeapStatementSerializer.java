package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapParamCaller;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapReturnCaller;

import java.io.IOException;

/**
 * Class for serializing information about locations and call indexes as
 * appropriate in statements that extend
 * {@link com.ibm.wala.ipa.slicer.HeapStatement}.
 */
public class HeapStatementSerializer extends StdSerializer<HeapStatement> {

  public HeapStatementSerializer(Class<HeapStatement> t) {
    super(t);
  }

  public HeapStatementSerializer() {
    this(null);
  }

  @Override
  public void serializeWithType(HeapStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
      TypeSerializer typeSerializer) throws IOException, JsonGenerationException {
    typeSerializer.writeTypePrefixForObject(this, jsonGenerator, s.getClass());
    serialize(s, jsonGenerator, serializerProvider);
    typeSerializer.writeTypeSuffixForObject(this, jsonGenerator);
  }

  @Override
  public void serialize(HeapStatement s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    jsonGenerator.writeNumberField("locationHashCode", s.getLocation().hashCode());
    jsonGenerator.writeNumberField("locationToStringHashCode", s.getLocation().toString().hashCode());
    if (s instanceof HeapParamCaller) {
      jsonGenerator.writeNumberField("callSite", ((HeapParamCaller) s).getCall().getProgramCounter());
    } else if (s instanceof HeapReturnCaller) {
      jsonGenerator.writeNumberField("callSite", ((HeapReturnCaller) s).getCall().getProgramCounter());
    }
  }

}
