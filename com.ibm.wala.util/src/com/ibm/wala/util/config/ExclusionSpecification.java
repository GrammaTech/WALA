package com.ibm.wala.util.config;

import java.io.Serializable;

import com.ibm.wala.util.debug.UnimplementedError;

/**
 * This class is needed to express complex logic for exclusions in WALA's
 * AnalysisScope. In WALA, an AnalysisScope may contain a SetOfClasses that
 * specifies exclusions. contains(klass) must be true iff klass should be
 * excluded from the analysis (see e.g. the usage of exclusions in
 * com.ibm.wala.classLoader.ClassLoaderImpl.loadAllClasses())
 * 
 * <p>
 * It is awkward that ExclusionSpecification both extends SetOfClasses and
 * contains up to two instances of SetOfClasses. However this is necessary to
 * express complex custom logic for exclusions while conforming to WALA's
 * abstraction where exclusions are a single SetOfClasses in the AnalysisScope.
 * 
 * <p>
 * An ExclusionSpecification can contain zero or one inclusion SetOfClasses and
 * zero or one exclusion SetOfClasses. However, there are restrictions based on
 * the kind field, as follows:
 *
 * <ul>
 * <li>INCL_ONLY - one inclusion set, no exclusion set. contains(klass) is true
 * iff klass is in the complement of the inclusion set.
 * <li>EXCL_ONLY - one exclusion set, no inclusion set. contains(klass) is true
 * iff klass is in the exclusion set.
 * <li>INCL_OVERRIDE_EXCL - one inclusion set and one exclusion set.
 * contains(klass) is true iff klass is in the exclusion set minus the inclusion
 * set.
 * </ul>
 */
public class ExclusionSpecification extends SetOfClasses implements Serializable {

  public static enum Kind {
    INCL_ONLY,
    EXCL_ONLY,
    INCL_OVERRIDE_EXCL
  }

  private Kind kind;
  private SetOfClasses inclusions;
  private SetOfClasses exclusions;
  
  public ExclusionSpecification(Kind kind, SetOfClasses inclusions, SetOfClasses exclusions) {
    this.kind = kind;
    switch (kind) {
    case INCL_ONLY :
      if (inclusions == null || exclusions != null){
        throw new IllegalArgumentException("Illegal arguments for ExclusionSpecification of type INCL_ONLY");
      }
      this.inclusions = inclusions;
      break;
    case EXCL_ONLY:
      if (inclusions != null || exclusions == null){
        throw new IllegalArgumentException("Illegal arguments for ExclusionSpecification of type EXCL_ONLY");
      }
      this.exclusions = exclusions;
      break;
    case INCL_OVERRIDE_EXCL:
      if (inclusions == null || exclusions == null){
        throw new IllegalArgumentException("Illegal arguments for ExclusionSpecification of type INCL_OVERRIDE_EXCL");
      }
      this.inclusions = inclusions;
      this.exclusions = exclusions;
      break;
    default:
      throw new IllegalArgumentException("Invalid kind for ExclusionSpecification: " + kind);
    }
    // TODO potentially check if inclusions/exclusions are empty and throw error; would require adding isEmpty() 
    // method to SetOfClasses.
  }
  
  @Override
  public boolean contains(String klassName) {
    switch (kind) {
    case INCL_ONLY:
      return !inclusions.contains(klassName);
    case EXCL_ONLY:
      return exclusions.contains(klassName);
    case INCL_OVERRIDE_EXCL:
      return exclusions.contains(klassName) && !inclusions.contains(klassName);
    default:
      throw new IllegalArgumentException("contains() is unsupported for ExclusionSpecification kind " + kind);
    }
  }

  @Override
  public void add(String klass) {
    throw new UnimplementedError();
  }

}
