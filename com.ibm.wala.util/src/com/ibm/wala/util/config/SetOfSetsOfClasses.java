package com.ibm.wala.util.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import com.ibm.wala.util.debug.UnimplementedError;

/**
 * Supports the abstraction of multiple sets of classes to allow both exclusions and "negative
 * exclusions" i.e. inclusions for WALA's AnalysisScope. A notable usage of exclusions is in
 * com.ibm.wala.classLoader.ClassLoaderImpl.loadAllClasses().
 *
 * <p>Currently we support three kinds of SetsOfSetsOfClasses (SOSOC's):
 *
 * <ul>
 * <li>inclusions-only - the SOSOC specifies one or more sets of classes to be included, and no
 *     exclusion sets. contains(klass) will return true iff klass is in the complement of the union
 *     of the inclusion sets.
 * <li>exclusions-only - the SOSOC specifies one or more sets of classes to be excluded, and no
 *     inclusion sets. contains(klass) will return true iff klass is in the union of the exclusion
 *     sets.
 * <li>both inclusions and exclusions, with inclusions taking priority in case of conflicts.
 *     contains(klass) is true iff klass is in the union of the exclusion sets minus the union of
 *     the inclusion sets.
 * </ul>
 */
public class SetOfSetsOfClasses extends SetOfClasses implements Serializable {

  public static enum Kind {
    INCL_ONLY,
    EXCL_ONLY,
    INCL_OVERRIDE_EXCL
  }

  private Kind kind;

  private Collection<SetOfClasses> sets = new ArrayList<SetOfClasses>();
  
  public SetOfSetsOfClasses(Kind kind) {
    this.kind = kind;
  }

  public SetOfSetsOfClasses(Collection<SetOfClasses> sets, Kind kind) {
    this.sets.addAll(sets);
    this.kind = kind;
  }
  
  public void addSet(SetOfClasses set) {
    this.sets.add(set);
  }
  
  @Override
  public boolean contains(String klassName) {
    boolean isExcluded = false;
    boolean isIncluded = false;
    for (SetOfClasses set : sets) {
      if ((set instanceof ExplicitSetOfClasses) && ((ExplicitSetOfClasses) set).isInverted()) { // inclusion
        if (!set.contains(klassName)) {
          isIncluded = true;
        }
      } else { // exclusion
        if (set.contains(klassName)) {
          isExcluded = true;
        }
      }
    }
    if (kind == Kind.INCL_ONLY) {
      return !isIncluded;
    } else if (kind == Kind.EXCL_ONLY) {
      return isExcluded;
    } else { // kind is INCL_OVERRIDE_EXCL
      return isExcluded && !isIncluded;
    }
  }

  @Override
  public void add(String klass) {
    throw new UnimplementedError();
  }

}
