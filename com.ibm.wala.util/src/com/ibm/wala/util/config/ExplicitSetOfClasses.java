package com.ibm.wala.util.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ExplicitSetOfClasses extends SetOfClasses implements Serializable {

	/* Serial version */
	private static final long serialVersionUID = -6790094731673638421L;

	private Set<String> klasses = new HashSet<String>();
	private boolean inverted = false;
	private boolean includeSubclasses = false;
	
	public ExplicitSetOfClasses(Collection<String> klasses) {
		this(klasses, false, false);
	}
	
	public ExplicitSetOfClasses(Collection<String> klasses, boolean invert, boolean includeSubclasses) {
		this.klasses.addAll(klasses);
		this.inverted = invert;
		this.includeSubclasses = includeSubclasses;
	}
	
	public boolean isInverted() {
		return this.inverted;
	}
	
	public void setInverted(boolean invert) {
		this.inverted = invert;
	}
	
	public boolean isIncludeSubclasses() {
	  return this.includeSubclasses;
	}
	
	public void setIncludeSubclasses(boolean includeSubclasses) {
	  this.includeSubclasses = includeSubclasses;
	}
	
	@Override
	public boolean contains(String klassName) {
	  String klass = this.includeSubclasses ? klassName.split("\\$")[0] : klassName;
		return this.inverted ^ this.klasses.contains(klass);
	}

	@Override
	public void add(String klass) {
		this.klasses.add(klass);
	}

}
