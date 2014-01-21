package edu.psu.compbio.seqcode.gse.projects.dnaseseq;

import java.io.*;
import java.util.*;
import java.sql.SQLException;

import edu.psu.compbio.seqcode.gse.datasets.general.*;
import edu.psu.compbio.seqcode.gse.datasets.motifs.*;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.*;
import edu.psu.compbio.seqcode.gse.datasets.species.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.projects.readdb.ClientException;
import edu.psu.compbio.seqcode.gse.tools.motifs.WeightMatrixScanner;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseException;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;

/**
 * Train an HMM to recognize DNAse sensitive regions and the motif footprint in the sensitive regions.  
 *
 * java edu.psu.compbio.seqcode.gse.projects.dnaseq.ChipSeqHMMTrain --species "$HS;hg19" --wm "CTCF;JASPAR 11/09 MA0139.1" --chipseq "Crawford GM12878 CTCF GM12878 vs Input;GPS git d4021 1/17/11, mrc 2, mfc 2 round 1" --dnaseq "Crawford GM12878 DNaseSeq GM12878 against Input;statistical 1/11/11" --region "7:17m-18m" --cutoff .7 --distance 20 --minfold 1.5
 *
 * --region 1:3-5 (can give multiple)
 * --cutoff .7  motif cutoff as multiple of maximum LL score
 * --distance 20  max distance from binding call
 * --minfold 1.5 minimum fold change for hypersensitive region
 * --modelfile hmm.model  save hmm params to this file
 *
 * The analysis specified by --dnaseq isn't actually used; the
 * code just uses the same input alignments as that analysis.
 */


public class ChipSeqHMMTrain extends HMMTrain {
    private SeqAnalysis binding;
    private int bindingDistance;
    public ChipSeqHMMTrain() throws IOException, ClientException, SQLException{
        super();
    }
    public void parseArgs(String args[]) throws NotFoundException, SQLException, IOException {
        super.parseArgs(args);
        binding = Args.parseSeqAnalysis(args,"chipseq");
        bindingDistance = Args.parseInteger(args,"distance",10);
    }


    /** takes a sorted list of Regions and determines whether any of them
        overlaps the specified position
    */
    private List<Region> bindingEvents;
    protected void newTrainingRegion(Region region) {
        super.newTrainingRegion(region);
        bindingEvents = new ArrayList<Region>();
        try {
            for (SeqAnalysisResult result : binding.getResults(genome, region)) {
                if (result.foldEnrichment > 3) {
                    bindingEvents.add(result.expand(bindingDistance, bindingDistance));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.toString(),e);
        }

        Collections.sort(bindingEvents);
    }
    protected boolean hasBinding(int position) {
        if (bindingEvents.size() == 0) {
            return false;
        }
        for (int i = 0; i < bindingEvents.size(); i++) {
            Region r = bindingEvents.get(i);
            if (r.getStart() <= position && r.getEnd() >= position) {
                return true;
            }
        }
        return false;
    }
    public static void main(String args[]) throws Exception {
        ChipSeqHMMTrain train = new ChipSeqHMMTrain();
        train.parseArgs(args);
        train.train();
        train.printModel();
        train.saveModel();
    }

}
