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

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAAbstractThrowInstruction;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAStoreIndirectInstruction;
import com.ibm.wala.util.json.JSONObject;

/**
 * A statement that has a corresponding index in the SSA IR
 */
public class NormalStatement extends StatementWithInstructionIndex {

  public NormalStatement(CGNode node, int instructionIndex) {
    super(node, instructionIndex);
    if (
        getInstruction() instanceof SSAAbstractInvokeInstruction
        || getInstruction() instanceof SSAArrayLengthInstruction
        || getInstruction() instanceof SSAArrayReferenceInstruction
        || getInstruction() instanceof SSAFieldAccessInstruction
        || getInstruction() instanceof SSAStoreIndirectInstruction
        || getInstruction() instanceof SSANewInstruction
        ) {
      isKeyNode = true;
    } else {
      isKeyNode = false;
    }
  }

  @Override
  public Kind getKind() {
    return Kind.NORMAL;
  }

  @Override
  public String toString() {
    String name = "";
    if (getInstruction().hasDef()) {
      String[] names = getNode().getIR().getLocalNames(getInstructionIndex(), getInstruction().getDef());
      if (names != null && names.length > 0) {
        name = "[" + names[0];
        for (int i = 1; i < names.length; i++) {
          name = name + ", " + names[i];
        }
        name = name + "]: ";
      }
    }

    return "NORMAL " + getNode().getMethod().getName() + ":" + name + getInstruction().toString() + " " + getNode();
  }

  @Override
  public JSONObject toJSON() {
    JSONObject ret = super.toJSON();
    return ret;
  }

}
