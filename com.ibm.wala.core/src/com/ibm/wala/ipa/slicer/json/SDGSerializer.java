package com.ibm.wala.ipa.slicer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.graph.traverse.Topological;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Serializes an SDG to JSON. All edges are explicitly materialized and stored.
 * For edge serialization, nodes are encoded as [ i, j ] where i is the id of
 * the PDG the node belongs to and j is its local id within the PDG.
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
    // write the PDGs - nodes only will be serialized
    CallGraph cg = sdg.getCallGraph();
    jsonGenerator.writeFieldName("pdgs");
    jsonGenerator.writeStartArray();
    for (CGNode cgNode : cg) {
      jsonGenerator.writeStartObject();
      jsonGenerator.writeNumberField("cgNodeId", cg.getNumber(cgNode));
      jsonGenerator.writeObjectField("pdg", sdg.getPDG(cgNode));
      jsonGenerator.writeEndObject();
    }
    jsonGenerator.writeEndArray();
    // write all edges
    jsonGenerator.writeFieldName("edges");
    jsonGenerator.writeStartArray();
    for (CGNode cgNode : Topological.makeTopologicalIter(cg)) {
      PDG<? extends InstanceKey> srcPdg = sdg.getPDG(cgNode);
      Iterator<Statement> pdgNodeIter = srcPdg.iterator();
      while (pdgNodeIter.hasNext()) {
        Statement s = pdgNodeIter.next();
        Set<Statement> interprocSuccessors = Iterator2Collection.toSet(sdg.getSuccNodes(s));
        Set<Statement> interprocPredecessors = Iterator2Collection.toSet(sdg.getPredNodes(s));
        if (!interprocSuccessors.isEmpty() || !interprocPredecessors.isEmpty()) {
          jsonGenerator.writeStartObject();
          // source node pdgId and localId
          jsonGenerator.writeFieldName("node");
          jsonGenerator.writeStartArray();
          jsonGenerator.writeNumber(cg.getNumber(cgNode));
          jsonGenerator.writeNumber(sdg.getPDG(cgNode).getNumber(s));
          jsonGenerator.writeEndArray();
          if (!interprocSuccessors.isEmpty()) {
            jsonGenerator.writeFieldName("successors");
            jsonGenerator.writeStartArray();
            for (Statement succ : interprocSuccessors) {
              jsonGenerator.writeStartArray();
              jsonGenerator.writeNumber(cg.getNumber(succ.getNode()));
              jsonGenerator.writeNumber(sdg.getPDG(succ.getNode()).getNumber(succ));
              jsonGenerator.writeEndArray();
            }
            jsonGenerator.writeEndArray();
          }
          if (!interprocPredecessors.isEmpty()) {
            jsonGenerator.writeFieldName("predecessors");
            jsonGenerator.writeStartArray();
            for (Statement pred : interprocPredecessors) {
              jsonGenerator.writeStartArray();
              jsonGenerator.writeNumber(cg.getNumber(pred.getNode()));
              jsonGenerator.writeNumber(sdg.getPDG(pred.getNode()).getNumber(pred));
              jsonGenerator.writeEndArray();
            }
            jsonGenerator.writeEndArray();
          }
          jsonGenerator.writeEndObject();
        }
      }
    }
    jsonGenerator.writeEndArray();
    jsonGenerator.writeEndObject();
  }
}
