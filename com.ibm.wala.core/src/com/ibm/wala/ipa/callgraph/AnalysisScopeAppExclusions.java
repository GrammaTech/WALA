/**
 * ***************************************************************************** Copyright (c) 2007
 * IBM Corporation. All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * <p>Contributors: IBM Corporation - initial API and implementation
 * *****************************************************************************
 */
package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.SetOfClasses;
import java.util.Collection;
import java.util.Collections;

/**
 * Class to specify an analysis scope where some classes loaded by the application loader will not
 * be considered "application" classes. This is useful when analyzing programs where library code
 * has been added into the jar and thus would normally be treated as "application" code by WALA.
 */
public class AnalysisScopeAppExclusions extends AnalysisScope {

    private SetOfClasses applicationExclusions;

    public static AnalysisScopeAppExclusions createJavaAnalysisScope(
            SetOfClasses applicationExclusions) {
        AnalysisScopeAppExclusions scope =
                new AnalysisScopeAppExclusions(
                        Collections.singleton(Language.JAVA), applicationExclusions);
        scope.initForJava();
        return scope;
    }

    protected AnalysisScopeAppExclusions(
            Collection<? extends Language> languages, SetOfClasses applicationExclusions) {
        super(languages);
        this.applicationExclusions = applicationExclusions;
    }

    @Override
    public boolean isApplicationClass(IClass klass) {
        return klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)
                && !applicationExclusions.contains(klass.getName().toString().substring(1));
    }
}
