/*
 * Created on Mar 9, 2006
 */
package edu.psu.compbio.seqcode.gse.ewok.verbs;

import java.util.regex.*;

import edu.psu.compbio.seqcode.gse.datasets.general.NamedRegion;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.general.StrandedRegion;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;

/**
 * @author shaun
 */
public class StrandedRegionParser implements Mapper<String,Region> {
    
    private static Pattern regPatt;
    
    static { 
        regPatt = Pattern.compile("(\\w+):(\\d+)-(\\d+):([^:\\s]+)");
    }
    
    private Genome genome;
    private int chromIndex, startIndex, endIndex, nameIndex, minLength;

    public StrandedRegionParser(Genome g) {
        genome = g;
        chromIndex = 0;
        startIndex = 1;
        endIndex = 2;
        minLength = (Math.max(chromIndex, Math.max(startIndex, endIndex))) + 1;
    }

    /* (non-Javadoc)
     * @see edu.psu.compbio.seqcode.gse.ewok.verbs.Filter#execute(null)
     */
    public StrandedRegion execute(String input) {
        String[] array = input.split("\\s+");
        String chrom = array[chromIndex];
        
        Matcher m = regPatt.matcher(chrom);
        if(m.matches()) { 
            chrom = m.group(1);
            chrom = chrom.replaceFirst("chr", "");
            int start = Integer.parseInt(m.group(2));
            int end = Integer.parseInt(m.group(3));
            char strand = '?';
            String strandstr = m.group(4);
			if(strandstr.length() > 0) { strand = strandstr.charAt(0); }
            return new StrandedRegion(genome, chrom, start, end, strand);
        } else { 
            System.err.println("Line \"" + input + "\" is incorrectly formatted for a StrandedRegion");
            return null;
        }
    }

}
