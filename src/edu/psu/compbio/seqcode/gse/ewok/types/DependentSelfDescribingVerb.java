
package edu.psu.compbio.seqcode.gse.ewok.types;



public interface DependentSelfDescribingVerb extends SelfDescribingVerb { 

	public void setInput(String n, EchoType c);
	public void clearInput(String n);
	
	public void setParameter(String n, EchoType c);
	public void clearParameter(String n);
}
