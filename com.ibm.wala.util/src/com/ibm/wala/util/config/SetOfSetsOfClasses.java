package com.ibm.wala.util.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import com.ibm.wala.util.debug.UnimplementedError;

public class SetOfSetsOfClasses extends SetOfClasses implements Serializable {

  private Collection<SetOfClasses> sets = new ArrayList<SetOfClasses>();
  
  public SetOfSetsOfClasses() {}
  
  public SetOfSetsOfClasses(Collection<SetOfClasses> sets) {
    this.sets.addAll(sets);
  }
  
  public void addSet(SetOfClasses set) {
    this.sets.add(set);
  }
  
  @Override
  public boolean contains(String klassName) {
    for (SetOfClasses set : sets) {
      if (set.contains(klassName)) return true;
    }
    return false;
  }

  @Override
  public void add(String klass) {
    throw new UnimplementedError();
  }

}
