/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.core.tests.cha;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Collection;
import java.util.HashSet;

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.ExplicitSetOfClasses;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.config.ExclusionSpecification;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;

public class ExclusionsTest {

  @Test
  public void testExclusions() throws IOException {
    AnalysisScope scope = AnalysisScopeReader.readJavaScope(TestConstants.WALA_TESTDATA, (new FileProvider()).getFile("GUIExclusions.txt"),
        ExclusionsTest.class.getClassLoader());
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    Assert.assertTrue(scope.getExclusions().contains(buttonRef.getName().toString().substring(1)));
  }
  
  @Test
  public void testExclusionsOnly() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    ExclusionSpecification exclusions =
        new ExclusionSpecification(ExclusionSpecification.Kind.EXCL_ONLY);
    InputStream exclusionStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    exclusions.addExclusionSet(new FileOfClasses(exclusionStream));
    assertTrue(exclusions.contains(buttonRef.getName().toString().substring(1)));
    }
  
  @Test
  public void testInclusionsOnly() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.INCL_ONLY);
    Collection<String> inclusions = new HashSet<String>();
    inclusions.add("java/awt/Button");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions));
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    }

  @Test
  public void testInclusionsOverrideExclusions() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    ExclusionSpecification exclusions =
        new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL);
    InputStream exclusionStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    exclusions.addExclusionSet(new FileOfClasses(exclusionStream));
    Collection<String> inclusions = new HashSet<String>();
    inclusions.add("java/awt/Button");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions));
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    }

  @Test
  public void testMultipleInclusions() throws IOException {
     TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.INCL_ONLY);
    Collection<String> inclusions1 = new HashSet<String>();
    inclusions1.add("java/awt/Button");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions1));
    Collection<String> inclusions2 = new HashSet<String>();
    inclusions2.add("java/awt/Canvas");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions2));
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    }

  @Test
  public void testMultipleInclusionsSingleExclusions() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    ExclusionSpecification exclusions =
        new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL);
    InputStream exclusionStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    exclusions.addExclusionSet(new FileOfClasses(exclusionStream));
    Collection<String> inclusions1 = new HashSet<String>();
    inclusions1.add("java/awt/Button");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions1));
    Collection<String> inclusions2 = new HashSet<String>();
    inclusions2.add("java/awt/Canvas");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions2));
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    }

  @Test
  public void testMultipleInclusionsMultipleExclusions() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    ExclusionSpecification exclusions =
        new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL);
    InputStream exclusionStream1 =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    exclusions.addExclusionSet(new FileOfClasses(exclusionStream1));
    InputStream exclusionStream2 =
        new FileInputStream((new FileProvider()).getFile("GUICorbaExclusions.txt"));
    exclusions.addExclusionSet(new FileOfClasses(exclusionStream2));
    Collection<String> inclusions1 = new HashSet<String>();
        inclusions1.add("java/awt/Button");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions1));
    Collection<String> inclusions2 = new HashSet<String>();
    inclusions2.add("java/awt/Canvas");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions2));
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidExclusionAdd() throws IOException {
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.INCL_ONLY);
    Collection<String> inclusions1 = new HashSet<String>();
    inclusions1.add("java/awt/Button");
    exclusions.addExclusionSet(new ExplicitSetOfClasses(inclusions1));
    }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidInclusionAdd() throws IOException {
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.EXCL_ONLY);
    Collection<String> inclusions1 = new HashSet<String>();
    inclusions1.add("java/awt/Button");
    exclusions.addInclusionSet(new ExplicitSetOfClasses(inclusions1));
    }
}
