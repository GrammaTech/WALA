/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.slicer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import com.ibm.wala.analysis.stackMachine.AbstractIntStackMachine;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.modref.ExtendedHeapModel;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAAbstractThrowInstruction;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.SetOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;
import com.ibm.wala.util.intset.BitVectorIntSet;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.json.JSONArray;
import com.ibm.wala.util.json.JSONObject;
import com.ibm.wala.util.json.parser.JSONParser;
import com.ibm.wala.util.json.parser.ParseException;

/**
 * Program dependence graph for a single call graph node
 */
public class PDG<T extends InstanceKey> implements NumberedGraph<Statement> {

  /** BEGIN Custom change: control deps */
  public enum Dependency {CONTROL_DEP, DATA_DEP, DATA_AND_CONTROL_DEP};

  private SlowSparseNumberedLabeledGraph<Statement, Dependency> delegate =
      new SlowSparseNumberedLabeledGraph<Statement, Dependency>(Dependency.DATA_DEP);

  private SlowSparseNumberedLabeledGraph<Statement, Dependency> elided =
      new SlowSparseNumberedLabeledGraph<Statement, Dependency>(Dependency.DATA_DEP);
  /** END Custom change: control deps */

  private final static boolean VERBOSE = false;

  private static final boolean EXTRA_EDGES = false;

  private static final boolean INLINE_RESOLVABLE_CALLS = false;

  private static final boolean USE_JSON_SUMMARIES = false;

  private IR ir;

  private final CGNode node;

  private Statement[] paramCalleeStatements;

  private Statement[] returnStatements;

  /**
   * TODO: using CallSiteReference is sloppy. clean it up.
   */

  private final Map<CallSiteReference, Statement> callSite2Statement = HashMapFactory.make();

  private final Map<CallSiteReference, Set<Statement>> callerParamStatements = HashMapFactory.make();

  private final Map<CallSiteReference, Set<Statement>> callerReturnStatements = HashMapFactory.make();

  private final HeapExclusions exclusions;

  private final Collection<PointerKey> locationsHandled = HashSetFactory.make();

  private final PointerAnalysis<T> pa;

  private final ExtendedHeapModel heapModel;

  private final Map<CGNode, OrdinalSet<PointerKey>> mod;

  private final DataDependenceOptions dOptions;

  private final ControlDependenceOptions cOptions;

  private final CallGraph cg;

  private final ModRef modRef;

  private final Map<CGNode, OrdinalSet<PointerKey>> ref;

  private final boolean ignoreAllocHeapDefs;

  private boolean isPopulated = false;

  private boolean useSummaries;

  private int inlineThreshold;

  private final Set<Statement> IncomingHeapDependenciesComputed;
  private final Set<Statement> OutgoingHeapDependenciesComputed;

  /**
   * @param mod the set of heap locations which may be written (transitively) by this node. These are logically return values in the
   *          SDG.
   * @param ref the set of heap locations which may be read (transitively) by this node. These are logically parameters in the SDG.
   * @throws IllegalArgumentException if node is null
   */
  public PDG(final CGNode node, PointerAnalysis<T> pa, Map<CGNode, OrdinalSet<PointerKey>> mod,
      Map<CGNode, OrdinalSet<PointerKey>> ref, DataDependenceOptions dOptions, ControlDependenceOptions cOptions,
      HeapExclusions exclusions, CallGraph cg, ModRef modRef, boolean useSummaries, int inlineThreshold) {
    this(node, pa, mod, ref, dOptions, cOptions, exclusions, cg, modRef, false, useSummaries, inlineThreshold);
  }

  /**
   * @param mod the set of heap locations which may be written (transitively) by this node. These are logically return values in the
   *          SDG.
   * @param ref the set of heap locations which may be read (transitively) by this node. These are logically parameters in the SDG.
   * @throws IllegalArgumentException if node is null
   */
  public PDG(final CGNode node, PointerAnalysis<T> pa, Map<CGNode, OrdinalSet<PointerKey>> mod,
      Map<CGNode, OrdinalSet<PointerKey>> ref, DataDependenceOptions dOptions, ControlDependenceOptions cOptions,
      HeapExclusions exclusions, CallGraph cg, ModRef modRef, boolean ignoreAllocHeapDefs, boolean useSummaries, int inlineThreshold) {

    super();
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    this.cg = cg;
    this.node = node;
    this.heapModel = pa != null? modRef.makeHeapModel(pa): null;
    this.pa = pa;
    this.dOptions = dOptions;
    this.cOptions = cOptions;
    this.mod = mod;
    this.exclusions = exclusions;
    this.modRef = modRef;
    this.ref = ref;
    this.ignoreAllocHeapDefs = ignoreAllocHeapDefs;
    this.inlineThreshold = inlineThreshold;
    this.useSummaries = useSummaries;
    this.IncomingHeapDependenciesComputed = new HashSet<Statement>();
    this.OutgoingHeapDependenciesComputed = new HashSet<Statement>();
    this.ir = node.getIR();
  }

  /**
   * WARNING: Since we're using a {@link HashMap} of {@link SSAInstruction}s, and equals() of {@link SSAInstruction} assumes a
   * canonical representative for each instruction, we <bf>must</bf> ensure that we use the same IR object throughout
   * initialization!!
   * @throws FileNotFoundException
   */
  private void populate() {
    if (!isPopulated) {
      populate_nosimplify();
      simplify();
    }
  }

  private void populateFromJSON(String summary) {
    JSONParser jsonParser = new JSONParser();
    JSONObject jsonSummary = null;
    try {
      jsonSummary = (JSONObject) jsonParser.parse(new FileReader(summary));
    } catch (IOException | ParseException e) {
      e.printStackTrace();
    }
    Map<Integer,Statement> idToStatement = new HashMap<Integer,Statement>();
    JSONArray statements = (JSONArray) jsonSummary.get("statements");

    List<Statement> paramCalleeList = new ArrayList<Statement>();
    List<Statement> returnList = new ArrayList<Statement>();

    Map<String,IMethod> SignatureToIMethod = new HashMap<String,IMethod>();
    Iterator<CGNode> nodesIt = cg.iterator();
    while (nodesIt.hasNext()) {
      CGNode n = nodesIt.next();
      String s = n.getMethod().getSignature();
      SignatureToIMethod.put(s, n.getMethod());
    }

    for (Object o : statements) {
      JSONObject statement = (JSONObject) o;
      Statement s = null;
      SSAAbstractInvokeInstruction invoke = null;
      Collection<Statement> rets = null;

      JSONObject jsonCGNode = (JSONObject) statement.get("CGNode");
      JSONObject jsonIMethod = (JSONObject) jsonCGNode.get("method");
      JSONObject jsonContext = (JSONObject) jsonCGNode.get("context");

      CGNode curNode = cg.getNode(SignatureToIMethod.get(jsonIMethod.get("signature")), Everywhere.EVERYWHERE);

      switch ((String) statement.get("Kind")) {
      case "NORMAL":
        s = new NormalStatement(curNode, ((Long)statement.get("instructionIndex")).intValue());
        SSAInstruction inst = ((NormalStatement)s).getInstruction();
        if (inst instanceof SSAAbstractInvokeInstruction) {
          CallSiteReference cs = ((SSAAbstractInvokeInstruction)inst).getCallSite();
          callSite2Statement.put(cs, s);
          MapUtil.findOrCreateSet(callerParamStatements, cs);
          MapUtil.findOrCreateSet(callerReturnStatements, cs);
        }
        break;
      case "PARAM_CALLEE":
        s = new ParamCallee(curNode, ((Long)statement.get("valueNumber")).intValue());
        paramCalleeList.add(s);
        break;
      case "NORMAL_RET_CALLEE":
        s = new NormalReturnCallee(curNode);
        returnList.add(s);
        break;
      case "PARAM_CALLER":
        s = new ParamCaller(curNode, ((Long)statement.get("instructionIndex")).intValue(), ((Long)statement.get("valueNumber")).intValue());
        invoke =((ParamCaller)s).getInstruction();
        Collection<Statement> params = MapUtil.findOrCreateSet(callerParamStatements, invoke.getCallSite());
        params.add(s);
        break;
      case "NORMAL_RET_CALLER":
        s = new NormalReturnCaller(curNode, ((Long)statement.get("instructionIndex")).intValue());
        invoke = ((NormalReturnCaller)s).getInstruction();
        rets = MapUtil.findOrCreateSet(callerReturnStatements, invoke.getCallSite());
        rets.add(s);
        break;
      case "EXC_RET_CALLER":
        s = new ExceptionalReturnCaller(curNode, ((Long)statement.get("instructionIndex")).intValue());
        invoke = ((ExceptionalReturnCaller)s).getInstruction();
        rets = MapUtil.findOrCreateSet(callerReturnStatements, invoke.getCallSite());
        rets.add(s);
        break;
      case "EXC_RET_CALLEE":
        s = new ExceptionalReturnCallee(curNode);
        returnList.add(s);
        break;
      case "METHOD_ENTRY":
        s = new MethodEntryStatement(curNode);
        break;
      case "METHOD_EXIT":
        s = new MethodExitStatement(curNode);
        break;
      default:
        throw new IllegalArgumentException("JSON parsing not implemented " + (String) statement.get("Kind"));
      }
      delegate.addNode(s);
      idToStatement.put(((Long)statement.get("id")).intValue(), s);
    }

    paramCalleeStatements = new Statement[paramCalleeList.size()];
    paramCalleeList.toArray(paramCalleeStatements);
    returnStatements = new Statement[returnList.size()];
    returnList.toArray(returnStatements);

    JSONArray dataDeps = (JSONArray) jsonSummary.get("datadeps");
    for (Object o : dataDeps) {
      JSONObject dep = (JSONObject) o;
      int src = ((Long)dep.get("src")).intValue();
      int dst = ((Long)dep.get("dst")).intValue();
      delegate.addEdge(idToStatement.get(src), idToStatement.get(dst), Dependency.DATA_DEP);
    }
    JSONArray controlDeps = (JSONArray) jsonSummary.get("controldeps");
    for (Object o : controlDeps) {
      JSONObject dep = (JSONObject) o;
      int src = ((Long)dep.get("src")).intValue();
      int dst = ((Long)dep.get("dst")).intValue();
      delegate.addEdge(idToStatement.get(src), idToStatement.get(dst), Dependency.CONTROL_DEP);
    }
    try {
      DumpDotPDG(delegate, "reconstructed_pdg_" + node.getMethod().getDeclaringClass().getName().toString().replaceAll("/", ".") + "." + node.getMethod().getName().toString() + ".dot");
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  private void populate_nosimplify() {
    if (!isPopulated) {
      // ensure that we keep the single, canonical IR live throughout initialization, while the instructionIndices map
      // is live.
      //IR ir = node.getIR();
      isPopulated = true;

      String summary = getJSONFileName();
      File f = new File(summary);
      if (USE_JSON_SUMMARIES && f.exists() && node.getContext() instanceof Everywhere) {
        System.err.println("building PDG from summary " + summary + " in context " + node.getContext());
        populateFromJSON(summary);
        return;
      }

      Map<SSAInstruction, Integer> instructionIndices = computeInstructionIndices(ir);
      createNodes(ref, cOptions, ir);
      createScalarEdges(cOptions, ir, instructionIndices);


      if (pa != null) {
        Iterator<Statement> it = delegate.iterator();
        while (it.hasNext()) {
          Statement stmt = it.next();
          computeIncomingHeapDependencies(stmt);
          computeOutgoingHeapDependencies(stmt);
        }
      }
    }
  }

  private void simplify() {
    if (useSummaries && ClassLoaderReference.Primordial.equals(
        node.getMethod().getDeclaringClass().getClassLoader().getReference())) {
      try {
        DumpDotPDG(delegate, "pdg_" + node.getMethod().getDeclaringClass().getName().toString().replaceAll("/", ".") + "." + node.getMethod().getName().toString() + ".dot");
        if (INLINE_RESOLVABLE_CALLS) {
          inlineResolvableCalls(inlineThreshold);
          DumpDotPDG(delegate, "inlined_pdg_" + node.getMethod().getDeclaringClass().getName().toString().replaceAll("/", ".") + "." + node.getMethod().getName().toString() + ".dot");
        }
        computeElided();
        if (node.getContext() instanceof Everywhere) {
          DumpDotPDG(elided, "elided_pdg_" + node.getMethod().getDeclaringClass().getName().toString().replaceAll("/", ".") + "." + node.getMethod().getName().toString() + ".dot");
          DumpSummary(elided);
        }
        delegate = elided;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean isStaticallyResolvable(CallSiteReference cs) {
    boolean resolvable = false;
    MethodReference mref = cs.getDeclaredTarget();
    Set<IMethod> methods = cg.getClassHierarchy().getPossibleTargets(mref);
    if (methods.size() == 1) {
      IMethod m = methods.iterator().next();
      if (m instanceof SyntheticMethod) {
        return false;
      }
      if (ClassLoaderReference.Application.equals(
          m.getDeclaringClass().getClassLoader().getReference())) {
        return false;
      }
      // TODO should return true also if the class is marked final
      if (cs.isStatic()
          || m.isPrivate()
          || m.isFinal()
          || m.isInit()
          || m.isClinit()
          || m.getDeclaringClass().isArrayClass()
          || m.getDeclaringClass().isPrivate()
          || m.getDeclaringClass().getName().toString().equals("Ljava/lang/String")
          ) {
        resolvable = true;
      }
    }
    return resolvable;
  }

  private void inlineResolvableCalls(int threshold) {
    if (threshold <= 0) {
      return;
    }
    List<Pair<CallSiteReference,Integer>> worklist = new ArrayList<Pair<CallSiteReference,Integer>>();
    for (CallSiteReference cs : callSite2Statement.keySet()) {
      worklist.add(Pair.make(cs,threshold));
    }
    while (!worklist.isEmpty()) {
      Pair<CallSiteReference,Integer> p = worklist.remove(0);
      CallSiteReference cs = p.fst;
      if (isStaticallyResolvable(cs) && p.snd > 0) {
        Statement cs_stmt = callSite2Statement.get(cs);
        Set<CGNode> cgnode = cg.getNodes(cs.getDeclaredTarget());
        if (cgnode.size() != 1) { continue; }
        cs_stmt.isKeyNode = false;
        CGNode node = cgnode.iterator().next();

        PDG<T> pdg = new PDG<T>(node, pa, mod, ref, dOptions, cOptions, exclusions, cg, modRef, true, p.snd-1);
        pdg.populate_nosimplify();
        // adding all nodes and edges from the pdg
        Iterator<Statement> stmts = pdg.delegate.iterator();
        while (stmts.hasNext()) {
          Statement stmt = stmts.next();
          switch (stmt.getKind()) {
          case METHOD_ENTRY:
          case METHOD_EXIT:
            stmt.isKeyNode = false;
          default:
            delegate.addNode(stmt);
            //pdg.computeIncomingHeapDependencies(stmt);
            //pdg.computeOutgoingHeapDependencies(stmt);
          }
        }
        callerParamStatements.putAll(pdg.callerParamStatements);
        callerReturnStatements.putAll(pdg.callerReturnStatements);

        for (CallSiteReference pdg_cs : pdg.callSite2Statement.keySet()) {
          if (callSite2Statement.containsKey(pdg_cs)) {
            System.err.println("callsite " + pdg_cs + " already exists");
          }
          callSite2Statement.put(pdg_cs,pdg.callSite2Statement.get(pdg_cs));
          // TODO : fix this by changing the HashMaps <CallSiteReference, ...> (e.g. callSite2Statement)
          // inlining might create multiple invoke statements with the same callSiteReference and it screws up the hashmaps
          //worklist.add(Pair.make(pdg_cs,p.snd-1));
        }

        stmts = pdg.iterator();
        while (stmts.hasNext()) {
          Statement stmt = stmts.next();
          Iterator<Statement> succs = pdg.delegate.getSuccNodes(stmt);
          while (succs.hasNext()) {
            Statement succ = succs.next();
            if (!delegate.containsNode(succ)) {
              delegate.addNode(succ);
            }
            Set<Dependency> dependencies = (Set<Dependency>)delegate.getEdgeLabels(stmt, succ);
            for (Dependency d : dependencies) {
              delegate.addEdge(stmt, succ, d);
            }
          }
        }

        // linking caller params and callee params
        Statement[] ParamCallerSet = callerParamStatements.get(cs).toArray(new Statement[0]);
        Statement[] ParamCalleeSet = pdg.getParamCalleeStatements();

        for (int i = 0; i < ParamCalleeSet.length; i++) {
          int valueNumber = ((ParamCallee)ParamCalleeSet[i]).valueNumber;
          for (int j = 0; j < ParamCallerSet.length; j++) {
            if (valueNumber == ((ParamCaller)ParamCallerSet[j]).valueNumber) {
              delegate.addEdge(cs_stmt, ParamCallerSet[j], Dependency.DATA_DEP);
              ParamCallerSet[j].isKeyNode = false;
              ParamCalleeSet[i].isKeyNode = false;
              delegate.addEdge(ParamCallerSet[j], ParamCalleeSet[i], Dependency.DATA_DEP);
            }
          }
        }

        // linking callee return to caller return
        Statement[] ReturnCallerSet = callerReturnStatements.get(cs).toArray(new Statement[0]);
        Statement[] ReturnCalleeSet = pdg.getReturnStatements();

        for (int i = 0; i < ReturnCalleeSet.length; i++) {
          for (int j = 0; j < ReturnCallerSet.length; j++) {
            if ((ReturnCallerSet[j] instanceof NormalReturnCaller && ReturnCalleeSet[i] instanceof NormalReturnCallee)
                || (ReturnCallerSet[j] instanceof ExceptionalReturnCaller && ReturnCalleeSet[i] instanceof ExceptionalReturnCallee)) {
              delegate.addEdge(cs_stmt, ReturnCallerSet[j], Dependency.DATA_DEP);
              ReturnCallerSet[j].isKeyNode = false;
              ReturnCalleeSet[i].isKeyNode = false;
              delegate.addEdge(ReturnCalleeSet[i], ReturnCallerSet[j], Dependency.DATA_DEP);
            }
          }
        }

        callSite2Statement.remove(cs);
        callerParamStatements.remove(cs);
        callerReturnStatements.remove(cs);
      }
    }
  }

  private void computeElided() {
    Iterator<Statement> it = delegate.iterator();
    Set<Statement> keyNodes = new HashSet<Statement>();
    while (it.hasNext()) {
      Statement stmt = it.next();
      if (stmt.isKeyNode) {
        keyNodes.add(stmt);
        elided.addNode(stmt);
      }
    }
    for (Statement key : keyNodes) {
      for (Entry<Statement, Set<? extends Dependency>> dst: elidedFlow(key,keyNodes).entrySet()) {
        for (Dependency dep : dst.getValue()) {
          elided.addEdge(key, dst.getKey(), dep);
        }
      }
    }
  }

  private Map<Statement,Set<? extends Dependency>> elidedFlow(Statement key, Set<Statement> keyNodes) {
    Map<Statement,Set<? extends Dependency>> result = new HashMap<Statement,Set<? extends Dependency>>();
    Set<Statement> seen = new HashSet<Statement>();
    Queue<Pair<Statement,Set<Dependency>>> frontier = new LinkedList<Pair<Statement,Set<Dependency>>>();
    Set<Dependency> d = new HashSet<Dependency>();
    frontier.add(Pair.make(key,d));
    while (!frontier.isEmpty()) {
      Pair<Statement,Set<Dependency>> f = frontier.remove();
      seen.add(f.fst);

      Iterator<? extends Statement> succs = delegate.getSuccNodes(f.fst);
      while (succs.hasNext()) {
        Statement succ = succs.next();
        Set<Dependency> dependencies = (Set<Dependency>)delegate.getEdgeLabels(f.fst, succ);
        dependencies.addAll(f.snd);
        if (succ.isKeyNode) {
          result.put(succ, dependencies);
        } else if (!seen.contains(succ)){
          frontier.add(Pair.make(succ,dependencies));
        }
      }
    }
    return result;
  }

  private String getJSONFileName() {
    return "summary_" + node.getMethod().getSignature().replaceAll("/", ".") + ".json";
  }

  private void DumpSummary(SlowSparseNumberedLabeledGraph<Statement, Dependency> graph) throws IOException {
    String filename = getJSONFileName();

    JSONArray statements = new JSONArray();
    JSONArray ControlDeps = new JSONArray();
    JSONArray DataDeps = new JSONArray();

    Map<Statement,Integer> statementsIds = new HashMap<Statement,Integer>();

    Iterator<Statement> it = graph.iterator();
    int i = 0;
    while (it.hasNext()) {
      Statement stmt = it.next();
      statementsIds.put(stmt, i);
      JSONObject jsonStmt = stmt.toJSON();
      jsonStmt.put("id", i);
      statements.add(jsonStmt);
      i++;
    }

    it = graph.iterator();
    while (it.hasNext()) {
      Statement stmt = it.next();

      Iterator<Statement> succs = graph.getSuccNodes(stmt);
      while (succs.hasNext()) {
        Statement succ = succs.next();
        Set<? extends Dependency> labels = graph.getEdgeLabels(stmt, succ);
        JSONObject jsonEdge = new JSONObject();
        jsonEdge.put("src", statementsIds.get(stmt));
        jsonEdge.put("dst", statementsIds.get(succ));
        if (labels.contains(Dependency.DATA_DEP) || labels.contains(Dependency.DATA_AND_CONTROL_DEP)) {
          DataDeps.add(jsonEdge);
        }
        if (labels.contains(Dependency.CONTROL_DEP) || labels.contains(Dependency.DATA_AND_CONTROL_DEP)) {
          ControlDeps.add(jsonEdge);
        }
      }
    }

    PrintWriter writer = null;
    writer = new PrintWriter(filename);
    JSONObject json = new JSONObject();
    json.put("statements", statements);
    json.put("datadeps", DataDeps);
    json.put("controldeps", ControlDeps);
    json.writeJSONString(writer);
    writer.close();
  }

  private void DumpDotPDG(SlowSparseNumberedLabeledGraph<Statement, Dependency> graph, String filename) throws FileNotFoundException {
    PrintWriter writer = null;
    writer = new PrintWriter(filename);
    String NEWLINE = "&#10;";

    writer.println("digraph reachability_info {");

    HashMap<Statement, Integer> numbering = new HashMap<Statement, Integer>();
    int nodeNumber = 1;
    Iterator<Statement> it = graph.iterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      numbering.put(stmt, nodeNumber++);
    }

    it = graph.iterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      String args = "shape=circle";
      String color = "d3d7cf";
      String transparency = "DD";
      String style = "filled";
      String tooltip = stmt.toString();
      if (cg.getClassHierarchy()
          .getScope()
          .isApplicationClass(stmt.getNode().getMethod().getDeclaringClass())) {
        transparency = "DD";
      } else {
      }
      switch (stmt.getKind()) {
      case NORMAL:
        color = "d3d7cf";
        NormalStatement nstmt = (NormalStatement) stmt;
        if (nstmt.getInstruction() instanceof SSAAbstractInvokeInstruction) {
          color = "c19a6b";
        }
        if (nstmt.getInstruction() instanceof SSAFieldAccessInstruction) {
          SSAFieldAccessInstruction s = (SSAFieldAccessInstruction)nstmt.getInstruction();
          if (heapModel != null && s.getRef() > 0) {
            tooltip += NEWLINE + "PointerKey:::" + heapModel.getPointerKeyForLocal(node, s.getRef());
            Set<PointerKey> pkeys = modRef.getRef(node, heapModel, pa, s, exclusions);
            tooltip += NEWLINE + NEWLINE + "Result of getRef:";
            for (PointerKey p : pkeys) {
              tooltip += NEWLINE + p;
            }
          }
          if (heapModel != null && s.getRef() > 0) {
            Set<PointerKey> pkeys = modRef.getMod(node, heapModel, pa, s, exclusions);
            tooltip += NEWLINE + NEWLINE + "Result of getMod:";
            for (PointerKey p : pkeys) {
              tooltip += NEWLINE + p;
            }
          }
          color = "4997d0";
        }
        if (nstmt.getInstruction() instanceof SSANewInstruction) {
          if (heapModel != null) {
            SSANewInstruction s = (SSANewInstruction)nstmt.getInstruction();
            InstanceKey i = heapModel.getInstanceKeyForAllocation(node, s.getNewSite());
            tooltip += NEWLINE + NEWLINE + "InstanceKey:" + NEWLINE + i;
            color = "fad6a5";
          }
        }
        break;
      case PHI:
      case PI:
        color = "d3d7cf";
        break;
      case PARAM_CALLEE:
        ParamCallee pcallee = (ParamCallee)stmt;
        if (heapModel != null) {
          tooltip += NEWLINE + "PointerKey::" + NEWLINE + heapModel.getPointerKeyForLocal(node, pcallee.valueNumber);
        }
        style += ",dashed";
        color = "e9d66b";
        break;
      case PARAM_CALLER:
        ParamCaller pcaller = (ParamCaller)stmt;
        if (heapModel != null) {
          tooltip += NEWLINE + "PointerKey::" + NEWLINE + heapModel.getPointerKeyForLocal(node, pcaller.valueNumber);
        }
        color = "e9d66b";
        break;
      case NORMAL_RET_CALLEE:
        if (heapModel != null) {
          tooltip += NEWLINE + "PointerKey::" + NEWLINE + heapModel.getPointerKeyForReturnValue(node);
        }
        style += ",dashed";
        color = "e9d66b";
        break;
      case NORMAL_RET_CALLER:
        NormalReturnCaller nretcaller = (NormalReturnCaller) stmt;
        if (heapModel != null) {
          tooltip += NEWLINE + "PointerKey::" + NEWLINE + heapModel.getPointerKeyForReturnValue(nretcaller.getNode());
        }
        color = "e9d66b";
        break;
      case EXC_RET_CALLEE:
        style += ",dashed";
      case EXC_RET_CALLER:
      case CATCH:
        color = "e32636";
        break;
      case HEAP_PARAM_CALLEE:
      case HEAP_RET_CALLEE:
        style += ",dashed";
      case HEAP_PARAM_CALLER:
      case HEAP_RET_CALLER:
        color = "915c83";
        HeapStatement hstmt = (HeapStatement) stmt;
        tooltip += NEWLINE + "PointerKey:::" + hstmt.getLocation();
        break;
      case METHOD_ENTRY:
      case METHOD_EXIT:
        color = "8db600";
        break;
      }
      args += ",style=\"" + style + "\",fillcolor=\"#" + color + transparency + "\"";
      args +=
          ",tooltip=\""
              + tooltip
              + "\"";
      writer.println(numbering.get(stmt) + "[" + args + "]");
    }

    it = graph.iterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      Iterator<Statement> succs = graph.getSuccNodes(stmt);
      while (succs.hasNext()) {
        Statement succ = succs.next();
        Set<? extends Dependency> labels = graph.getEdgeLabels(stmt, succ);
        String args = "";
        if (labels.contains(Dependency.DATA_DEP) || labels.contains(Dependency.DATA_AND_CONTROL_DEP)) {
          args += "color=blue,";
        }
        if (!labels.contains(Dependency.CONTROL_DEP) && !labels.contains(Dependency.DATA_AND_CONTROL_DEP)) {
          args += "style=dotted,";
        }
        args += "penwidth=1.0";
        writer.println(numbering.get(stmt) + " -> " + numbering.get(succ) + "[" + args + "]");

      }
    }

    writer.println("}");
    writer.close();
  }

  private void createScalarEdges(ControlDependenceOptions cOptions, IR ir, Map<SSAInstruction, Integer> instructionIndices) {
    createScalarDataDependenceEdges(ir, instructionIndices);
    createControlDependenceEdges(cOptions, ir, instructionIndices);
  }

  /**
   * return the set of all PARAM_CALLER and HEAP_PARAM_CALLER statements associated with a given call
   */
  public Set<Statement> getCallerParamStatements(SSAAbstractInvokeInstruction call) throws IllegalArgumentException {
    if (call == null) {
      throw new IllegalArgumentException("call == null");
    }
    populate();
    return callerParamStatements.get(call.getCallSite());
  }

  /**
   * return the set of all PARAM_CALLER, HEAP_PARAM_CALLER, and NORMAL statements (i.e., the actual call statement) associated with
   * a given call
   */
  public Set<Statement> getCallStatements(SSAAbstractInvokeInstruction call) throws IllegalArgumentException {
    populate();
    if (!callSite2Statement.containsKey(call.getCallSite())) {
      return new HashSet<Statement>();
    }
    Set<Statement> callerParamStatements = getCallerParamStatements(call);
    Set<Statement> result = HashSetFactory.make(callerParamStatements.size() + 1);
    result.addAll(callerParamStatements);

    result.add(callSite2Statement.get(call.getCallSite()));
    return result;
  }

  public Map<CallSiteReference,Statement> getCallSiteStatementsMap() {
    populate();
    return callSite2Statement;
  }

  /**
   * return the set of all NORMAL_RETURN_CALLER and HEAP_RETURN_CALLER statements associated with a given call.
   */
  public Set<Statement> getCallerReturnStatements(SSAAbstractInvokeInstruction call) throws IllegalArgumentException {
    populate();
    if (call == null) {
      throw new IllegalArgumentException("call == null");
    }
    populate();
    if (!callerReturnStatements.containsKey(call.getCallSite())) {
      System.err.println("PDG does not contain callsite " + node + " " + call);
      throw new IllegalArgumentException("PDG does not contain callsite " + node + " " + call);
    }
    return callerReturnStatements.get(call.getCallSite());
  }

  /**
   * Create all control dependence edges in this PDG.
   */
  private void createControlDependenceEdges(ControlDependenceOptions cOptions, IR ir,
      Map<SSAInstruction, Integer> instructionIndices) {
    if (cOptions.equals(ControlDependenceOptions.NONE)) {
      return;
    }
    if (ir == null) {
      return;
    }
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> controlFlowGraph = ir.getControlFlowGraph();
    if (cOptions.equals(ControlDependenceOptions.NO_EXCEPTIONAL_EDGES)) {
      PrunedCFG<SSAInstruction, ISSABasicBlock> prunedCFG = ExceptionPrunedCFG.make(controlFlowGraph);
      // In case the CFG has only the entry and exit nodes left 
      // and no edges because the only control dependencies
      // were exceptional, simply return because at this point there are no nodes.
      // Otherwise, later this may raise an Exception.
      if (prunedCFG.getNumberOfNodes() == 2 
          && prunedCFG.containsNode(controlFlowGraph.entry()) 
          && prunedCFG.containsNode(controlFlowGraph.exit())
          && GraphUtil.countEdges(prunedCFG) == 0) {
        return;
      }
      controlFlowGraph = prunedCFG;
    } else {
      Assertions.productionAssertion(cOptions.equals(ControlDependenceOptions.FULL));
    }

    ControlDependenceGraph<ISSABasicBlock> cdg = new ControlDependenceGraph<ISSABasicBlock>(
        controlFlowGraph);
    for (ISSABasicBlock bb : cdg) {
      if (bb.isExitBlock()) {
        // nothing should be control-dependent on the exit block.
        continue;
      }

      Statement src = null;
      if (bb.isEntryBlock()) {
        src = new MethodEntryStatement(node);
      } else {
        SSAInstruction s = ir.getInstructions()[bb.getLastInstructionIndex()];
        if (s == null) {
          // should have no control dependent successors.
          // leave src null.
        } else {
          src = ssaInstruction2Statement(s, ir, instructionIndices);
          // add edges from call statements to parameter passing and return
          // SJF: Alexey and I think that we should just define ParamStatements
          // as
          // being control dependent on nothing ... they only represent pure
          // data dependence. So, I'm commenting out the following.
          if (EXTRA_EDGES) {
            if (s instanceof SSAAbstractInvokeInstruction) {
              SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction)
                  s;
              for (Statement st : callerParamStatements.get(call.getCallSite()))
              {
                // JULIEN : switched st <-> src
                delegate.addEdge(st, src, Dependency.CONTROL_DEP);

              }
              for (Statement st : callerReturnStatements.get(call.getCallSite()))
              {
                delegate.addEdge(src, st, Dependency.CONTROL_DEP);
              }
            }
          }

        }
      }
      // add edges for every control-dependent statement in the IR, if there are
      // any
      // control-dependent successors
      if (src != null) {
        for (Iterator<? extends ISSABasicBlock> succ = cdg.getSuccNodes(bb); succ.hasNext();) {
          ISSABasicBlock bb2 = succ.next();
          for (Iterator<SSAInstruction> it2 = bb2.iterator(); it2.hasNext();) {
            SSAInstruction st = it2.next();
            if (st != null) {
              Statement dest = ssaInstruction2Statement(st, ir, instructionIndices);
              assert src != null;
              //delegate.addEdge(src, dest, Dependency.DATA_DEP);
              /** BEGIN Custom change: control deps */
              delegate.addEdge(src, dest, Dependency.CONTROL_DEP);
              /** END Custom change: control deps */
            }
          }
        }
      }
    }

    // the CDG does not represent control dependences from the entry node.
    // add these manually
    // We add control dependences to all instructions in all basic blocks B that _must_ execute.
    // B is the set of blocks that dominate the exit basic block
    Statement methodEntry = new MethodEntryStatement(node);
    Dominators<ISSABasicBlock> dom = Dominators.make(controlFlowGraph, controlFlowGraph.entry());
    for (ISSABasicBlock exitDom : Iterator2Iterable.make(dom.dominators(controlFlowGraph.exit()))) {
      for (SSAInstruction st : exitDom) {
        Statement dest = ssaInstruction2Statement(st, ir, instructionIndices);
        //delegate.addEdge(methodEntry, dest, Dependency.DATA_DEP);
        /** BEGIN Custom change: control deps */
        delegate.addEdge(methodEntry, dest, Dependency.CONTROL_DEP);
        /** END Custom change: control deps */
      }
    }
    // add CD from method entry to all callee parameter assignments
    // SJF: Alexey and I think that we should just define ParamStatements as
    // being control dependent on nothing ... they only represent pure
    // data dependence. So, I'm commenting out the following.
    if (EXTRA_EDGES) {
      for (int i = 0; i < paramCalleeStatements.length; i++) {
        delegate.addEdge(methodEntry, paramCalleeStatements[i], Dependency.CONTROL_DEP);
      }
    }
    /**
     * JTD: While phi nodes live in a particular basic block, they represent a meet of values from multiple blocks. Hence, they are
     * really like multiple statements that are control dependent in the manner of the predecessor blocks. When the slicer is
     * following both data and control dependences, it therefore seems right to add control dependence edges to represent how a phi
     * node depends on predecessor blocks.
     */
    if (!dOptions.equals(DataDependenceOptions.NONE)) {
      for (ISSABasicBlock bb : cdg) {
        for (Iterator<SSAPhiInstruction> ps = bb.iteratePhis(); ps.hasNext();) {
          SSAPhiInstruction phi = ps.next();
          Statement phiSt = ssaInstruction2Statement(phi, ir, instructionIndices);
          int phiUseIndex = 0;
          for (Iterator<? extends ISSABasicBlock> preds = controlFlowGraph.getPredNodes(bb); preds.hasNext();) {
            ISSABasicBlock pb = preds.next();
            int use = phi.getUse(phiUseIndex);
            if (use == AbstractIntStackMachine.TOP) {
              // the predecessor is part of some infeasible bytecode. we probably don't want slices to include such code, so ignore.
              continue;
            }
            if (controlFlowGraph.getSuccNodeCount(pb) > 1) {
              // in this case, there is more than one edge from the
              // predecessor block, hence the phi node actually
              // depends on the last instruction in the previous
              // block, rather than having the same dependences as
              // statements in that block.
              SSAInstruction pss = ir.getInstructions()[pb.getLastInstructionIndex()];
              assert pss != null;
              Statement pst = ssaInstruction2Statement(pss, ir, instructionIndices);
              //delegate.addEdge(pst, phiSt, Dependency.DATA_DEP);
              /** BEGIN Custom change: control deps */
              delegate.addEdge(pst, phiSt, Dependency.CONTROL_DEP);
              /** END Custom change: control deps */
            } else {
              for (Iterator<? extends ISSABasicBlock> cdps = cdg.getPredNodes(pb); cdps.hasNext();) {
                ISSABasicBlock cpb = cdps.next();
                /** BEGIN Custom change: control deps */
                if (cpb.getLastInstructionIndex() < 0) {
                  continue;
                }
                /** END Custom change: control deps */
                SSAInstruction cps = ir.getInstructions()[cpb.getLastInstructionIndex()];
                assert cps != null : "unexpected null final instruction for CDG predecessor " + cpb + " in node " + node;
                Statement cpst = ssaInstruction2Statement(cps, ir, instructionIndices);
                //delegate.addEdge(cpst, phiSt, Dependency.DATA_DEP);
                /** BEGIN Custom change: control deps */
                delegate.addEdge(cpst, phiSt, Dependency.CONTROL_DEP);
                /** END Custom change: control deps */
              }
            }
            phiUseIndex++;
          }
        }
      }
    }
  }

  /**
   * Create all data dependence edges in this PDG.
   * 
   * Scalar dependences are taken from SSA def-use information.
   * 
   * Heap dependences are computed by a reaching defs analysis.
   * 
   * @param pa
   * @param mod
   */
  private void createScalarDataDependenceEdges(IR ir, Map<SSAInstruction, Integer> instructionIndices) {
    if (dOptions.equals(DataDependenceOptions.NONE)) {
      return;
    }

    if (ir == null) {
      return;
    }

    // this is tricky .. I'm explicitly creating a new DefUse to make sure it refers to the instructions we need from
    // the "one true" ir of the moment.
    DefUse DU = new DefUse(ir);
    SSAInstruction[] instructions = ir.getInstructions();

    //
    // TODO: teach some other bit of code about the uses of
    // GetCaughtException, and then delete this code.
    //
    if (!dOptions.isIgnoreExceptions()) {
      for (ISSABasicBlock bb : ir.getControlFlowGraph()) {
        if (bb.isCatchBlock()) {
          SSACFG.ExceptionHandlerBasicBlock ehbb = (SSACFG.ExceptionHandlerBasicBlock) bb;

          if (ehbb.getCatchInstruction() != null) {
            Statement c = ssaInstruction2Statement(ehbb.getCatchInstruction(), ir, instructionIndices);

            for (ISSABasicBlock pb : ir.getControlFlowGraph().getExceptionalPredecessors(ehbb)) {
              SSAInstruction st = instructions[pb.getLastInstructionIndex()];

              if (st instanceof SSAAbstractInvokeInstruction) {
                delegate.addEdge(new ExceptionalReturnCaller(node, pb.getLastInstructionIndex()), c, Dependency.DATA_DEP);
              } else if (st instanceof SSAAbstractThrowInstruction) {
                delegate.addEdge(ssaInstruction2Statement(st, ir, instructionIndices), c, Dependency.DATA_DEP);
              }
            }
          }
        }
      }
    }

    for (Iterator<? extends Statement> it = iterator(); it.hasNext();) {
      Statement s = it.next();
      switch (s.getKind()) {
      case NORMAL:
      case CATCH:
      case PI:
      case PHI: {
        SSAInstruction statement = statement2SSAInstruction(instructions, s);
        // note that data dependencies from invoke instructions will pass
        // interprocedurally
        if (!(statement instanceof SSAAbstractInvokeInstruction)) {
          if (dOptions.isTerminateAtCast() && (statement instanceof SSACheckCastInstruction)) {
            break;
          }
          if (dOptions.isTerminateAtCast() && (statement instanceof SSAInstanceofInstruction)) {
            break;
          }
          // add edges from this statement to every use of the def of this
          // statement
          for (int i = 0; i < statement.getNumberOfDefs(); i++) {
            int def = statement.getDef(i);
            for (Iterator<SSAInstruction> it2 = DU.getUses(def); it2.hasNext();) {
              SSAInstruction use = it2.next();
              if (dOptions.isIgnoreBasePtrs()) {
                if (use instanceof SSANewInstruction) {
                  // cut out array length parameters
                  continue;
                }
                if (hasBasePointer(use)) {
                  int base = getBasePointer(use);
                  if (def == base) {
                    // skip the edge to the base pointer
                    continue;
                  }
                  if (use instanceof SSAArrayReferenceInstruction) {
                    SSAArrayReferenceInstruction arr = (SSAArrayReferenceInstruction) use;
                    if (def == arr.getIndex()) {
                      // skip the edge to the array index
                      continue;
                    }
                  }
                }
              }
              Statement u = ssaInstruction2Statement(use, ir, instructionIndices);
              delegate.addEdge(s, u, Dependency.DATA_DEP);
            }
          }
        }
        break;
      }
      case EXC_RET_CALLER:
      case NORMAL_RET_CALLER:
      case PARAM_CALLEE: {
        if (dOptions.isIgnoreExceptions()) {
          assert !s.getKind().equals(Kind.EXC_RET_CALLER);
        }

        ValueNumberCarrier a = (ValueNumberCarrier) s;
        for (Iterator<SSAInstruction> it2 = DU.getUses(a.getValueNumber()); it2.hasNext();) {
          SSAInstruction use = it2.next();
          if (dOptions.isIgnoreBasePtrs()) {
            if (use instanceof SSANewInstruction) {
              // cut out array length parameters
              continue;
            }
            if (hasBasePointer(use)) {
              int base = getBasePointer(use);
              if (a.getValueNumber() == base) {
                // skip the edge to the base pointer
                continue;
              }
              if (use instanceof SSAArrayReferenceInstruction) {
                SSAArrayReferenceInstruction arr = (SSAArrayReferenceInstruction) use;
                if (a.getValueNumber() == arr.getIndex()) {
                  // skip the edge to the array index
                  continue;
                }
              }
            }
          }
          Statement u = ssaInstruction2Statement(use, ir, instructionIndices);
          delegate.addEdge(s, u, Dependency.DATA_DEP);
        }
        break;
      }
      case NORMAL_RET_CALLEE:
        for (NormalStatement ret : computeReturnStatements(ir)) {
          delegate.addEdge(ret, s, Dependency.DATA_DEP);
        }
        break;
      case EXC_RET_CALLEE:
        if (dOptions.isIgnoreExceptions()) {
          Assertions.UNREACHABLE();
        }
        // TODO: this is overly conservative. deal with catch blocks?
        for (IntIterator ii = getPEIs(ir).intIterator(); ii.hasNext();) {
          int index = ii.next();
          SSAInstruction pei = ir.getInstructions()[index];
          if (dOptions.isTerminateAtCast() && (pei instanceof SSACheckCastInstruction)) {
            continue;
          }
          if (pei instanceof SSAAbstractInvokeInstruction) {
            if (! dOptions.isIgnoreExceptions()) {
              Statement st = new ExceptionalReturnCaller(node, index);
              delegate.addEdge(st, s, Dependency.DATA_DEP);
            }
          } else {
            delegate.addEdge(new NormalStatement(node, index), s, Dependency.DATA_DEP);
          }
        }
        break;
      case PARAM_CALLER: {
        ParamCaller pac = (ParamCaller) s;
        int vn = pac.getValueNumber();
        // note that if the caller is the fake root method and the parameter
        // type is primitive,
        // it's possible to have a value number of -1. If so, just ignore it.
        if (vn > -1) {
          if (ir.getSymbolTable().isParameter(vn)) {
            Statement a = new ParamCallee(node, vn);
            delegate.addEdge(a, pac, Dependency.DATA_DEP);
          } else {
            SSAInstruction d = DU.getDef(vn);
            if (dOptions.isTerminateAtCast() && (d instanceof SSACheckCastInstruction)) {
              break;
            }
            if (d != null) {
              if (d instanceof SSAAbstractInvokeInstruction) {
                SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) d;
                if (vn == call.getException()) {
                  if (! dOptions.isIgnoreExceptions()) {
                    Statement st = new ExceptionalReturnCaller(node, instructionIndices.get(d));
                    delegate.addEdge(st, pac, Dependency.DATA_DEP);
                  }
                } else {
                  Statement st = new NormalReturnCaller(node, instructionIndices.get(d));
                  delegate.addEdge(st, pac, Dependency.DATA_DEP);
                }
              } else {
                Statement ds = ssaInstruction2Statement(d, ir, instructionIndices);
                delegate.addEdge(ds, pac, Dependency.DATA_DEP);
              }
            }
          }
        }
      }
      break;

      case HEAP_RET_CALLEE:
      case HEAP_RET_CALLER:
      case HEAP_PARAM_CALLER:
      case HEAP_PARAM_CALLEE:
      case METHOD_ENTRY:
      case METHOD_EXIT:
        // do nothing
        break;
      default:
        Assertions.UNREACHABLE(s.toString());
        break;
      }
    }
  }

  private static class SingletonSet extends SetOfClasses implements Serializable {

    /* Serial version */
    private static final long serialVersionUID = -3256390509887654324L;

    private final TypeReference t;

    SingletonSet(TypeReference t) {
      this.t = t;
    }

    @Override
    public void add(String klass) {
      Assertions.UNREACHABLE();
    }

    @Override
    public boolean contains(String klassName) {
      return t.getName().toString().substring(1).equals(klassName);
    }
  }

  private static class SetComplement extends SetOfClasses implements Serializable {

    /* Serial version */
    private static final long serialVersionUID = -3256390509887654323L;

    private final SetOfClasses set;

    SetComplement(SetOfClasses set) {
      this.set = set;
    }

    static SetComplement complement(SetOfClasses set) {
      return new SetComplement(set);
    }

    @Override
    public void add(String klass) {
      Assertions.UNREACHABLE();
    }

    @Override
    public boolean contains(String klassName) {
      return !set.contains(klassName);
    }
  }

  /**
   * Create heap data dependence edges in this PDG relevant to a particular {@link PointerKey}.
   */
  private void createHeapDataDependenceEdges(final PointerKey pk) {
    //System.out.println("createHeapDataDependenceEdges" + pk);
    if (locationsHandled.contains(pk)) {
      return;
    } else {
      locationsHandled.add(pk);
    }
    if (dOptions.isIgnoreHeap() || (exclusions != null && exclusions.excludes(pk))) {
      return;
    }

    TypeReference t = HeapExclusions.getType(pk);
    if (t == null) {
      return;
    }

    // It's OK to create a new IR here; we're not keeping any hashing live up to this point
    //IR ir = node.getIR();
    if (ir == null) {
      return;
    }

    if (VERBOSE) {
      System.out.println("Location " + pk);
    }

    // in reaching defs calculation, exclude heap statements that are
    // irrelevant.
    Predicate f = new Predicate() {
      @Override public boolean test(Object o) {
        if (o instanceof HeapStatement) {
          HeapStatement h = (HeapStatement) o;
          return h.getLocation().equals(pk);
        } else {
          return true;
        }
      }
    };
    Collection<Statement> relevantStatements = Iterator2Collection.toSet(new FilterIterator<Statement>(iterator(), f));

    Map<Statement, OrdinalSet<Statement>> heapReachingDefs = new HeapReachingDefs<T>(modRef, heapModel).computeReachingDefs(node, ir, pa, mod,
        relevantStatements, new HeapExclusions(SetComplement.complement(new SingletonSet(t))), cg);
    for (Statement st : heapReachingDefs.keySet()) {
      switch (st.getKind()) {
      case NORMAL:
      case CATCH:
      case PHI:
      case PI: {
        OrdinalSet<Statement> defs = heapReachingDefs.get(st);
        if (defs != null) {
          for (Statement def : defs) {
            if (delegate.containsNode(def) && delegate.containsNode(st)) {
              delegate.addEdge(def, st, Dependency.DATA_DEP);
            } else {
              // TODO
              System.err.println("missing nodes in graph");
            }
          }
        }
      }
      break;
      case EXC_RET_CALLER:
      case NORMAL_RET_CALLER:
      case PARAM_CALLEE:
      case NORMAL_RET_CALLEE:
      case PARAM_CALLER:
      case EXC_RET_CALLEE:
        break;
      case HEAP_RET_CALLEE:
      case HEAP_RET_CALLER:
      case HEAP_PARAM_CALLER: {
        OrdinalSet<Statement> defs = heapReachingDefs.get(st);
        if (defs != null) {
          for (Statement def : defs) {
            if (delegate.containsNode(def) && delegate.containsNode(st)) {
              delegate.addEdge(def, st, Dependency.DATA_DEP);
            } else {
              // TODO
              System.err.println("missing nodes in graph");
            }
          }
        }
        break;
      }
      case HEAP_PARAM_CALLEE:
      case METHOD_ENTRY:
      case METHOD_EXIT:
        // do nothing .. there are no incoming edges
        break;
      default:
        Assertions.UNREACHABLE(st.toString());
        break;
      }
    }
  }

  private boolean hasBasePointer(SSAInstruction use) {
    if (use instanceof SSAFieldAccessInstruction) {
      SSAFieldAccessInstruction f = (SSAFieldAccessInstruction) use;
      return !f.isStatic();
    } else if (use instanceof SSAArrayReferenceInstruction) {
      return true;
    } else if (use instanceof SSAArrayLengthInstruction) {
      return true;
    } else {
      return false;
    }
  }

  private int getBasePointer(SSAInstruction use) {
    if (use instanceof SSAFieldAccessInstruction) {
      SSAFieldAccessInstruction f = (SSAFieldAccessInstruction) use;
      return f.getRef();
    } else if (use instanceof SSAArrayReferenceInstruction) {
      SSAArrayReferenceInstruction a = (SSAArrayReferenceInstruction) use;
      return a.getArrayRef();
    } else if (use instanceof SSAArrayLengthInstruction) {
      SSAArrayLengthInstruction s = (SSAArrayLengthInstruction) use;
      return s.getArrayRef();
    } else {
      Assertions.UNREACHABLE("BOOM");
      return -1;
    }
  }

  /**
   * @return Statements representing each return instruction in the ir
   */
  private Collection<NormalStatement> computeReturnStatements(final IR ir) {
    Predicate filter = new Predicate() {
      @Override public boolean test(Object o) {
        if (o instanceof NormalStatement) {
          NormalStatement s = (NormalStatement) o;
          SSAInstruction st = ir.getInstructions()[s.getInstructionIndex()];
          return st instanceof SSAReturnInstruction;
        } else {
          return false;
        }
      }
    };
    return Iterator2Collection.toSet(new FilterIterator<NormalStatement>(iterator(), filter));
  }

  /**
   * @return {@link IntSet} representing instruction indices of each PEI in the ir
   */
  private IntSet getPEIs(final IR ir) {
    BitVectorIntSet result = new BitVectorIntSet();
    for (int i = 0; i < ir.getInstructions().length; i++) {
      if (ir.getInstructions()[i] != null && ir.getInstructions()[i].isPEI()) {
        result.add(i);
      }
    }
    return result;
  }

  /**
   * Wrap an {@link SSAInstruction} in a {@link Statement}. WARNING: Since we're using a {@link HashMap} of {@link SSAInstruction}s,
   * and equals() of {@link SSAInstruction} assumes a canonical representative for each instruction, we <bf>must</bf> ensure that we
   * use the same IR object throughout initialization!!
   */
  private Statement ssaInstruction2Statement(SSAInstruction s, IR ir, Map<SSAInstruction, Integer> instructionIndices) {
    return ssaInstruction2Statement(node, s, instructionIndices, ir);
  }

  public static synchronized Statement ssaInstruction2Statement(CGNode node, SSAInstruction s,
      Map<SSAInstruction, Integer> instructionIndices, IR ir) {
    if (node == null) {
      throw new IllegalArgumentException("null node");
    }
    if (s == null) {
      throw new IllegalArgumentException("null s");
    }
    if (s instanceof SSAPhiInstruction) {
      SSAPhiInstruction phi = (SSAPhiInstruction) s;
      return new PhiStatement(node, phi);
    } else if (s instanceof SSAPiInstruction) {
      SSAPiInstruction pi = (SSAPiInstruction) s;
      return new PiStatement(node, pi);
    } else if (s instanceof SSAGetCaughtExceptionInstruction) {
      return new GetCaughtExceptionStatement(node, ((SSAGetCaughtExceptionInstruction) s));
    } else {
      Integer x = instructionIndices.get(s);
      if (x == null) {
        Assertions.UNREACHABLE(s.toString() + "\nnot found in map of\n" + ir);
      }
      return new NormalStatement(node, x.intValue());
    }
  }

  /**
   * @return for each SSAInstruction, its instruction index in the ir instruction array
   */
  public static Map<SSAInstruction, Integer> computeInstructionIndices(IR ir) {
    Map<SSAInstruction, Integer> result = HashMapFactory.make();
    if (ir != null) {
      SSAInstruction[] instructions = ir.getInstructions();
      for (int i = 0; i < instructions.length; i++) {
        SSAInstruction s = instructions[i];
        if (s != null) {
          result.put(s, new Integer(i));
        }
      }
    }
    return result;
  }

  /**
   * Convert a NORMAL or PHI Statement to an SSAInstruction
   */
  private SSAInstruction statement2SSAInstruction(SSAInstruction[] instructions, Statement s) {
    SSAInstruction statement = null;
    switch (s.getKind()) {
    case NORMAL:
      NormalStatement n = (NormalStatement) s;
      statement = instructions[n.getInstructionIndex()];
      break;
    case PHI:
      PhiStatement p = (PhiStatement) s;
      statement = p.getPhi();
      break;
    case PI:
      PiStatement ps = (PiStatement) s;
      statement = ps.getPi();
      break;
    case CATCH:
      GetCaughtExceptionStatement g = (GetCaughtExceptionStatement) s;
      statement = g.getInstruction();
      break;
    default:
      Assertions.UNREACHABLE(s.toString());
    }
    return statement;
  }

  /**
   * Create all nodes in this PDG. Each node is a Statement.
   */
  private void createNodes(Map<CGNode, OrdinalSet<PointerKey>> ref, ControlDependenceOptions cOptions, IR ir) {

    if (ir != null) {
      createNormalStatements(ir, ref);
      createSpecialStatements(ir);
    }

    createCalleeParams();
    createReturnStatements();

    delegate.addNode(new MethodEntryStatement(node));
    delegate.addNode(new MethodExitStatement(node));
  }

  /**
   * create nodes representing defs of the return values
   * 
   * @param mod the set of heap locations which may be written (transitively) by this node. These are logically parameters in the
   *          SDG.
   * @param dOptions
   */
  private void createReturnStatements() {
    ArrayList<Statement> list = new ArrayList<Statement>();
    if (!node.getMethod().getReturnType().equals(TypeReference.Void)) {
      NormalReturnCallee n = new NormalReturnCallee(node);
      delegate.addNode(n);
      list.add(n);
    }
    if (!dOptions.isIgnoreExceptions()) {
      ExceptionalReturnCallee e = new ExceptionalReturnCallee(node);
      delegate.addNode(e);
      list.add(e);
    }
    if (!dOptions.isIgnoreHeap()) {
      for (PointerKey p : mod.get(node)) {
        Statement h = new HeapStatement.HeapReturnCallee(node, p);
        delegate.addNode(h);
        list.add(h);
      }
    }
    returnStatements = new Statement[list.size()];
    list.toArray(returnStatements);
  }

  /**
   * create nodes representing defs of formal parameters
   * 
   * @param ref the set of heap locations which may be read (transitively) by this node. These are logically parameters in the SDG.
   */
  private void createCalleeParams() {
    if (paramCalleeStatements == null) {
      ArrayList<Statement> list = new ArrayList<Statement>();
      int paramCount = node.getMethod().getNumberOfParameters();

      for (Iterator<CGNode> callers = cg.getPredNodes(node); callers.hasNext(); ) {
        CGNode caller = callers.next();
        IR callerIR = caller.getIR();
        for (Iterator<CallSiteReference> sites = cg.getPossibleSites(caller, node); sites.hasNext(); ) {
          for (SSAAbstractInvokeInstruction inst : callerIR.getCalls(sites.next())) {
            paramCount = Math.max(paramCount, inst.getNumberOfParameters()-1);
          }
        }
      }

      for (int i = 1; i <= paramCount; i++) {
        ParamCallee s = new ParamCallee(node, i);
        delegate.addNode(s);
        list.add(s);
      }
      if (!dOptions.isIgnoreHeap()) {
        for (PointerKey p : ref.get(node)) {
          Statement h = new HeapStatement.HeapParamCallee(node, p);
          delegate.addNode(h);
          list.add(h);
        }
      }
      paramCalleeStatements = new Statement[list.size()];
      list.toArray(paramCalleeStatements);
    }
  }

  /**
   * Create nodes corresponding to
   * <ul>
   * <li>phi instructions
   * <li>getCaughtExceptions
   * </ul>
   */
  private void createSpecialStatements(IR ir) {
    // create a node for instructions which do not correspond to bytecode
    for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
      SSAInstruction s = it.next();
      if (s instanceof SSAPhiInstruction) {
        delegate.addNode(new PhiStatement(node, (SSAPhiInstruction) s));
      } else if (s instanceof SSAGetCaughtExceptionInstruction) {
        delegate.addNode(new GetCaughtExceptionStatement(node, (SSAGetCaughtExceptionInstruction) s));
      } else if (s instanceof SSAPiInstruction) {
        delegate.addNode(new PiStatement(node, (SSAPiInstruction) s));
      }
    }
  }

  /**
   * Create nodes in the graph corresponding to "normal" (bytecode) instructions
   */
  private void createNormalStatements(IR ir, Map<CGNode, OrdinalSet<PointerKey>> ref) {
    // create a node for every normal instruction in the IR
    SSAInstruction[] instructions = ir.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      SSAInstruction s = instructions[i];

      if (s instanceof SSAGetCaughtExceptionInstruction) {
        continue;
      }

      if (s != null) {
        final NormalStatement statement = new NormalStatement(node, i);
        delegate.addNode(statement);
        if (s instanceof SSAAbstractInvokeInstruction) {
          callSite2Statement.put(((SSAAbstractInvokeInstruction) s).getCallSite(), statement);
          addParamPassingStatements(i, ref, ir);
        }
      }
    }
  }

  /**
   * Create nodes in the graph corresponding to in/out parameter passing for a call instruction
   */
  private void addParamPassingStatements(int callIndex, Map<CGNode, OrdinalSet<PointerKey>> ref, IR ir) {
    SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) ir.getInstructions()[callIndex];
    Collection<Statement> params = MapUtil.findOrCreateSet(callerParamStatements, call.getCallSite());
    Collection<Statement> rets = MapUtil.findOrCreateSet(callerReturnStatements, call.getCallSite());
    for (int j = 0; j < call.getNumberOfUses(); j++) {
      Statement st = new ParamCaller(node, callIndex, call.getUse(j));
      delegate.addNode(st);
      params.add(st);
    }
    if (!call.getDeclaredResultType().equals(TypeReference.Void)) {
      Statement st = new NormalReturnCaller(node, callIndex);
      delegate.addNode(st);
      rets.add(st);
    }
    {
      if (!dOptions.isIgnoreExceptions()) {
        Statement st = new ExceptionalReturnCaller(node, callIndex);
        delegate.addNode(st);
        rets.add(st);
      }
    }

    if (!dOptions.isIgnoreHeap()) {
      OrdinalSet<PointerKey> uref = unionHeapLocations(cg, node, call, ref);
      for (PointerKey p : uref) {
        Statement st = new HeapStatement.HeapParamCaller(node, callIndex, p);
        delegate.addNode(st);
        params.add(st);
      }
      OrdinalSet<PointerKey> umod = unionHeapLocations(cg, node, call, mod);
      for (PointerKey p : umod) {
        Statement st = new HeapStatement.HeapReturnCaller(node, callIndex, p);
        delegate.addNode(st);
        rets.add(st);
      }
    }
  }

  /**
   * @return the set of all locations read by any callee at a call site.
   */
  private OrdinalSet<PointerKey> unionHeapLocations(CallGraph cg, CGNode n, SSAAbstractInvokeInstruction call,
      Map<CGNode, OrdinalSet<PointerKey>> loc) {
    BitVectorIntSet bv = new BitVectorIntSet();
    for (CGNode t : cg.getPossibleTargets(n, call.getCallSite())) {
      bv.addAll(loc.get(t).getBackingSet());
    }
    return new OrdinalSet<PointerKey>(bv, loc.get(n).getMapping());
  }

  @Override
  public String toString() {
    populate();
    StringBuffer result = new StringBuffer("PDG for " + node + ":\n");
    result.append(super.toString());
    return result.toString();
  }

  public Statement[] getParamCalleeStatements() {
    if (paramCalleeStatements == null) {
      createCalleeParams();
    }
    Statement[] result = new Statement[paramCalleeStatements.length];
    System.arraycopy(paramCalleeStatements, 0, result, 0, result.length);
    return result;
  }

  public Statement[] getReturnStatements() {
    populate();
    Statement[] result = new Statement[returnStatements.length];
    System.arraycopy(returnStatements, 0, result, 0, result.length);
    return result;
  }

  public CGNode getCallGraphNode() {
    return node;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass().equals(obj.getClass())) {
      return node.equals(((PDG) obj).node);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return 103 * node.hashCode();
  }

  @Override
  public int getPredNodeCount(Statement N) throws UnimplementedError {
    populate();
    Assertions.UNREACHABLE();
    return delegate.getPredNodeCount(N);
  }

  @Override
  public Iterator<Statement> getPredNodes(Statement N) {
    populate();
    if (!dOptions.isIgnoreHeap()) {
      computeIncomingHeapDependencies(N);
    }
    return delegate.getPredNodes(N);
  }

  private void computeIncomingHeapDependencies(Statement N) {
    if (IncomingHeapDependenciesComputed.contains(N)) {
      return;
    }
    switch (N.getKind()) {
    case NORMAL:
      NormalStatement st = (NormalStatement) N;
      if (!(ignoreAllocHeapDefs && st.getInstruction() instanceof SSANewInstruction)) {
        Collection<PointerKey> ref = modRef.getRef(node, heapModel, pa, st.getInstruction(), exclusions);
        for (PointerKey pk : ref) {
          createHeapDataDependenceEdges(pk);
        }
      }
      break;
    case HEAP_PARAM_CALLEE:
    case HEAP_PARAM_CALLER:
    case HEAP_RET_CALLEE:
    case HEAP_RET_CALLER:
      HeapStatement h = (HeapStatement) N;
      createHeapDataDependenceEdges(h.getLocation());
    }
    IncomingHeapDependenciesComputed.add(N);

  }

  private void computeOutgoingHeapDependencies(Statement N) {
    if (OutgoingHeapDependenciesComputed.contains(N)) {
      return;
    }
    switch (N.getKind()) {
    case NORMAL:
      NormalStatement st = (NormalStatement) N;
      if (!(ignoreAllocHeapDefs && st.getInstruction() instanceof SSANewInstruction)) {
        Collection<PointerKey> mod = modRef.getMod(node, heapModel, pa, st.getInstruction(), exclusions);
        for (PointerKey pk : mod) {
          createHeapDataDependenceEdges(pk);
        }
      }
      break;
    case HEAP_PARAM_CALLEE:
    case HEAP_PARAM_CALLER:
    case HEAP_RET_CALLEE:
    case HEAP_RET_CALLER:
      HeapStatement h = (HeapStatement) N;
      createHeapDataDependenceEdges(h.getLocation());
    }
    OutgoingHeapDependenciesComputed.add(N);
  }

  @Override
  public int getSuccNodeCount(Statement N) throws UnimplementedError {
    populate();
    Assertions.UNREACHABLE();
    return delegate.getSuccNodeCount(N);
  }

  @Override
  public Iterator<Statement> getSuccNodes(Statement N) {
    populate();
    if (!dOptions.isIgnoreHeap()) {
      computeOutgoingHeapDependencies(N);
    }
    return delegate.getSuccNodes(N);
  }

  @Override
  public boolean hasEdge(Statement src, Statement dst) throws UnimplementedError {
    populate();
    return delegate.hasEdge(src, dst);
  }

  @Override
  public void removeNodeAndEdges(Statement N) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void addNode(Statement n) {
    Assertions.UNREACHABLE();
  }

  @Override
  public boolean containsNode(Statement N) {
    populate();
    return delegate.containsNode(N);
  }

  @Override
  public int getNumberOfNodes() {
    populate();
    return delegate.getNumberOfNodes();
  }

  @Override
  public Iterator<Statement> iterator() {
    populate();
    return delegate.iterator();
  }

  @Override
  public void removeNode(Statement n) {
    Assertions.UNREACHABLE();
  }

  @Override
  public void addEdge(Statement src, Statement dst) {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeAllIncidentEdges(Statement node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeEdge(Statement src, Statement dst) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeIncomingEdges(Statement node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public void removeOutgoingEdges(Statement node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();
  }

  @Override
  public int getMaxNumber() {
    populate();
    return delegate.getMaxNumber();
  }

  @Override
  public Statement getNode(int number) {
    populate();
    return delegate.getNode(number);
  }

  @Override
  public int getNumber(Statement N) {
    populate();
    return delegate.getNumber(N);
  }

  @Override
  public Iterator<Statement> iterateNodes(IntSet s) {
    Assertions.UNREACHABLE();
    return null;
  }

  @Override
  public IntSet getPredNodeNumbers(Statement node) {
    Assertions.UNREACHABLE();
    return null;
  }

  @Override
  public IntSet getSuccNodeNumbers(Statement node) {
    Assertions.UNREACHABLE();
    return null;
  }
  /** BEGIN Custom change: control deps */
  public boolean isControlDependend(Statement from, Statement to) {
    return delegate.hasEdge(from, to, Dependency.CONTROL_DEP);
  }
  /** END Custom change: control deps */
}
