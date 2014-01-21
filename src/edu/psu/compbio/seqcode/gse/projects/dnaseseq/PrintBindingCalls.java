package edu.psu.compbio.seqcode.gse.projects.dnaseseq;

import java.util.*;
import java.sql.SQLException;

import edu.psu.compbio.seqcode.gse.datasets.general.*;
import edu.psu.compbio.seqcode.gse.datasets.motifs.*;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.*;
import edu.psu.compbio.seqcode.gse.datasets.species.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.tools.motifs.WeightMatrixScanner;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;

/**
 * Print binding calls for comparison to HMM output.  If a motif is provided,
 * then the positions are the motif positions that are close to binding events.
 * If no motif is specified, then just output the binding calls
 */

public class PrintBindingCalls {
    private WeightMatrix motif;
    private SeqDataLoader loader;
    private SeqAnalysis binding, dnaseq;
    private Genome genome;
    private SequenceGenerator seqgen;
    private float motifCutoff;
    private WMHitStartComparator hitcomp;
    private List<Region> regions;
    private int bindingDistance;

    public PrintBindingCalls() {
        hitcomp = new WMHitStartComparator();
    }
    public void parseArgs(String args[]) throws SQLException, NotFoundException {
        binding = Args.parseSeqAnalysis(args,"chipseq");
        bindingDistance = Args.parseInteger(args,"distance",10);
        dnaseq = null;
        try {
            dnaseq = Args.parseSeqAnalysis(args,"dnaseq");
        } catch (RuntimeException e) {
            // don't worry, this just means none was specified
        }
        genome = Args.parseGenome(args).cdr();
        Collection<WeightMatrix> matrices = Args.parseWeightMatrices(args);
        Iterator<WeightMatrix> iter = matrices.iterator();
        if (iter.hasNext() && matrices.size() < 10) {
            motif = iter.next();
        } else {
            motif = null;
        }
        if (motif != null) {
            MarkovBackgroundModel bgModel = null;
            String bgmodelname = Args.parseString(args,"bgmodel","whole genome zero order");
            BackgroundModelMetadata md = BackgroundModelLoader.getBackgroundModel(bgmodelname,
                                                                                  1,
                                                                                  "MARKOV",
                                                                                  genome.getDBID());
            if (bgModel == null) {
                motif.toLogOdds();
            } else {
                motif.toLogOdds(bgModel);
            }
            motifCutoff = (float)(motif.getMaxScore() * Args.parseDouble(args,"cutoff",.7));
        }
        seqgen = new SequenceGenerator(genome);
        seqgen.useLocalFiles(true);
        seqgen.useCache(true);
        regions = Args.parseRegionsOrDefault(args);
    }
    public void run() throws SQLException {

        for (Region region : regions) {
            List<SeqAnalysisResult> dnaseqResults = null;
            if (dnaseq != null) {
                dnaseqResults = new ArrayList<SeqAnalysisResult>();
                for (SeqAnalysisResult r : dnaseq.getResults(genome, region)) {
                    dnaseqResults.add(r);
                }
            }
            

            if (motif == null) {
                for (SeqAnalysisResult result : binding.getResults(genome, region)) {
                    boolean print = true;
                    if (dnaseq != null) {
                        print = false;
                        for (SeqAnalysisResult d : dnaseqResults) {
                            if (d.overlaps(result)) {
                                print = true;
                                break;
                            }
                        }
                    }
                    if (print) {
                        System.out.println(result.toString());
                    }
                }
            } else {
                char[] sequence = seqgen.execute(region).toCharArray();
                List<WMHit> hits = WeightMatrixScanner.scanSequence(motif, motifCutoff, sequence);
                Collections.sort(hits, hitcomp);
                int[] motifHitStarts = new int[hits.size()];
                for (int i = 0; i < hits.size(); i++) {
                    motifHitStarts[i] = hits.get(i).getStart() + region.getStart();
                    System.err.println("Motif at " + motifHitStarts[i]);
                }
                List<Region> bindingEvents = new ArrayList<Region>();
                for (SeqAnalysisResult result : binding.getResults(genome, region)) {
                    bindingEvents.add(result.expand(bindingDistance, bindingDistance));
                }
                System.err.println("Binding " + bindingEvents);
                System.err.println("Dnaseq is " + dnaseq);
                for (int i = 0; i < motifHitStarts.length; i++) {
                    Region motifRegion = new Region(region.getGenome(), region.getChrom(), motifHitStarts[i], motifHitStarts[i] + motif.length());
                    for (Region b : bindingEvents) {
                        if (b.overlaps(motifRegion)) {
                            boolean print = true;
                            if (dnaseq != null) {
                                print = false;
                                for (SeqAnalysisResult d : dnaseqResults) {
                                    if (d.overlaps(b)) {
                                        print = true;
                                        break;
                                    }
                                }
                            }
                            if (print) {                            
                                System.out.println(region.getChrom() + ":" + motifHitStarts[i] + "-" + (motifHitStarts[i] + motif.length()));
                                break;
                            }
                        }
                    }
                }

            }


            

        }

    }

    public static void main(String args[]) throws NotFoundException, SQLException {
        PrintBindingCalls pbc = new PrintBindingCalls();
        pbc.parseArgs(args);
        pbc.run();
    }


}