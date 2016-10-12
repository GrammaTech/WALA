package com.ibm.wala.util.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import com.ibm.wala.util.debug.UnimplementedError;

/**
 * This class is needed to express complex logic for exclusions in WALA's
 * AnalysisScope. In WALA, an AnalysisScope may contain a SetOfClasses that
 * specifies exclusions. contains(klass) must be true iff klass should be
 * excluded from the analysis (see e.g. the usage of exclusions in
 * com.ibm.wala.classLoader.ClassLoaderImpl.loadAllClasses())
 * 
 * <p> It is awkward that ExclusionSpecification both extends SetOfClasses and
 * contains two collections of SetOfClasses. However this is necessary to
 * express complex custom logic for exclusions while conforming to WALA's
 * abstraction where exclusions are a single SetOfClasses in the AnalysisScope.
 * 
 * <p>An ExclusionSpecification can contain zero or more inclusion SetOfClasses and
 * zero or more exclusion SetOfClasses. However, there are restrictions based on
 * the kind field, as follows:
 *
 * <ul>
 * <li>INCL_ONLY - zero or more inclusion sets (typically one), and no exclusion
 * sets. contains(klass) is true iff klass is in the complement of the union of
 * the inclusion sets.
 * <li>EXCL_ONLY - zero or more exclusion sets (typically one), and no inclusion
 * sets. contains(klass) is true iff klass is in the union of the exclusion
 * sets.
 * <li>INCL_OVERRIDE_EXCL - zero or more inclusion sets and zero or more
 * inclusion sets (typically one of each). contains(klass) is true iff klass is
 * in the union of the exclusion sets minus the union of the inclusion sets.
 * </ul>
 */
public class ExclusionSpecification extends SetOfClasses implements Serializable {

  public static enum Kind {
    INCL_ONLY,
    EXCL_ONLY,
    INCL_OVERRIDE_EXCL
  }

  private Kind kind;
  private Collection<SetOfClasses> inclusions;
  private Collection<SetOfClasses> exclusions;
  
  public ExclusionSpecification(Kind kind) {
    this.kind = kind;
    this.inclusions = new ArrayList<SetOfClasses>();
    this.exclusions = new ArrayList<SetOfClasses>();
  }
  
  public void addInclusionSet(SetOfClasses set) {
    if ((kind != Kind.INCL_ONLY) && (kind != Kind.INCL_OVERRIDE_EXCL)){
      throw new IllegalArgumentException("Illegal attempt to add inclusions when kind is " + kind);
    }
    inclusions.add(set);
  }
  
  public void addExclusionSet(SetOfClasses set) {
    if ((kind != Kind.EXCL_ONLY) && (kind != Kind.INCL_OVERRIDE_EXCL)){
      throw new IllegalArgumentException("Illegal attempt to add exclusions when kind is " + kind);
    }
    exclusions.add(set);
  }
  
  @Override
  public boolean contains(String klassName) {
    boolean isExcluded = false;
    boolean isIncluded = false;
    for (SetOfClasses incl : inclusions) {
      if (incl.contains(klassName)) {
        isIncluded = true;
      }
    }
    for (SetOfClasses excl : exclusions) {
      if (excl.contains(klassName)) {
        isExcluded = true;
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
