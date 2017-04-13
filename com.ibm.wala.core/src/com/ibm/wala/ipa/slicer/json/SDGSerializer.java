package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.SDG;

import java.io.IOException;
import java.util.Iterator;

/**
 * Serializes an SDG to JSON. Edges between nodes in different PDGs are not
 * explicitly materialized and stored. Rather, we store enough information to
 * allow such edges to be reconstituted on demand using the logic in
 * {@link SDG#hasEdge(Object, Object)}.
 *
 */
public class SDGSerializer extends StdSerializer<SDG<? extends InstanceKey>> {

  protected SDGSerializer(Class<SDG<? extends InstanceKey>> t) {
    super(t);
  }

  protected SDGSerializer() {
    this(null);
  }

  @Override
  public void serialize(SDG<? extends InstanceKey> sdg, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeStartObject();
    jsonGenerator.writeStringField("controlDeps", sdg.getCOptions().toString());
    jsonGenerator.writeStringField("dataDeps", sdg.getDOptions().toString());
    // write info about call targets
    jsonGenerator.writeFieldName("callTargetInfo");
    jsonGenerator.writeStartObject();
    CallGraph cg = sdg.getCallGraph();
    for (CGNode cgNode : cg) {
      jsonGenerator.writeNumberField("nodeID", cg.getNumber(cgNode));
      jsonGenerator.writeFieldName("callTargets");
      jsonGenerator.writeStartObject();
      Iterator<CallSiteReference> callSiteIterator = cgNode.iterateCallSites();
      while (callSiteIterator.hasNext()) {
        CallSiteReference callSite = callSiteIterator.next();
        jsonGenerator.writeNumberField("callSite", callSite.getProgramCounter());
        jsonGenerator.writeFieldName("targetNodeIDs");
        jsonGenerator.writeStartArray();
        for (CGNode targetNode : cg.getPossibleTargets(cgNode, callSite)) {
          jsonGenerator.writeNumber(cg.getNumber(targetNode));
        }
        jsonGenerator.writeEndArray();
      }
      jsonGenerator.writeEndObject();
    }
    jsonGenerator.writeEndObject();
    // now write the PDGs
    jsonGenerator.writeFieldName("pdgs");
    jsonGenerator.writeStartObject();
    for (CGNode cgNode : cg) {
      jsonGenerator.writeNumberField("nodeID", cg.getNumber(cgNode));
      jsonGenerator.writeObjectField("pdg", sdg.getPDG(cgNode));
    }
    jsonGenerator.writeEndObject();
    jsonGenerator.writeEndObject();
  }

}
