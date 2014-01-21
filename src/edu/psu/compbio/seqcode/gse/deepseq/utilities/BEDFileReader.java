package edu.psu.compbio.seqcode.gse.deepseq.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.deepseq.Read;
import edu.psu.compbio.seqcode.gse.deepseq.ReadHit;
import edu.psu.compbio.seqcode.gse.utils.stats.StatUtil;

public class BEDFileReader extends AlignmentFileReader {

	public BEDFileReader(File f, Genome g, boolean useNonUnique, int idStart) {
		super(f,g,-1,useNonUnique, idStart);
	}

	//Estimate chromosome lengths
	protected void estimateGenome() {
		HashMap<String, Integer> chrLenMap = new HashMap<String, Integer>();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(inFile));
			String line;
			while ((line = reader.readLine()) != null) {
	        	line = line.trim();
	        	if(line.charAt(0)!='#'){
		            String[] words = line.split("\\s+");
		            String chr = words[0];
		            String[] tmp = chr.split("\\.");
	            	chr=tmp[0].replaceFirst("chr", "");
	            	chr=chr.replaceFirst("^>", "");
	            	int max = Math.max(new Integer(words[1]).intValue(), new Integer(words[2]).intValue());
	            	
	        		if(!chrLenMap.containsKey(chr) || chrLenMap.get(chr)<max)
						chrLenMap.put(chr, max+1);
	        	}
			}
			gen=new Genome("Genome", chrLenMap);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//Return the total reads and weight
	protected void countReads() {
		try {
			readLength=-1;
			totalHits=0;
			totalWeight=0;
			BufferedReader reader = new BufferedReader(new FileReader(inFile));
			String line, lastID="";
			double currReadHitCount=0;
			Read currRead=null;
	        while ((line = reader.readLine()) != null) {
	        	line = line.trim();
	        	if(line.charAt(0)!='#'){
		            String[] words = line.split("\\s+");
		            String chr="."; char strand = '.';
		            int start=0, end=0;
		            
		            //String ID = words[3]; //No reliable ID for BED format, so treat EVERY hit as a new/unique read
		            
		            
	            	//if(ID.equals(lastID)){
	            	//	currReadHitCount++;
	            	//}else{
	            		if(currRead!=null){
	            			currRead.setNumHits(currReadHitCount);
	            			//Add the hits to the data structure
	            			addHits(currRead);
	            			currRead=null;
	            		}
	            		currReadHitCount=1;            			
	            	//}
	            	//String tag = words[3];
	            	//if(tag.equals("U") || (useNonUnique && words.length>9 && tag.charAt(0)=='R')){
	            		try{
	            			chr = words[0];
	            			String[] tmp = chr.split("\\.");
	            			chr=tmp[0].replaceFirst("chr", "");
	            			chr=chr.replaceFirst("^>", "");
	// http://genome.ucsc.edu/FAQ/FAQformat.html#format1
	//BED format is half open - The chromEnd base is not included  
	// For example, the first 100 bases of a chromosome are defined as chromStart=0, chromEnd=100, and span the bases numbered 0-99.
	            			start = new Integer(words[1]).intValue();
	            			end = new Integer(words[2]).intValue();
	            			if(readLength==-1)
	    	    				readLength = end-start;
	            			strand = words[5].charAt(0);
	    					ReadHit currHit = new ReadHit(gen,currID,chr, start, end-1, strand);
	    					currID++;
	    					//if(!ID.equals(lastID) || currRead==null){
	    					currRead = new Read((int)totalWeight);
	    					totalWeight++;
	    	            	//}
	    					currRead.addHit(currHit);
	            		} catch (NumberFormatException e){
	            			// skip reading this line for header or comment lines
	            		}
	    			//}
	            	//lastID=ID;
	        	}
            }
	        if(currRead!=null){
    			currRead.setNumHits(currReadHitCount);
    			//Add the hits to the data structure
    			addHits(currRead);
    		}
	        reader.close();
	        populateArrays();
	        
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}//end of countReads method
	
}//end of BEDFileReader class
