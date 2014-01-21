package edu.psu.compbio.seqcode.gse.tools.seqdata;

import java.io.IOException;
import java.sql.SQLException;

import edu.psu.compbio.seqcode.gse.datasets.seqdata.SeqAnalysisResult;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSParser;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSPeak;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseException;

/**
 * See AnalysisImporter docs.  Command line options are the same; the only difference
 * is that StatisticalAnalysisImporter parses the statistical peak finder
 * (ChipSeqPeakFinder) output.
 */

public class StatisticalAnalysisImporter extends AnalysisImporter {

    /* oracle complains about underflow if we don't limit the pvalues.  the actual 
       min value is somewhere between E-100 and E-200, but I didn't bother tracking 
       it down more closely since I don't think the difference really matters
    */
    public final static double minpval = Math.pow(10,-100);

    private int lineno = 0;

    public static void main(String args[]) throws NotFoundException, SQLException, DatabaseException, IOException {
        StatisticalAnalysisImporter importer = new StatisticalAnalysisImporter();
        importer.parseArgs(args);
        importer.run(System.in);
        importer.close();
    }
    public SeqAnalysisResult parseLine(String line) {
        if (lineno++ == 0) {
            if (!line.equals("Region\tWidth\tPeak\tPeakOffset\tMaxSigHits\tMaxBackHits\tScore\tTotalSigHits\tTotalBackHits\tOverRep\tClosestGene\tTSSDist\tOtherAnnotations")) {
                throw new RuntimeException("Invalid header line: " + line);
            }
            return null;
        }
        String pieces[] = line.split("\t");
        String pospieces[] = pieces[0].split("[\\:\\-]");
        String peakpieces[] = pieces[2].split(":");
        double pval = Double.parseDouble(pieces[6]);
        if (pval < minpval) {
            pval = minpval;
        }

        return new SeqAnalysisResult(getGenome(),
                                         pospieces[0],
                                         Integer.parseInt(pospieces[1]),
                                         Integer.parseInt(pospieces[2]),
                                         Integer.parseInt(peakpieces[1]),
                                         Double.parseDouble(pieces[7]),
                                         Double.parseDouble(pieces[8]),
                                         Double.parseDouble(pieces[9]),
                                         0.0,
                                         pval,
                                         Double.parseDouble(pieces[9]));
    }


}