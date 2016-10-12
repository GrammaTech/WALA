package com.ibm.wala.util.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ExplicitSetOfClasses extends SetOfClasses implements Serializable {

	/* Serial version */
	private static final long serialVersionUID = -6790094731673638421L;

	private Set<String> klasses = new HashSet<String>();
	
	public ExplicitSetOfClasses(Collection<String> klasses) {
    this.klasses.addAll(klasses);
	}
	
	@Override
	public boolean contains(String klassName) {
		return this.klasses.contains(klassName);
	}

	@Override
	public void add(String klass) {
		this.klasses.add(klass);
	}

}
