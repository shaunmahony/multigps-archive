package edu.psu.compbio.seqcode.gse.projects.readdb;

import org.apache.commons.cli.*;
import java.util.*;
import java.io.*;

/** 
 * <p>Query hits from the database.  Reads chrom:start-stop:strand values from
 * stdin.  Repeats them on stdout along with the hit positions.
 *
 * <p>Usage:
 * <pre>
 *  echo "1:100-6000" | java edu.psu.compbio.seqcode.gse.projects.readdb.Query [--hostname nanog.csail.mit.edu --port 52000 --user foo --passwd bar] [--quiet] --align 100
 *  </pre>
 * <ul>
 * <li>--quiet means don't print any output.  This is useful for testing the query performance
 * without worrying about the time it takes to print the output.
 * <li>--wiggle means output in wiggle format
 * <li>--bed means output in BED format
 * <li>--paired means to query the paired reads.
 * <li>--noheader means to skip printing the input region in the outpu
 * <li>--right means to query the right side reads rather than left.
 */

public class Query {

    private String alignname;
    private String hostname;
    private String username, password;
    private int portnum, histogram = -1;
    private boolean quiet, weights, paired, isleft, noheader, bed, wiggle;
    

    public static void main(String args[]) throws Exception {
        Query query = new Query();
        query.parseArgs(args);
        query.run(System.in);
    }

    public void parseArgs(String args[]) throws IllegalArgumentException, ParseException {
        Options options = new Options();
        options.addOption("H","hostname",true,"server to connect to");
        options.addOption("P","port",true,"port to connect to");
        options.addOption("a","align",true,"alignment name");
        options.addOption("u","user",true,"username");
        options.addOption("p","passwd",true,"password");
        options.addOption("q","quiet",false,"quiet: don't print output");
        options.addOption("w","weights",false,"get and print weights in addition to positions");
        options.addOption("g","histogram",true,"produce a histogram with this binsize instead of printing all read positions");
        options.addOption("d","paired",false,"work on paired alignment?");
        options.addOption("r","right",false,"query right side reads when querying paired alignments");
        options.addOption("N","noheader",false,"skip printing the query header");
        options.addOption("W","wiggle",true,"output in wiggle format with the specified bin format");
        options.addOption("B","bed",false,"output in BED format");
        options.addOption("h","help",false,"print help message");
        CommandLineParser parser = new GnuParser();
        CommandLine line = parser.parse( options, args, false );            
        if (line.hasOption("help")) {
            printHelp();
            System.exit(0);
        }
        if (line.hasOption("port")) {
            portnum = Integer.parseInt(line.getOptionValue("port"));
        } else {
            portnum = -1;
        }
        if (line.hasOption("hostname")) {
            hostname = line.getOptionValue("hostname");
        } else {
            hostname = null;
        }
        if (line.hasOption("align")) {
            alignname = line.getOptionValue("align");
        } else {
            alignname = null;
        }
        if (line.hasOption("user")) {
            username = line.getOptionValue("user");
        } else {
            username = null;
        }
        if (line.hasOption("passwd")) {
            password = line.getOptionValue("passwd");
        } else {
            password = null;
        }
        if (line.hasOption("histogram")) {
            histogram = Integer.parseInt(line.getOptionValue("histogram"));
        }
        quiet = line.hasOption("quiet");
        weights = line.hasOption("weights");
        paired = line.hasOption("paired");
        isleft = !line.hasOption("right");
        noheader = line.hasOption("noheader");
        bed = line.hasOption("bed");
        if (bed) {
            histogram = 0;
            noheader = true;
            if (paired) {
                System.err.println("Can't do BED formatted output in paired-end mode");
            }
        }
        if (line.hasOption("wiggle")) {
            wiggle = true;
            histogram = Integer.parseInt(line.getOptionValue("wiggle"));
        }
    }
    public void printHelp() {
        System.out.println("Query ReadDB.  Regions are read on STDIN and output is printed on STDOUT.");
        System.out.println("usage: java edu.psu.compbio.seqcode.gse.projects.readdb.Query --align alignmentname < regions.txt");
        System.out.println(" [--quiet]  don't print any output.  Useful for performance testing without worrying about output IO time");
        System.out.println(" [--weights] include weights in output or use weights for histogram");
        System.out.println(" [--paired] query paired reads");
        System.out.println(" [--right] when querying paired reads for histograms, use right read rather than left");
        System.out.println(" [--histogram 10] output a histogram of read counts or weights using a 10bp bin size");
        System.out.println(" [--noheader] don't output query regions in the output");
        System.out.println(" [--bed] output hit positions in BED format (doesn't work with paired reads)");
        System.out.println(" [--wiggle 10] output a histogram in wiggle format with 10bp bin size");
        System.out.println("");
        System.out.println("Lines in the input should be of them form");
        System.out.println("3:1000-2000");
    }

    public void run(InputStream instream) throws IOException, ClientException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
        String line;
        Client client;
        if (hostname != null && portnum != -1 && username != null && password != null) {
            client = new Client(hostname,
                                portnum,
                                username,
                                password);
        } else {
            client = new Client();
        }

        while ((line = reader.readLine()) != null) {            
            try {
                String pieces[] = line.split("[\\:]");
                int chr = Integer.parseInt(pieces[0].replaceFirst("^chr",""));
                Boolean strand = pieces.length >= 3 ? pieces[2].equals("+") : null;
                pieces = pieces[1].split("\\-");
                int start = Integer.parseInt(pieces[0]);
                int stop = Integer.parseInt(pieces[1]);
                if (wiggle) {
                    System.out.println(String.format("variableStep chrom=chr%d span=%d",chr, histogram));
                }

                if (histogram > 0) {
                    TreeMap<Integer,Integer> hits = client.getHistogram(alignname,
                                                                        chr,
                                                                        paired,
                                                                        false,
                                                                        histogram,
                                                                        start,
                                                                        stop,
                                                                        null,
                                                                        strand);
                    TreeMap<Integer,Float> weightsmap = null;
                    if (weights) {
                        weightsmap = client.getWeightHistogram(alignname,
                                                               chr,
                                                               paired,
                                                               false,
                                                               histogram,
                                                               start,
                                                               stop,
                                                               null,
                                                               strand);
                    }
                    if (!quiet) {
                        for (int i : hits.keySet()) {
                            if (weights) {
                                if (wiggle) {
                                    System.out.println(String.format("%d\t%f", i,weightsmap.get(i)));  
                                } else {
                                    System.out.println(String.format("%d\t%d\t%f", i, hits.get(i), weightsmap.get(i)));  
                                }
                            } else {
                                System.out.println(String.format("%d\t%d", i, hits.get(i)));
                            }
                        }
                    }
                } else {
                    if (paired) {
                        List<PairedHit> hits = client.getPairedHits(alignname,
                                                                    chr,
                                                                    isleft,
                                                                    start,
                                                                    stop,
                                                                    null,
                                                                    strand);
                        if (!quiet) {
                            if (!noheader) {
                                System.out.println(line);
                            }
                            for (PairedHit h : hits) {                                
                                System.out.println(h.toString());
                            }
                        }
                    } else {
                        List<SingleHit> hits = client.getSingleHits(alignname,
                                                                    chr,
                                                                    start,
                                                                    stop,
                                                                    null,
                                                                    strand);
                        if (!quiet) {
                            if (!noheader) {
                                System.out.println(line);
                            }
                            for (SingleHit h : hits) {
                                if (bed) {//BED is 0-based and half-open by definition
                                    System.out.println(String.format("chr%d\t%d\t%d\thit\t%f\t%s\n",
                                                                     h.chrom, 
                                                                     h.strand ? h.pos-1 : h.pos - h.length -1,
                                                                     h.strand ? h.pos + h.length : h.pos,
                                                                     h.weight,
                                                                     h.strand ? "+" : "-"));
                                                                     
                                                                     
                                } else {
                                    System.out.println(h.toString());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("FROM " + line);
                e.printStackTrace();
            }
        }
        client.close();
    }


    
}