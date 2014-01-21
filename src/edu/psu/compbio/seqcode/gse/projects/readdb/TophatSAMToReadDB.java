package edu.psu.compbio.seqcode.gse.projects.readdb;

import java.io.*;
import java.util.*;

import org.apache.commons.cli.*;

import net.sf.samtools.*;
import net.sf.samtools.util.CloseableIterator;

/**
 * Reads SAM or BAM data on stdin.
 * Produces a file on stdout in the format expected by ImportHits.
 * The weight for a hit is 1/(# of hits for that read)
 * 
 * Options:	--nosuboptimal (flag to only take the hits with the minimum number of mismatches)
 * 			--uniquehits (flag to only print 1:1 read to hit mappings)
 * 			--pairedend (flag to print pairs)
 * 			--junctions (flag to print junction mapping reads as pairs)
 * 
 * nosuboptimal is applied before uniquehits
 */

public class TophatSAMToReadDB {

    public static boolean uniqueOnly;
    public static boolean filterSubOpt;
    public static boolean inclPairedEnd;
    public static boolean inclJunction;

    public static void main(String args[]) throws IOException, ParseException {
        Options options = new Options();
        options.addOption("u","uniquehits",false,"only output hits with a single mapping");
        options.addOption("s","nosuboptimal",false,"do not include hits whose score is not equal to the best score for the read");
        options.addOption("p","pairedend",false,"output paired-end hits");
        options.addOption("j","junctions",false,"output junction mapping reads (reads with gaps)");
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args, false );            
    	uniqueOnly = cl.hasOption("uniquehits");
    	filterSubOpt = cl.hasOption("nosuboptimal");
    	inclPairedEnd = cl.hasOption("pairedend");
    	inclJunction = cl.hasOption("junctions");
    	SAMFileReader.setDefaultValidationStringency(SAMFileReader.ValidationStringency.SILENT);
        SAMFileReader reader = new SAMFileReader(System.in);
        reader.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);
        CloseableIterator<SAMRecord> iter = reader.iterator();
        while (iter.hasNext()) {
            SAMRecord record = iter.next();
            if (record.getReadUnmappedFlag()) {continue; }
            processRecord(record);
        }
        iter.close();
        reader.close();
    }       
    public static void processRecord(SAMRecord record) {

    	if (uniqueOnly && record.getIntegerAttribute("NH") > 1) {
            return;
        }
    	float weight = 1/(float)record.getIntegerAttribute("NH");
	    
    	if(inclPairedEnd || inclJunction){
    		/*
    		 * Okay, so the paired-end part is just hacked together for now.
    		 * It only accepts true pairs.
    		 * It also assumes that the left and right mates have the same length, 
    		 * and that there are no gaps in the second mate alignment (SAM doesn't store the paired read's end)
    		 * Note: if you change this, you may have to change the SAMStats output also
    		 */
    		if(inclPairedEnd){
	    		if(!record.getNotPrimaryAlignmentFlag() && record.getFirstOfPairFlag() && record.getProperPairFlag() && record.getReadPairedFlag()){
	                boolean neg = record.getReadNegativeStrandFlag();
	                boolean mateneg = record.getMateNegativeStrandFlag();
	                String len = record.getReadLength() + "\t";
	                System.out.println(
	                        record.getReferenceName() + "\t" +
	                        (neg ? 
	                         record.getAlignmentEnd() : 
	                         record.getAlignmentStart()) + "\t" +
	                        (neg ? "-\t" : "+\t") + 
	                        len +
	                        
	                        record.getMateReferenceName() + "\t" +
	                        (mateneg ? 
	                         record.getMateAlignmentStart()+record.getReadLength()-1 : 
	                         record.getMateAlignmentStart()) + "\t" +
	                        (mateneg ? "-\t" : "+\t") +
	                        len + 
	                        weight +"\t"+
	                        1);
	    		}
    		}
    		
    		/*
    		 * Outputs as paired alignments those reads that are aligned in >2 blocks
    		 * Note: if you change this, you may have to change the SAMStats output also
    		 */
    		if(inclJunction){
	    		List<AlignmentBlock> blocks = record.getAlignmentBlocks();
	    		if(blocks.size()>=2){
	    			for(int ab=0; ab<blocks.size()-1; ab++){
		    			AlignmentBlock lBlock = blocks.get(ab);
		    		   	int lStart = lBlock.getReferenceStart();
		    		   	int lEnd = lStart + lBlock.getLength()-1;
		    		   	int lLen = lBlock.getLength();
		    		   	AlignmentBlock rBlock = blocks.get(ab+1);
		    		   	int rStart = rBlock.getReferenceStart();
		    		   	int rEnd = rStart + rBlock.getLength()-1;
		    		   	int rLen = rBlock.getLength();
		                boolean neg = record.getReadNegativeStrandFlag();
		                String refname = record.getReferenceName() + "\t"; 
		    		   	System.out.println(
		                                   refname +
		                                   (neg ? lEnd : lStart) + "\t" +
		                                   (neg ? "-\t" : "+\t") +
		                                   lLen + "\t" +
		                                   refname + 
		                                   (neg ? rEnd : rStart) + "\t" +
		                                   (neg ? "-\t" : "+\t") +
		                                   rLen + "\t" +
		                                   weight +"\t"+
		                                   0);
	    			}
	    		}
    		}
    	}else{ //Just output reads (or read parts)
    		List<AlignmentBlock> blocks = record.getAlignmentBlocks();
		    for(int a=0; a<blocks.size(); a++){ //Iterate over alignment blocks
		    	AlignmentBlock currBlock = blocks.get(a);
		    	int aStart = currBlock.getReferenceStart();
		    	int aEnd = aStart + currBlock.getLength()-1;
		    	int aLen = currBlock.getLength();
		    	boolean nearbyBlocks=true;
		    	while(nearbyBlocks && a<blocks.size()-1){
		    		if(blocks.get(a+1).getReferenceStart() - currBlock.getReferenceStart() < record.getReadLength()){
		    			aEnd = blocks.get(a+1).getReferenceStart() + blocks.get(a+1).getLength()-1;
		    			aLen += blocks.get(a+1).getLength();
		    			a++;
		    		}else{
		    			nearbyBlocks=false;
		    		}
		    	}
                boolean neg = record.getReadNegativeStrandFlag();
	    		System.out.println(
                        record.getReferenceName() + "\t" +
                        (neg ? aEnd : aStart) + "\t" +
                        (neg ? "-\t" : "+\t") + 
                        aLen + "\t" + 
                        weight);	    		
	       }
		}
                                          
    }
}