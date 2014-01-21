/*
 * Created on Feb 7, 2007
 *
 * TODO 
 * 
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.psu.compbio.seqcode.gse.datasets.species;

import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.utils.iterators.SingleIterator;

public class ExonicGene extends Gene {

    private TreeSet<Region> exons;
    
    public ExonicGene(Genome g, String c, int start, int end, String name, String id, char str, String src) {
        super(g, c, start, end, name, id, str, src);
        exons = null;
    }
    
    public void addExon(Region r) { 
        // can't do this check because the exon may be outside the *coding* region of the
        // gene, and sometimes we want the gene boundaries to be the coding region rather than
        // transcribed region.
        //        if(!contains(r)) { throw new IllegalArgumentException(); }
        if(exons == null) { exons = new TreeSet<Region>(); }
        /* we probably want to allow overlapping exons... AR */
        //        for(Region exon : exons) { if(exon.overlaps(r)) { throw new IllegalArgumentException(); } }
        exons.add(r);
    }
    
    public void addExon(int start, int end) { 
        Region r = new Region(getGenome(), getChrom(), start, end);
        addExon(r);
    }
    
    public Iterator<Region> getExons() { 
        return exons == null ? new SingleIterator<Region>(this) : exons.iterator(); 
    }
    
    public int getNumExons() { return exons == null ? 1 : exons.size(); }
    
    public int hashCode() { 
        int code = super.hashCode();
        if(exons != null) { 
            for(Region exon : exons) { 
                code += exon.hashCode(); code *= 37;
            }
        }
        return code;
    }
    
    public boolean equals(Object o) { 
        if(!(o instanceof ExonicGene)) { return false; }
        ExonicGene g = (ExonicGene)o;
        if(!super.equals(g)) { return false; }
        if(exons != null || g.exons != null) { 
            if(exons != null && g.exons != null) { 
                if(exons.size() != g.exons.size()) { return false; }
                for(Region exon : exons) { if(!g.exons.contains(exon)) { return false; } }
            } else { 
                return false;
            }
        }
        return true;
    }
}
