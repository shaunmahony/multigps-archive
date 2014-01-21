package edu.psu.compbio.seqcode.gse.tools.core;

import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;

import java.io.*;
import java.util.Collection;

/**
 * Replaces a chromosome name with its ID in a column of tab-delimited text.
 *
 * eg,
 * cat PPG_D5_WCE-iHoxc6_1.bowtie.align.gz | gunzip | java edu.psu.compbio.seqcode.gse.projects.readdb.BowtieToReadDB | \
 *   java edu.psu.compbio.seqcode.gse.tools.core.ChromColumn2ID --species "$MM;mm8" --column 0
 *
 */

public class ChromColumn2ID {
    
    public static void main(String args[]) throws Exception {
        Genome genome = Args.parseGenome(args).cdr();
        Collection<String> fs = Args.parseStrings(args,"column");
        boolean reverse = Args.parseFlags(args).contains("reverse");
        int fields[] = new int[fs.size()];
        int i = 0;
        for (String fieldstring : fs) {
            fields[i++] = Integer.parseInt(fieldstring);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        StringBuffer out = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            String pieces[] = line.split("\\t");
            for (i = 0; i < fields.length; i++) {
                int field = fields[i];
                if (reverse) {
                    pieces[field] = genome.getChromName(Integer.parseInt(pieces[field]));
                } else {
                    pieces[field] = Integer.toString(genome.getChromID(pieces[field].replaceAll("^chr","")));
                }
            }
            out.delete(0,out.length());
            out.append(pieces[0]);
            for (i = 1; i < pieces.length ; i++) {
                out.append("\t" + pieces[i]);
            }
            System.out.println(out.toString());
        }
        genome.close();
    }
}