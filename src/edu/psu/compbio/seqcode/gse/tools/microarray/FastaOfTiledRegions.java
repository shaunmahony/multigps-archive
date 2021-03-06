package edu.psu.compbio.seqcode.gse.tools.microarray;

import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.ewok.*;
import edu.psu.compbio.seqcode.gse.ewok.nouns.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.*;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.utils.database.*;

import java.util.*;
import java.io.*;

/* Generates a FASTA file of the regions tiled by a specified array
   design.  

   usage:
   java edu.psu.compbio.seqcode.gse.tools.microarray.FastaOfTiledRegions \
    --species "$SC;SGDv1"
    --design "Sc 244k"
    --outfname tiledregions.fasta
    [--spacing 300] [--mincount 8]
 */

public class FastaOfTiledRegions {
    
    public static void main (String args[]) {
        String designName = Args.parseString(args,"design",null);
        String outfname = Args.parseString(args,"outfile",null);
        int spacing = Args.parseInteger(args,"spacing",200);
        int mincount = Args.parseInteger(args,"mincount",10);


        try {
            PrintStream outfile = new PrintStream(outfname);
            Pair<Organism,Genome> pair = Args.parseGenome(args);
            Organism organism = pair.car();
            Genome genome = pair.cdr();
            SequenceGenerator seqgen = new SequenceGenerator(genome);

            TiledRegionGenerator gen = new TiledRegionGenerator(designName,spacing,mincount);
            List<String> chromnames = genome.getChromList();
            for (int i = 0; i < chromnames.size(); i++) {
                Region region = new Region(genome,chromnames.get(i),1,genome.getChromLength(chromnames.get(i)));
                Iterator<Region> tiled = gen.execute(region);
                while (tiled.hasNext()) {
                    Region r = tiled.next();
                    String bases = seqgen.execute(r);
                    outfile.println(">" + r.toString());
                    int length = bases.length();
                    for (int j = 0; j < length; j += 60) {
                        int end = j + 60;
                        if (end > length) {end = length;}
                        outfile.println(bases.substring(j,end));
                    }
                
                }
            }
        
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
