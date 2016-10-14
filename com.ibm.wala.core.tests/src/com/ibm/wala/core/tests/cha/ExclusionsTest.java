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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Collection;
import java.util.HashSet;

import org.junit.Test;

import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.ExplicitSetOfClasses;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.config.SetOfClasses;
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
    assertTrue(scope.getExclusions().contains(buttonRef.getName().toString().substring(1)));
  }
  
  @Test
  public void testExclusionsOnly() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    TypeReference sunRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("sun.net.httpserver.ServerImpl"));
    InputStream exclusionStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    SetOfClasses exclSet = new FileOfClasses(exclusionStream);
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.EXCL_ONLY, null, exclSet);
    assertTrue(exclusions.contains(buttonRef.getName().toString().substring(1)));
    assertFalse(exclusions.contains(sunRef.getName().toString().substring(1)));
  }
  
  @Test
  public void testInclusionsOnly() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    TypeReference canvasRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Canvas"));
    TypeReference sunRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("sun.net.httpserver.ServerImpl"));
    Collection<String> inclStrings = new HashSet<String>();
    inclStrings.add("java/awt/Button");
    SetOfClasses inclSet = new ExplicitSetOfClasses(inclStrings);
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.INCL_ONLY, inclSet, null);
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    assertTrue(exclusions.contains(sunRef.getName().toString().substring(1)));
    assertTrue(exclusions.contains(canvasRef.getName().toString().substring(1)));
  }

  @Test
  public void testInclusionsOverrideExclusions() throws IOException {
    TypeReference buttonRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Button"));
    TypeReference canvasRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("java.awt.Canvas"));
    TypeReference sunRef = TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString("sun.net.httpserver.ServerImpl"));
    InputStream exclusionStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    SetOfClasses exclSet = new FileOfClasses(exclusionStream);
    Collection<String> inclStrings = new HashSet<String>();
    inclStrings.add("java/awt/Button");
    SetOfClasses inclSet = new ExplicitSetOfClasses(inclStrings);
    ExclusionSpecification exclusions = new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL, inclSet, exclSet);
    assertFalse(exclusions.contains(buttonRef.getName().toString().substring(1)));
    assertFalse(exclusions.contains(sunRef.getName().toString().substring(1)));
    assertTrue(exclusions.contains(canvasRef.getName().toString().substring(1)));
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorIncl1() throws IOException {
    InputStream classesStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    SetOfClasses classSet = new FileOfClasses(classesStream);
    new ExclusionSpecification(ExclusionSpecification.Kind.INCL_ONLY, null, classSet);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorIncl2() throws IOException {
    new ExclusionSpecification(ExclusionSpecification.Kind.INCL_ONLY, null, null);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorExcl1() throws IOException {
    InputStream classesStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    SetOfClasses classSet = new FileOfClasses(classesStream);
    new ExclusionSpecification(ExclusionSpecification.Kind.EXCL_ONLY, classSet, null);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorExcl2() throws IOException {
    new ExclusionSpecification(ExclusionSpecification.Kind.EXCL_ONLY, null, null);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorInclExcl1() throws IOException {
    InputStream classesStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    SetOfClasses classSet = new FileOfClasses(classesStream);
    new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL, null, classSet);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorInclExcl2() throws IOException {
    InputStream classesStream =
        new FileInputStream((new FileProvider()).getFile("GUIExclusions.txt"));
    SetOfClasses classSet = new FileOfClasses(classesStream);
    new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL, classSet, null);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidConstructorInclExcl3() throws IOException {
    new ExclusionSpecification(ExclusionSpecification.Kind.INCL_OVERRIDE_EXCL, null, null);
  }
}
