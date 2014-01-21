/*
 * Created on Mar 3, 2006
 */
package edu.psu.compbio.seqcode.gse.ewok.verbs;

import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.general.NamedRegion;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;

/**
 * @author tdanford
 *
 * <code>ChromRegionIterator</code> is an iterator over the chromosomes
 * in the Genome provided to the constructor.
 */
public class ChromRegionIterator implements Iterator<NamedRegion> {
    
    private Genome genome;
    private Vector<NamedRegion> regions;
    private int index;

    public ChromRegionIterator(Genome g) {
        genome = g;
        regions = new Vector<NamedRegion>();
        index = 0;
        
        for(String chromName : genome.getChromList()) { 
            Genome.ChromosomeInfo s = genome.getChrom(chromName);
            NamedRegion region = new NamedRegion(genome, chromName, 1, s.getLength() , chromName);
            regions.add(region);
        }
        
    }
    

    /* (non-Javadoc)
     * @see java.util.Iterator#hasNext()
     */
    public boolean hasNext() {
        return index < regions.size();
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#next()
     */
    public NamedRegion next() {
        NamedRegion nr = regions.get(index);
        index++;
        //System.out.println("--- " + nr.getChrom());
        return nr;
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
