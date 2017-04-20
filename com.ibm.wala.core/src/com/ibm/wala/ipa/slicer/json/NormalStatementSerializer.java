package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;

import java.io.IOException;

/**
 * Serializer for NormalStatements; includes info needed for computing the
 * result of {@link com.ibm.wala.ipa.slicer.SDGSupergraph#isCall} as well as for
 * computing edges between different PDGs.
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
    SSAInstruction instruction = s.getInstruction();
    boolean isAbstractInvokeInstruction = instruction instanceof SSAAbstractInvokeInstruction;
    jsonGenerator.writeBooleanField("isAbstractInvokeInstruction", isAbstractInvokeInstruction);
    if (isAbstractInvokeInstruction) {
      SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) instruction;
      jsonGenerator.writeNumberField("callSite", call.getProgramCounter());
      jsonGenerator.writeBooleanField("isDispatch", call.isDispatch());
      if (call.isDispatch()) {
        jsonGenerator.writeNumberField("receiver", call.getReceiver());
      }
      jsonGenerator.writeFieldName("parameters");
      jsonGenerator.writeStartArray();
      for (int i = 0; i < call.getNumberOfParameters(); i++) {
        jsonGenerator.writeNumber(call.getUse(i));
      }
      jsonGenerator.writeEndArray();
    }
  }
}
