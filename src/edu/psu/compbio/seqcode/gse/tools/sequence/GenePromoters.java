package edu.psu.compbio.seqcode.gse.tools.sequence;

import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.SQLException;

import edu.psu.compbio.seqcode.gse.datasets.general.*;
import edu.psu.compbio.seqcode.gse.datasets.species.*;
import edu.psu.compbio.seqcode.gse.ewok.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.*;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;

/**
 * cat gene_names.txt | java GenePromoters --species "$SC;Sigmav7" --genes s288cMapped --upstream 500 --downstream 100
 * cat gene_names.txt | java GenePromoters --species "$SC;Sigmav7" --genes s288cMapped --upstream 500 --downstream 100 --fasta
 * java GenePromoters --species "$SC;Sigmav7" --genes s288cMapped --upstream 500 --downstream 100 --fasta --allgenes
 *
 * [--fasta] fasta formatted output
 * [--dontoverlaporfs] don't overlap annotated genes or some other features like refBase and sgdOther
 * [--phastmax] don't include regions with a phastcons score greater than this value
 * [--phastmin] only include regions with a phastcons score greater than this value
 * [--maskkeep] provide "name;score" for a ScoredRegionGenerator and minimum score to specify regions to keep
 * [--maskout] provide "name;score" for a ScoredRegionGenerator and maximum score to specify regions to keep
 * [--minlen] specify a minimum length for the output regions
 *
 *
 */

public class GenePromoters {

    private int upstream, downstream, minlen;
    private List<RefGeneGenerator> geneGenerators;
    private SequenceGenerator seqgen;
    private Genome genome;
    private boolean allGenes, toFasta, dontOverlapOrfs;
    private GeneToPromoter promgen;
    private Map<Expander<Region,? extends ScoredRegion>, Double> maskKeep, maskOut;

    public static void main(String args[]) throws Exception {
        GenePromoters gp = new GenePromoters();
        gp.parseArgs(args);        
        gp.run();
    }
    public GenePromoters() {}
    public void parseArgs(String args[]) throws NotFoundException {
        geneGenerators = Args.parseGenes(args);
        for (RefGeneGenerator r : geneGenerators) {
            r.retrieveExons(false);
        }        
        upstream = Args.parseInteger(args,"upstream",10000);
        downstream = Args.parseInteger(args,"downstream",2000);
        minlen = Args.parseInteger(args,"minlen",0);
        genome = Args.parseGenome(args).getLast();        
        seqgen = new SequenceGenerator(genome);
        allGenes = Args.parseFlags(args).contains("allgenes");
        toFasta = Args.parseFlags(args).contains("fasta");
        dontOverlapOrfs = Args.parseFlags(args).contains("dontoverlaporfs");
        if (dontOverlapOrfs) {
            Collection<Expander<Region, ? extends Region>> dontoverlap =
                new ArrayList<Expander<Region, ? extends Region>>();
            for (RefGeneGenerator g : geneGenerators) {
                dontoverlap.add(g);
            }
            try {
                RegionExpanderFactoryLoader<NamedTypedRegion> annotLoader =
                    new RegionExpanderFactoryLoader<NamedTypedRegion>("annots");
                RegionExpanderFactory factory = annotLoader.getFactory(genome,
                                                                       "repBase");
                dontoverlap.add(factory.getExpander(genome));
                System.err.println("Also filtering against repbase");
            } catch (Exception e) {
                System.err.println("Trying to add repbase to list of things not to overlap (will continue):");
                e.printStackTrace();
            }
            try {
                RegionExpanderFactoryLoader<NamedTypedRegion> annotLoader =
                    new RegionExpanderFactoryLoader<NamedTypedRegion>("annots");
                RegionExpanderFactory factory = annotLoader.getFactory(genome,
                                                                       "sgdOther");
                dontoverlap.add(factory.getExpander(genome));
                System.err.println("Also filtering against sgdOther");
            } catch (Exception e) {
                System.err.println("Trying to add sgdOther to list of things not to overlap (will continue):");
                e.printStackTrace();
            }
            
            promgen = new GeneToPromoter(upstream, downstream, dontoverlap);
        } else {
            promgen = new GeneToPromoter(upstream, downstream);
        }
        maskKeep = new HashMap<Expander<Region,? extends ScoredRegion>, Double>();
        maskOut = new HashMap<Expander<Region,? extends ScoredRegion>, Double>();
        for (String s : Args.parseStrings(args,"phastmin")) {
            String pieces[] = s.split(";");
            if (!pieces[1].equals("0")) {
                maskKeep.put(new PhastConsGenerator(genome, pieces[0]), Double.parseDouble(pieces[1]));
            }
        }
        for (String s : Args.parseStrings(args,"phastmax")) {
            String pieces[] = s.split(";");
            if (!pieces[1].equals("0")) {
                maskOut.put(new PhastConsGenerator(genome, pieces[0]), Double.parseDouble(pieces[1]));
            }
        }

        for (String s : Args.parseStrings(args,"maskkeep")) {
            String pieces[] = s.split(";");
            maskKeep.put(new ScoredRegionGenerator(genome, pieces[0]), Double.parseDouble(pieces[1]));
        }
        for (String s : Args.parseStrings(args,"maskout")) {
            String pieces[] = s.split(";");
            maskOut.put(new ScoredRegionGenerator(genome, pieces[0]), Double.parseDouble(pieces[1]));
        }

    }
    public Collection<Region> getMasksForRegion(Region r) {
        Collection<Region> maskedout = new ArrayList<Region>();
        if (maskOut.size() == 0 && maskKeep.size() == 0) {
            return maskedout;
        }
        if (maskOut.size() == 0) {
            maskedout.add(r);
        } else {
            for (Expander<Region,? extends ScoredRegion> exp : maskOut.keySet()) {
                Iterator<? extends ScoredRegion> iter = exp.execute(r);
                while (iter.hasNext()) {
                    ScoredRegion sr = iter.next();
                    if (sr.getScore() >= maskOut.get(exp)) {maskedout.add(sr);}                    
                }
            }
        }
        for (Expander<Region,? extends ScoredRegion> exp : maskKeep.keySet()) {
            Iterator<? extends ScoredRegion> iter = exp.execute(r);
            while (iter.hasNext()) {
                ScoredRegion sr = iter.next();
                if (sr.getScore() >= maskKeep.get(exp)) {
                    ArrayList<Region> newmo = new ArrayList<Region>();
                    for (Region old : maskedout) {
                        if (old.overlaps(sr)) {
                            if (old.contains(sr)) {
                                newmo.add(new Region(old.getGenome(), old.getChrom(), old.getStart(), sr.getStart()));
                                newmo.add(new Region(old.getGenome(), old.getChrom(), sr.getEnd(), old.getEnd()));
                            } else if (old.before(sr)) {
                                newmo.add(new Region(old.getGenome(), old.getChrom(), old.getStart(), sr.getStart()));
                            } else {
                                newmo.add(new Region(old.getGenome(), old.getChrom(), Math.min(sr.getEnd(), old.getEnd()), Math.max(sr.getEnd(), old.getEnd())));
                            }
                        } else {
                            newmo.add(old);
                        }
                    }
                    maskedout = newmo;                    
                }
            }
        }
        return maskedout;
    }
    public char[] getMaskedRegion(Region r, Collection<Region> masks) {
        boolean[] keep = new boolean[r.getWidth()];
        for (int i = 0; i < keep.length; i++) {
            keep[i] = true;
        }
        for (Region mask : masks) {
            Region overlap = mask.getOverlap(r);
            for (int pos = overlap.getStart() - r.getStart(); pos < overlap.getEnd() - r.getStart(); pos++) {
                keep[pos] = false;
            }
        }
        char[] chars = seqgen.execute(r).toUpperCase().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!keep[i]) {
                chars[i] = 'X';
            }
        }
        return chars;
    }
    public void run() throws IOException {
        if (allGenes) {
            ChromRegionIterator chroms = new ChromRegionIterator(genome);
            while (chroms.hasNext()) {
                Region chrom = chroms.next();
                /* we'll use all the gene generators provided but don't want to output duplicate regions.
                   seen is keyed on 5' and contains a list of 3' ends that have already been output.
                */
                Map<Integer,List<Integer>> seen = new HashMap<Integer,List<Integer>>();
                for (RefGeneGenerator refgene : geneGenerators) {
                    Iterator<Gene> iter = refgene.execute(chrom);
                    while (iter.hasNext()) {
                        Gene g = iter.next();
                        if (seen.containsKey(g.getFivePrime()) &&
                            seen.get(g.getFivePrime()).contains(g.getThreePrime())) {
                            continue;
                        } else {
                            if (!seen.containsKey(g.getFivePrime())) {
                                seen.put(g.getFivePrime(), new ArrayList<Integer>());
                            }
                            seen.get(g.getFivePrime()).add(g.getThreePrime());
                        }
                        output(promgen.execute(g));
                    }
                }
            }
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = null;
            while ((line = reader.readLine()) != null) {
                Gene g = null;
                String pieces[] = line.split("\\t");
                for (int i = 0; i < pieces.length; i++) {
                    for (RefGeneGenerator refgene : geneGenerators) {
                        Iterator<Gene> iter = refgene.byName(pieces[i]);
                        while (iter.hasNext()) {
                            if (g == null) {
                                g = iter.next();
                            } else {
                                iter.next();
                            }
                        }
                        if (g != null) {
                            break;
                        }
                        
                    }
                    if (g != null) {
                            break;
                    }                    
                }
                if (g != null) {
                    g.setName(line);
                    output(promgen.execute(g));
                }
            }
        }
    }
    public void output(NamedStrandedRegion r) {
        char[] c = getMaskedRegion(r, getMasksForRegion(r));
        if (r.getWidth() < minlen) {
            return;
        }

        if (toFasta) {
            System.out.println(">" + r.getName());
            for (int pos = 0; pos < c.length; pos += 60) {
                for (int i = 0; i < 60 && pos + i < c.length; i++) {
                    System.out.print(c[pos+i]);
                }
                System.out.println();
            }
        } else {
            System.out.println(String.format("%s\t%s:%d-%d:%s",
                                             r.toString(),
                                             r.getChrom(), r.getStart(), r.getEnd(), r.getStrand()));
        }
    }

}