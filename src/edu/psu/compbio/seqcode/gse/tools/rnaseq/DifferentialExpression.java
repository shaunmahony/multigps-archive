package edu.psu.compbio.seqcode.gse.tools.rnaseq;

import java.util.*;
import java.io.IOException;
import java.sql.SQLException;
import cern.jet.random.Gamma;
import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;
import edu.psu.compbio.seqcode.gse.datasets.general.*;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.*;
import edu.psu.compbio.seqcode.gse.datasets.species.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.*;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;

/**
 * DifferentialExpression --species "$SC;Sigmav6" --one "Sigma polyA RNA, haploid from 2/9/09;3/17/09;bowtie --best -m 100 -k 100" \
 *                        --two "Sigma polyA RNA, tetraploid from 2/9/09;3/17/09;bowtie --best -m 100 -k 100" --genes sgdGene \
 *                        [--bothstrands] [--byweight] [--flipgenetrands] [--exons] [--lengthnorm]
 *
 * Output columns are
 * - gene name
 * - count one
 * - count two
 * - frequency in sample count (count / total count)
 * - frequency two
 * - pval of count two given frequency one
 * - pval of count one given frequency two
 */

public class DifferentialExpression {

    SeqDataLoader loader;
    List<SeqAlignment> one, two;
    RefGeneGenerator genes;
    Genome genome;
    boolean bothstrands, byweight, exons, lengthnorm;

    public static void main(String args[]) throws Exception {
        DifferentialExpression d = new DifferentialExpression();
        d.parseArgs(args);
        d.run();
        d.loader.close();
    }

    public DifferentialExpression() throws SQLException, IOException {
        loader = new SeqDataLoader();
        one = new ArrayList<SeqAlignment>();
        two = new ArrayList<SeqAlignment>();
    }
    public void parseArgs(String args[]) throws SQLException, NotFoundException {
        genome = Args.parseGenome(args).cdr();
        List<SeqLocator> locators = Args.parseSeqExpt(args,"one");
        for (SeqLocator locator : locators) {
            one.addAll(loader.loadAlignments(locator,genome));
        }
        locators = Args.parseSeqExpt(args,"two");
        for (SeqLocator locator : locators) {
            two.addAll(loader.loadAlignments(locator,genome));
        }
        // parseGenes returns a list of genes; just take the first one
        genes = Args.parseGenes(args).get(0);
        if (one.size() == 0) {
            throw new NotFoundException("--one didn't match any alignments");
        }
        if (two.size() == 0) {
            throw new NotFoundException("--two didn't match any alignments");
        }
        bothstrands = Args.parseFlags(args).contains("bothstrands");
        byweight = Args.parseFlags(args).contains("byweight");
        exons = Args.parseFlags(args).contains("exons");
        lengthnorm = Args.parseFlags(args).contains("lengthnorm");
        if (exons) {
            genes.retrieveExons(true);
        }
    }

    public void run() throws SQLException, IOException {
        ChromRegionIterator chroms = new ChromRegionIterator(genome);
        double totalweightone = getWeight(one), totalweighttwo = getWeight(two);
        double totalcountone, totalcounttwo;
        if (byweight) {
            totalcountone = getWeight(one);
            totalcounttwo = getWeight(two);
        } else {
            totalcountone = getCount(one);
            totalcounttwo = getCount(two);
        }

        System.err.println("Total weight one is " + totalweightone + " hits one is " + totalcountone);
        System.err.println("Total weight two is " + totalweighttwo + " hits two is " + totalcounttwo);
        
        Binomial binomial = new Binomial(100,.1,new DRand((int)(System.currentTimeMillis() % 0xFFFFFFFF)));

        List<StrandedRegion> geneRegions = new ArrayList<StrandedRegion>();
        while (chroms.hasNext()) {
            Region chrom = chroms.next();
            Iterator<Gene> geneiter = genes.execute(chrom);
            while (geneiter.hasNext()) {
                Gene g = geneiter.next();
                double countone = 0, counttwo = 0;
                int length = 0;
                geneRegions.clear();
                if (exons && g instanceof ExonicGene) {
                    Iterator<Region> exoniter = ((ExonicGene)g).getExons();
                    while (exoniter.hasNext()) {
                        geneRegions.add(new StrandedRegion(exoniter.next(), g.getStrand()));
                    }
                } else {
                    geneRegions.add(g);
                }
                for (StrandedRegion r : geneRegions) {
                    length += r.getWidth();
                    try {
                        if (bothstrands) {
                            if (byweight) {
                                countone += loader.weightByRegion(one,(Region)r);
                                counttwo += loader.weightByRegion(two,(Region)r);
                            } else {
                                countone += loader.countByRegion(one,(Region)r);
                                counttwo += loader.countByRegion(two,(Region)r);
                            }
                        } else {
                            if (byweight) {
                                countone += loader.weightByRegion(one,r);
                                counttwo += loader.weightByRegion(two,r);
                            } else {
                                countone += loader.countByRegion(one,r);
                                counttwo += loader.countByRegion(two,r);
                            }
                        }

                    } catch (IllegalArgumentException e) {
                        // this is just it complaining about invalid chromosomes
                    }
                }

                if (countone < 2 && counttwo < 2) { continue; }
                if (countone < 2) {countone = 2;}
                if (counttwo < 2) {counttwo = 2;}

                if (lengthnorm) {
                    countone = countone * 1000.0 / length;
                    counttwo = counttwo * 1000.0 / length;
                }

                
                double pone = countone / totalcountone;
                double ptwo = counttwo / totalcounttwo;

                binomial.setNandP((int)totalcountone, ptwo);
                double cdf = binomial.cdf((int)countone);
                double pvalonegiventwo = Math.min(cdf, 1 - cdf);
                binomial.setNandP((int)totalcounttwo, pone);
                cdf = binomial.cdf((int)counttwo);
                double pvaltwogivenone = Math.min(cdf, 1 - cdf);
                System.out.println(String.format("%s\t%.0f\t%.0f\t%.4e\t%.4e\t%.4e\t%.4e", g.toString(),
                                                 countone,
                                                 counttwo,
                                                 pone, ptwo,
                                                 pvaltwogivenone,
                                                 pvalonegiventwo));                
            }

        }

    }
    private double getWeight(Collection<SeqAlignment> alignments) throws SQLException, IOException {
        double weight = 0;
        for (SeqAlignment a : alignments) {
            weight += loader.weighAllHits(a);
        }
        return weight;
    }
    private int getCount(Collection<SeqAlignment> alignments) throws SQLException, IOException {
        int count = 0;
        for (SeqAlignment a : alignments) {
            count += loader.countAllHits(a);
        }
        return count;
    }

}