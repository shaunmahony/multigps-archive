package edu.psu.compbio.seqcode.gse.deepseq;

import edu.psu.compbio.seqcode.gse.datasets.species.Genome;

/**
 * Extended read: initializes a read, shifts it towards the 3', and extends from both the 5' and 3' independently.
 * Although three extra parameters now have to be provided, this offers greater flexibility in specifying different artificial read extension schemas. 
 * 
 * @author shaun
 *
 */
public class ExtReadHit extends ReadHit{

	//private int startShift;
	//private int fivePrimeExt;
	//private int threePrimeExt;
	
	public ExtReadHit(ReadHit r, int startShift, int fivePrimeExt, int threePrimeExt){
		this(r.getGenome(), r.getID(), r.getChrom(), r.getStart(), r.getEnd(), r.getStrand(), r.getWeight(), startShift, fivePrimeExt, threePrimeExt);
	}
	public ExtReadHit(Genome g, int id,String c, int s, int e, char str, double w, int startShift, int fivePrimeExt, int threePrimeExt){
		super(g,id,c,
		    (str=='+' ? Math.max(1, s+startShift-fivePrimeExt+1):Math.max(1, s-startShift-threePrimeExt+1)),
		    (str=='+' ? Math.min(g.getChromLength(c),e+startShift+threePrimeExt-1):Math.min(e-startShift+fivePrimeExt-1, g.getChromLength(c))),
		    str,w);
		//this.startShift=startShift;
		//this.fivePrimeExt = fivePrimeExt;
		//this.threePrimeExt = threePrimeExt;
	}
}
