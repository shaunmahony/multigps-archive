/**
 * 
 */
package edu.psu.compbio.seqcode.gse.utils.io;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import org.apache.log4j.Logger;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;

/**
 * @author rca Reads in a file with 1 point per line, expands the points to
 *         regions and writes the regions to a file
 */
public class Points2RegionsConverter {

  private static Logger logger = Logger.getLogger(Points2RegionsConverter.class);


  private static void usage() {
    String usage = "java Points2RegionsConverter --inputfile \"foo.txt\" --outputfile \"bar.txt\" --dist 50 --species \"Mus musculus;mm8\" [--overwrite true]";
    System.err.println(usage);
    logger.error(usage);
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    String infile = "/Users/rca/matlab scratch/Sing_Smad1_top25_peaks.txt";
    String outfile = "/Users/rca/matlab scratch/Sing_Smad1_top25_peaks_regions.txt";
    args = new String[] {"--inputfile", infile, "--outputfile", outfile, "--dist", "50", "--species", "Mus musculus;mm8"};
    
    ArgParser ap = new ArgParser(args);
    Genome genome = null;

    try {
      Pair<Organism, Genome> pair = Args.parseGenome(args);
      if(pair==null) {
        //Make fake genome... chr lengths provided???
        if(ap.hasKey("geninfo")) {
          genome = new Genome("Genome", new File(ap.getKeyValue("geninfo")), true);
        }
        else {
          logger.fatal("No genome provided; provide a Gifford lab DB genome name or a file containing chromosome name/length pairs.");
          Points2RegionsConverter.usage();
          System.exit(1);
        }
      } 
      else {
        genome = pair.cdr();
//        org = pair.car();
      }
    } 
    catch (NotFoundException nfex) {
      logger.fatal("", nfex);
      Points2RegionsConverter.usage();
      System.exit(-1);
    }
    
    String inputFilename = Args.parseString(args, "inputfile", null);
    String outputFilename = Args.parseString(args, "outputfile", null);
    boolean overwrite = Args.parseString(args, "overwrite", "false").equals("true");
    
    if (new File(outputFilename).exists() && !overwrite) {
      logger.fatal("Output File already exists. Specify a different output file or use the --overwrite flag to allow overwrite.");
      Points2RegionsConverter.usage();
      System.exit(1);
    }
    
    int dist = Args.parseInteger(args, "dist", -1);
    if (dist < 1) {
      logger.fatal("Must specify a positive distance to expand point");
      Points2RegionsConverter.usage();
      System.exit(-1);
    }
     
    logger.debug("Reading file " + inputFilename);
    Vector<Point> points = null;
    try { 
      points = DatasetsGeneralIO.readPointsFromFile(genome, inputFilename);
    }
    catch (IOException ioex) {
      logger.fatal(ioex);
      Points2RegionsConverter.usage();
      System.exit(-1);
    }
     
    logger.debug(points.size() + " points read. Converting to regions...");
    Vector<Region> regions = new Vector<Region>(points.size());
    for (Point point : points) {
      Region region = point.expand(dist);
      regions.add(region);
    }
    
    logger.debug("Writing file " + outputFilename);
    LineByLineFileWriter lblfw = new LineByLineFileWriter();
    try {
      lblfw.openFile(outputFilename);
      for (Region region : regions) {
        lblfw.writeLine(region.regionString());
      }      
    }
    catch (IOException ioex) {
      logger.fatal(ioex);
      System.exit(-1);      
    }
    finally {
      if (lblfw != null) {
        try {
          lblfw.closeFile();
        }
        catch (IOException ioex2) {
          logger.fatal(ioex2);
          System.exit(-1);      
        }
      }
    }
    logger.debug("done!");
  }
}
