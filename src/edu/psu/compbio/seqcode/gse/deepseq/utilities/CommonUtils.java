/**
 * 
 */
package edu.psu.compbio.seqcode.gse.deepseq.utilities;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrixImport;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrixPainter;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.deepseq.discovery.kmer.Kmer;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScoreProfile;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScorer;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;

/**
 * @author Yuchun Guo
 * This clsss is to hold some frequently used static methods
 *
 */
public class CommonUtils {
	/*
	 * load text file in CGS Point format
	 * chr:coord, e.g. 1:234234
	 */
	public static ArrayList<Point> loadCgsPointFile(String filename, Genome genome) {

		File file = new File(filename);
		FileReader in = null;
		BufferedReader bin = null;
		ArrayList<Point> points = new ArrayList<Point>();
		try {
			in = new FileReader(file);
			bin = new BufferedReader(in);
			String line;
			while((line = bin.readLine()) != null) { 
				line = line.trim();
				Region point = Region.fromString(genome, line);
				if (point!=null)
					points.add(new Point(genome, point.getChrom(),point.getStart()));
			}
		}
		catch(IOException ioex) {
			//logger.error("Error parsing file", ioex);
		}
		finally {
			try {
				if (bin != null) {
					bin.close();
				}
			}
			catch(IOException ioex2) {
				//nothing left to do here, just log the error
				//logger.error("Error closing buffered reader", ioex2);
			}			
		}
		return points;
	}
	
	public static ArrayList<Region> loadRegionFile(String fname, Genome gen){
		ArrayList<Region> rset = new ArrayList<Region>();
		try{
			File rFile = new File(fname);
			if(!rFile.isFile()){
				System.err.println("\nThe region file is not found: "+fname+"!");
				System.exit(1);
			}
	        BufferedReader reader = new BufferedReader(new FileReader(rFile));
	        String line;
	        while ((line = reader.readLine()) != null) {
	            line = line.trim();
	            String[] words = line.split("\\s+");
            	Region r = Region.fromString(gen, words[0]);
            	if (r!=null)
            		rset.add(r);
            }
	        reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return rset;
	}
	
	/** load text file in SISSRS output BED format, then sort by p-value
	 * 	Chr		cStart	cEnd	NumTags	Fold	p-value
		---		------	----	-------	----	-------
		chr1	4132791	4132851	38		71.44	5.0e-006
	 */
	static public ArrayList<SISSRS_Event> load_SISSRs_events(Genome genome, String filename, boolean isPreSorted) {

		CommonUtils util = new CommonUtils();
		File file = new File(filename);
		FileReader in = null;
		BufferedReader bin = null;
		ArrayList<SISSRS_Event> events = new ArrayList<SISSRS_Event>();
		try {
			in = new FileReader(file);
			bin = new BufferedReader(in);
			// skip header, data starts at 58th line
			for (int i=1;i<58;i++){
				bin.readLine();
			}
			String line;
			while((line = bin.readLine()) != null) { 
				line = line.trim();
				String[] t = line.split("\\t");
				if (t.length==6){	// region format
					events.add(util.new SISSRS_Event(genome, t[0].replaceFirst("chr", ""), Integer.parseInt(t[1]), Integer.parseInt(t[2]),
						Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5])));
				}
				if (t.length==5){	// point format
					events.add(util.new SISSRS_Event(genome, t[0].replaceFirst("chr", ""), Integer.parseInt(t[1]), Integer.parseInt(t[1]),
						Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4])));
				}
			}
		}
		catch(IOException ioex) {
			ioex.printStackTrace();
		}
		finally {
			try {
				if (bin != null) {
					bin.close();
				}
			}
			catch(IOException ioex2) {
				//nothing left to do here, just log the error
				//logger.error("Error closing buffered reader", ioex2);
			}			
		}
		// sort by pvalue
		if (!isPreSorted)
			Collections.sort(events);
		
		return events;
	}
	
	public class SISSRS_Event implements Comparable<SISSRS_Event>{
		public Region region;
		public double tags;
		public double fold;
		public double pvalue;
		public SISSRS_Event(Genome g, String chrom, int start, int end, double tags, double fold, double pvalue){
			this.region = new Region(g, chrom, start, end);
			this.tags = tags;
			this.fold = fold;
			this.pvalue = pvalue;
		}
		public Point getPeak(){
			return region.getMidpoint();
		}
		//Comparable default method
		public int compareTo(SISSRS_Event p) {
			double diff = pvalue-p.pvalue;
			return diff==0?0:(diff<0)?-1:1;
		}
	}
	
	public static String timeElapsed(long tic){
		return timeString(System.currentTimeMillis()-tic);
	}
	private static String timeString(long length){
		float sec = length/1000F;
		return sec>3600?
				String.format("%.1fh",sec/3600):
				sec>60?
					String.format("%.1fm",sec/60):
					sec>1?
						String.format("%.1fs",sec):
						String.format("%dmils",length)	;
	}
	public static String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        Date date = new Date();
        return dateFormat.format(date);
    }
	public static void printArray(double[]array, String msgBefore, String msgAfter){
		System.out.print(msgBefore);
		System.out.print(arrayToString(array));
		System.out.print(msgAfter);
	}
	public static void printArray(int[]array, String msgBefore, String msgAfter){
		System.out.print(msgBefore);
		System.out.print(arrayToString(array));
		System.out.print(msgAfter);
	}
	public static String arrayToString(int[] array){
        StringBuilder output = new StringBuilder();
        for (int i=0;i<array.length-1;i++){
        	output.append(String.format("%d\t",array[i]));
        }
        output.append(String.format("%d",array[array.length-1]));
        return output.toString();
	}
	public static String arrayToString(double[] array){
        StringBuilder output = new StringBuilder();
        for (int i=0;i<array.length-1;i++){
        	output.append(String.format("%.2f\t",array[i]));
        }
        output.append(String.format("%.2f",array[array.length-1]));
        return output.toString();
	}
	public static String arrayToString(double[] array, String format){
        StringBuilder output = new StringBuilder();
        for (int i=0;i<array.length-1;i++){
        	output.append(String.format(format+"\t",array[i]));
        }
        output.append(String.format(format,array[array.length-1]));
        return output.toString();
	}
	public static String arrayToString(double[] array, int digit){
        StringBuilder output = new StringBuilder();
        for (int i=0;i<array.length-1;i++){
        	output.append(String.format("%."+digit+"f\t",array[i]));
        }
        output.append(String.format("%."+digit+"f",array[array.length-1]));
        return output.toString();
	}	
	public static String matrixToString(double[][] matrix, int digit, String[] names){
        StringBuilder output = new StringBuilder();
        for (int i=0;i<matrix.length;i++){
        	if (names!=null)
        		output.append(names[i]).append("\t");
        	output.append(arrayToString(matrix[i], digit)).append("\n");
        }
        return output.toString();
	}
	
	public static void writeFile(String fileName, String text){
		try{
			FileWriter fw = new FileWriter(fileName, false); //new file
			fw.write(text);
			fw.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
//		System.out.println("File was written to "+fileName);
	}
	
	public static ArrayList<String> readTextFile(String fileName){
		ArrayList<String> strs = new ArrayList<String>();
		try {	
			BufferedReader bin = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
	        String line;
	        while((line = bin.readLine()) != null) { 
	            line = line.trim();
	            if (line.length()!=0)
	            	strs.add(line);
	        }			
	        if (bin != null) {
	            bin.close();
	        }
        } catch (IOException e) {
        	System.err.println("Error when processing "+fileName);
            e.printStackTrace(System.err);
        }   
        return strs;
	}
	
	/** 
	 * Find the index that gives value larger than or equal to the key
	 * @param values
	 * @param key
	 * @return
	 */
	public static int findKey(double[] values, double key){
		int index = Arrays.binarySearch(values, key);
		if( index < 0 )  							// if key not found
			index = -(index+1); 
		else{		// if key match found, continue to search ( binarySearch() give undefined index with multiple matches)
			for (int k=index; k>=0;k--){
				if (values[k]==key)
					index = k;
				else
					break;
			}
		}
		return index;
	}
	
	public static String padding(int repeatNum, char padChar) throws IndexOutOfBoundsException {
	      if (repeatNum < 0) {
	          throw new IndexOutOfBoundsException("Cannot pad a negative amount: " + repeatNum);
	      }
	      if (repeatNum == 0) 
	    	  return "";

	      final char[] buf = new char[repeatNum];
	      for (int i = 0; i < buf.length; i++) {
	          buf[i] = padChar;
	      }
	      return new String(buf);
	}
	public static String padding(int repeatNum, String s) throws IndexOutOfBoundsException {
	      if (repeatNum < 0) {
	          throw new IndexOutOfBoundsException("Cannot pad a negative amount: " + repeatNum);
	      }
	      if (repeatNum == 0) 
	    	  return "";
	      
	      StringBuilder sb = new StringBuilder();
	      for (int i = 0; i < repeatNum; i++) {
	          sb.append(s);
	      }
	      return sb.toString();
	}
    /*
     * parse motif string, version, threshold, species_id from command line
     */
    public static Pair<WeightMatrix, Double> loadPWM(String args[], int orgId){
    	Pair<WeightMatrix, Double> pair=null;
    	try {   
    		double motifThreshold = Args.parseDouble(args, "motifThreshold", -1);
    		if (motifThreshold==-1){
    			System.err.println("No motif threshold was provided, default=9.99 is used.");
    			motifThreshold = 9.99;
    		}  
      	    String motifString = Args.parseString(args, "motif", null);
            if (motifString!=null){
      	      String motifVersion = Args.parseString(args, "version", null);
      	      
      		  int motif_species_id = Args.parseInteger(args, "motif_species_id", -1);
//      		  int wmid = WeightMatrix.getWeightMatrixID(motif_species_id!=-1?motif_species_id:orgId, motifString, motifVersion);
      		  int wmid = WeightMatrix.getWeightMatrixID(motifString, motifVersion);
      		  WeightMatrix motif = WeightMatrix.getWeightMatrix(wmid);
      		  System.out.println(motif.toString());
      		  pair = new Pair<WeightMatrix, Double>(motif, motifThreshold);
            }
            else{
            	String pfmFile = Args.parseString(args, "pfm", null);
            	if (pfmFile!=null){
            		double gc = Args.parseDouble(args, "gc", 0.41);
            		WeightMatrix motif = loadPWM_PFM_file(pfmFile, gc);
                	pair = new Pair<WeightMatrix, Double>(motif, motifThreshold);
            	}
            }
          } 
          catch (NotFoundException e) {
            e.printStackTrace();
          }  
  		  return pair;
    }
    
    public static WeightMatrix loadPWM_PFM_file(String pfmFile, double gc){
		try{
			List<WeightMatrix> wms = WeightMatrixImport.readTRANSFACFreqMatrices(pfmFile, "file");
			if (wms.isEmpty()){
				return null;
			}
			else{		// if we have valid PFM
				WeightMatrix wm = wms.get(0);
				float[][] matrix = wm.matrix;
				// normalize
		        for (int position = 0; position < matrix.length; position++) {
		            double sum = 0;
		            for (int j = 0; j < WeightMatrix.letters.length; j++) {
		                sum += matrix[position][WeightMatrix.letters[j]];
		            }
		            for (int j = 0; j < WeightMatrix.letters.length; j++) {
		                matrix[position][WeightMatrix.letters[j]] = (float)(matrix[position][WeightMatrix.letters[j]] / sum);
		            }
		        }
		        // log-odds
		        for (int pos = 0; pos < matrix.length; pos++) {
		            for (int j = 0; j < WeightMatrix.letters.length; j++) {
		                matrix[pos][WeightMatrix.letters[j]] = (float)Math.log(Math.max(matrix[pos][WeightMatrix.letters[j]], .000001) / 
		                		(WeightMatrix.letters[j]=='G'||WeightMatrix.letters[j]=='C'?gc/2:(1-gc)/2));
		            }
		        } 
		        return wm;
			}
		}
		catch (IOException e){
			return null;
		}
    }
	public static WeightMatrix loadPWM(String pfmFile, double gc ){
		WeightMatrix wm;
		try{
			List<WeightMatrix> wms = WeightMatrixImport.readTRANSFACFreqMatrices(pfmFile, "file");
			if (wms.isEmpty()){
				wm=null;
				System.out.println(pfmFile+" is not a valid motif file.");
			}
			else{		// if we have valid PFM, convert it to PWM
				wm = wms.get(0);		// only get primary motif
				float[][] matrix = wm.matrix;
				// normalize
		        for (int position = 0; position < matrix.length; position++) {
		            double sum = 0;
		            for (int j = 0; j < WeightMatrix.letters.length; j++) {
		                sum += matrix[position][WeightMatrix.letters[j]];
		            }
		            for (int j = 0; j < WeightMatrix.letters.length; j++) {
		                matrix[position][WeightMatrix.letters[j]] = (float)(matrix[position][WeightMatrix.letters[j]] / sum);
		            }
		        }
		        // log-odds
		        for (int pos = 0; pos < matrix.length; pos++) {
		            for (int j = 0; j < WeightMatrix.letters.length; j++) {
		                matrix[pos][WeightMatrix.letters[j]] = (float)Math.log(Math.max(matrix[pos][WeightMatrix.letters[j]], .001) / 
		                		(WeightMatrix.letters[j]=='G'||WeightMatrix.letters[j]=='C'?gc/2:(1-gc)/2));
		            }
		        } 
			}
		}
		catch (IOException e){
			System.out.println(pfmFile+" motif PFM file reading error!!!");
			wm = null;
		}
		return wm;
	}
    
	/**
	 *  Scan the sequence for best match to the weight matrix
	 *  @return  Pair of values, the lower coordinate of highest scoring PWM hit and the score
	 *  The position will be negative if the match is on '-' strand    
	 */
	public static Pair<Integer, Double> scanPWM(String sequence, int wmLen, WeightMatrixScorer scorer){
		if (sequence==null||sequence.length()<wmLen-1){
			return new Pair<Integer, Double>(-1, Double.NEGATIVE_INFINITY);
		}
		WeightMatrixScoreProfile profiler = scorer.execute(sequence);
		double maxSeqScore = Double.NEGATIVE_INFINITY;
		int maxScoringShift = 0;
		char maxScoringStrand = '+';
		for (int i=0;i<profiler.length();i++){
			double score = profiler.getMaxScore(i);
			if (maxSeqScore<score || (maxSeqScore==score && maxScoringStrand=='-')){	// equal score, prefer on '+' strand
				maxSeqScore = score;
				maxScoringShift = i;
				maxScoringStrand = profiler.getMaxStrand(i);
			}
		}
	
		if (maxScoringStrand =='-'){
			maxScoringShift = -maxScoringShift;		
		}
		return new Pair<Integer, Double>(maxScoringShift, maxSeqScore);
	}

	/**
	 *  Scan the sequence to find all matches to the weight matrix<br>
	 *  Note: the definition of motif position here is different from scanPWM() method<br>
	 *  Return  List of positions (middle of motif match) that pass the threshold. <br>
	 *  The position will be negative if the match is on '-' strand     
	 */
	public static ArrayList<Integer> getAllPWMHit(String sequence, int wmLen, WeightMatrixScorer scorer, double threshold){
		ArrayList<Integer> pos = new ArrayList<Integer>();
		if (sequence==null||sequence.length()<wmLen-1){
			return pos;
		}
		WeightMatrixScoreProfile profiler = scorer.execute(sequence);
		for (int i=0;i<profiler.length();i++){
			double score = profiler.getMaxScore(i);
			if (score >= threshold){
				if( profiler.getMaxStrand(i)=='+')
					pos.add(i+wmLen/2);
				else
					pos.add(-i-(wmLen-wmLen/2) );
			}
		}
		return pos;
	}
	
	/**
	 *  Scan the sequence using weight matrix, outwards from the given point, until a match pass the threshold<br>
	 *  return  Pair of values, the start position of nearest PWM hit and the score<br>
	 *  The position will be negative if the match is on '-' strand    <br>
	 *  If no match pass the threshold, return -999 as position. The caller need to check for this.<br>
	 */
	public static  Pair<Integer, Double> scanPWMoutwards(String sequence, WeightMatrix wm, WeightMatrixScorer scorer, int middle, double threshold){
		if (sequence==null||sequence.length()<wm.length()-1){
			return new Pair<Integer, Double>(-999,-1.0);
		}
		WeightMatrixScoreProfile profiler = scorer.execute(sequence);
		double goodScore = 0;
		int goodScoreShift = -999;
		char goodScoreStrand = '+';
		int maxRange = Math.max(middle, profiler.length()-middle);
		for (int i=0;i<maxRange;i++){
			int idx = middle+i;
			if (idx<0 || idx>=sequence.length()-wm.length())
				continue;
			double score = profiler.getMaxScore(idx);
			if (score>=threshold){
				goodScore = score;
				goodScoreShift = idx;
				goodScoreStrand = profiler.getMaxStrand(idx);
				break;
			}
			idx = middle-i;
			if (idx<0 || idx>=sequence.length()-wm.length())
				continue;
			score = profiler.getMaxScore(idx);
			if (score>=threshold){
				goodScore = score;
				goodScoreShift = idx;
				goodScoreStrand = profiler.getMaxStrand(idx);
				break;
			}
		}
	
		if (goodScoreStrand =='-'){
			goodScoreShift = -goodScoreShift;		
		}
		return new Pair<Integer, Double>(goodScoreShift, goodScore);
	}
	
	public static void printMotifLogo(WeightMatrix wm, File f, int pixheight){
		int pixwidth = (pixheight-WeightMatrixPainter.Y_MARGIN*3-WeightMatrixPainter.YLABEL_SIZE) * wm.length() /2 +WeightMatrixPainter.X_MARGIN*2;
		System.setProperty("java.awt.headless", "true");
		BufferedImage im = new BufferedImage(pixwidth, pixheight,BufferedImage.TYPE_INT_ARGB);
        Graphics g = im.getGraphics();
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
        WeightMatrixPainter wmp = new WeightMatrixPainter();
        g2.setColor(Color.WHITE);
        g2.fillRect(0,0,pixwidth, pixheight);
        wmp.paint(wm,g2,0,0,pixwidth,pixheight);
        try {
            ImageIO.write(im,"png",f);
        }  catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	/**
	 * Visualize sequences as color pixels
	 * @param seqs, raw sequences or FASTA sequences
	 * @param width, width of each base, in pixel
	 * @param height, height of each base, in pixel
	 * @param f, output file
	 */
	public static void visualizeSequences(String[] seqs, int width, int height, File f){
		if (seqs.length==0)
			return;
		
		int pixheight = 0;
		int maxLen = 0;
		for (String s:seqs){
        	if (s.length()!=0 && s.charAt(0)!='>')	{		// ignore header line of FASTA file
        		pixheight += height;
        		if (maxLen < s.length())
        			maxLen = s.length();
        	}
		}
		int pixwidth = maxLen*width;
		
		System.setProperty("java.awt.headless", "true");
		BufferedImage im = new BufferedImage(pixwidth, pixheight,BufferedImage.TYPE_INT_ARGB);
        Graphics g = im.getGraphics();
        Graphics2D g2 = (Graphics2D)g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0,0,pixwidth, pixheight);
        
        int count = 0;
        for (String s:seqs){
        	if (s.charAt(0)=='>')			// ignore header line of FASTA file
        		continue;
        	char[] letters = s.toCharArray();
        	for (int j=0;j<letters.length;j++){
        		switch(letters[j]){
        		case 'A':
        		case 'a':
        			g.setColor(Color.GREEN);
        			break;
        		case 'C':
        		case 'c':
                    g.setColor(Color.BLUE);
        			break;
        		case 'G':
        		case 'g':
                    g.setColor(Color.ORANGE);
        			break;
        		case 'T':
        		case 't':
                    g.setColor(Color.RED);
        			break;
        		case '-':
                    g.setColor(Color.WHITE);
        			break;
                default:
                	g.setColor(Color.GRAY);
        		}
                g.fillRect(j*width, count*height, width, height);
        	}
            count++;
        }
        try {
            ImageIO.write(im,"png",f);
        }  catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	public static int mismatch(String ref, String seq){
	  if (ref.length()!=seq.length())
		  return -1;
	  int mismatch = 0;
	  for (int i=0;i<ref.length();i++){
		  if (ref.charAt(i)!=seq.charAt(i))
			  mismatch ++;
	  }
	  return mismatch;
	}
	public static void copyFile(String srFile, String dtFile){
		try{
		  File f1 = new File(srFile);
		  File f2 = new File(dtFile);
		  InputStream in = new FileInputStream(f1);
		  OutputStream out = new FileOutputStream(f2);
		
		  byte[] buf = new byte[1024];
		  int len;
		  while ((len = in.read(buf)) > 0){
			  out.write(buf, 0, len);
		  }
		  in.close();
		  out.close();
		}
		catch(FileNotFoundException ex){
		  System.err.println(ex.getMessage() + " in the specified directory.");
		  System.exit(0);
		}
		catch(IOException e){
		  System.out.println(e.getMessage());  
		}
	}
	
	public static void main0(String[] args){
		System.out.println(findKey(new double[]{0,1,1,1,2,4,6}, 7));
	}
	
	public static void main1(String[] args){
		// --seqFile Y:\Tools\GPS\Multi_TFs\ESTF-Jan10\Sox2_Klf4_Tcfcp2I1_sites_MSA.fasta.txt
//	    String inName = Args.parseString(args, "seqFile", null);
//	    ArrayList<String> strs = readTextFile(inName);
//	    if (strs.isEmpty())
//	    	return;
//	    String[] ss = new String[strs.size()];
//	    strs.toArray(ss);
//	    int width = Args.parseInteger(args, "w", 5);
//	    int height = Args.parseInteger(args, "h", 3);
//	    visualizeSequences(ss, width, height, new File(inName+".png"));
	    
	}
	
    // --species "Mus musculus;mm8" --motif "CTCF" --version "090828" --windowSize 100 --motifThreshold 11.52
    public static void main(String args[]){
		// load motif
    	Genome genome;
    	Organism org=null;
    	WeightMatrix motif = null;
    	ArgParser ap = new ArgParser(args);
		Set<String> flags = Args.parseFlags(args);		
	    try {
	      Pair<Organism, Genome> pair = Args.parseGenome(args);
	      if(pair==null){
	        //Make fake genome... chr lengths provided???
	        if(ap.hasKey("geninfo")){
	          genome = new Genome("Genome", new File(ap.getKeyValue("geninfo")), true);
	            }else{
	              System.err.println("No genome provided; provide a Gifford lab DB genome name or a file containing chromosome name/length pairs.");;System.exit(1);
	            }
	      }else{
	        genome = pair.cdr();
	        org = pair.car();
	      }
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }
	    
		if (Args.parseString(args, "pfm", null)==null){
			Pair<WeightMatrix, Double> wm = CommonUtils.loadPWM(args, org.getDBID());
			motif = wm.car();
//			CommonUtils.printMotifLogo(motif, new File("test.png"), 150);	
		}
		else{
			motif = CommonUtils.loadPWM(Args.parseString(args, "pfm", null), Args.parseDouble(args, "gc", 0.41)); //0.41 human, 0.42 mouse
//			CommonUtils.printMotifLogo(motif, new File("test.png"), 150);
		}

		System.out.println(WeightMatrix.printMatrix(motif));
		
		System.out.println(motif.getMaxScore());
    }
}
