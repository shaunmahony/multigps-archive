package edu.psu.compbio.seqcode.gse.deepseq.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.CommonUtils;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.CommonUtils.SISSRS_Event;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSParser;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSPeak;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.MACSParser;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.MACSPeakRegion;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScoreProfile;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScorer;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.SetTools;

public class MethodComparisonMotifAnalysis {
	private final int NOHIT_OFFSET = 999;
	
	private boolean isPreSorted = false;	
	private int windowSize = 50;	
	private int rank = 0;
	private List<Region> restrictRegions;
	private boolean sortByStrength = false;
	
	private Genome genome;
	private Organism org;
	private String[] args;
	private WeightMatrix motif = null;
	private String outName="out";
	
	// each element in the list is for one ChIP-Seq method
	private ArrayList<String> methodNames = new ArrayList<String>();
	private ArrayList<ArrayList<Point>> events = new ArrayList<ArrayList<Point>>();
	private ArrayList<HashMap<Point, MotifHit>> maps = new ArrayList<HashMap<Point, MotifHit>>();	
	
	private ArrayList<Point> peaks_gps = new ArrayList<Point>();
	private ArrayList<Point> peaks_macs = new ArrayList<Point>();
	HashMap<Point, ArrayList<MotifHit>> maps_gps = null;
	HashMap<Point, ArrayList<MotifHit>> maps_macs = null;
	HashMap<Point, MotifHit> map_gps = null;
	HashMap<Point, MotifHit> map_macs = null;
	
	// motif score cutoffs
	//	double[] motifThresholds = new double[]{0,4,8,12,16,20,24,28};
	double[] motifThresholds = new double[]{5.587944030761719, 7.559912204742432, 
		11.52034854888916, 12.886543273925781,	15.858697891235352}; //Ctcf mouse
	private double motifThreshold=0;	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		MethodComparisonMotifAnalysis analysis = new MethodComparisonMotifAnalysis(args);
		// peak based analysis
		// We only print out the motif (at diff threshold) distances for each peak
		// spatialResolution or motifOccurence are analysis in Matlab 
//		analysis.printMotifOffsetDistance();
		
		// motif based analysis
//		analysis.motifBasedAnalysis();
		int type = Args.parseInteger(args, "analysisType", 0);
		switch(type){
		case 0:analysis.printMotifOffsets();break;
		case 1:analysis.proximalEventAnalysis();break;
		case 2:analysis.getUnaryEventList();break;
		case 3:analysis.singleMotifEventAnalysis();break;
		case 4:analysis.printOverlapTable();break;
		default: System.err.println("Unrecognize analysis type: "+type);
		}
	}
	
	MethodComparisonMotifAnalysis(String[] args){
		this.args = args;
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
	    	    
		// some parameters
		windowSize = Args.parseInteger(args, "windowSize", 50);
		rank = Args.parseInteger(args, "rank", 0);
		outName = Args.parseString(args,"out",outName);
		sortByStrength = flags.contains("ss");	// only for GPS
		isPreSorted = flags.contains("sorted");
		try{
			restrictRegions = Args.parseRegions(args);
		}
		catch (NotFoundException e){
			// do nothing
		}
		
		// load motif
		if (Args.parseString(args, "pfm", null)==null){
			Pair<WeightMatrix, Double> wm = CommonUtils.loadPWM(args, org.getDBID());
			motif = wm.car();
			motifThreshold = wm.cdr();		
		}
		else{
			double pwm_ratio = Args.parseDouble(args, "pwm_ratio", 0.6);
			motif = CommonUtils.loadPWM(Args.parseString(args, "pfm", null), Args.parseDouble(args, "gc", 0.41)); //0.41 human, 0.42 mouse
			motifThreshold = Args.parseDouble(args, "motifThreshold", -1);
			if (motifThreshold==-1){				
    			motifThreshold = motif.getMaxScore()*pwm_ratio;
    			System.err.println("No motif threshold was provided, use "+pwm_ratio+"*max_score = "+motifThreshold);
    		}  
		}
		

	}
	
	// This method print three text files
	// 1. sharedMotifOffset, for spatial resolution histogram
	// Only consider the motif positions that are shared by all methods, 
	// so that we are comparing at the same binding event, each row is matched
	// The output can be further processed by Matlab to get histogram of spatial resolution
	// for only one specified threshold
	
	// 2. allMotifOffset, for sensitivity and specificity analysis
	// Consider all the motif positions that are covered by at least one method, 
	// each row is matched, if not covered in a method, rank=-1, offset=999
	// The output can be further processed by Matlab 
	// for only one specified threshold
	
	// 3. rankedMotifOffset, both for motif coverage and occurrence curve
	// Each column is ranked as its original method, so each row is not matched
	private void printMotifOffsets() throws IOException {
		long tic = System.currentTimeMillis();
		readPeakLists();
		
		int minCount = Integer.MAX_VALUE;
		int maxCount = 0;
		for (int i=0;i<methodNames.size();i++){
			minCount = Math.min(minCount, events.get(i).size());
			maxCount = Math.max(maxCount, events.get(i).size());
		}
		if (rank==0){
			rank = minCount;
		}
		// print some information
		String msg = String.format("Motif score cutoff=%.2f, window size=%d, top %d events.\n",
				motifThreshold, windowSize, rank);
		System.out.print(msg);
		System.out.println("\nNumber of events:");
		for (int i=0;i<methodNames.size();i++){
			System.out.println(methodNames.get(i)+"\t"+events.get(i).size());
		}
		
		// get all regions that covered by any of the peak callers, 
		// so that the motif search will be done only once
		// process by chrom to be more efficient in sorting
		ArrayList<Region> allRegions = new ArrayList<Region>();
		for (String chrom: genome.getChromList()){
			ArrayList<Region> byChrom = new ArrayList<Region>();
			for (ArrayList<Point> ps:events){
				for (Point p: ps){
					if (p.getChrom().equalsIgnoreCase(chrom))
						byChrom.add(p.expand(windowSize));
				}
			}
			allRegions.addAll( mergeRegions(byChrom));
		}
		System.out.println(CommonUtils.timeElapsed(tic));
				
		// get all the strong motifs in these regions
		Collections.sort(allRegions);		// sort regions, the motifs should be sorted
		Pair<ArrayList<Point>, ArrayList<Double>> results = getAllMotifs(allRegions, motifThreshold);
		ArrayList<Point> allMotifs = results.car();
		ArrayList<Double> allMotifScores = results.cdr();
		System.out.println(CommonUtils.timeElapsed(tic));
		System.out.printf("%n%d motifs (in %d regions).%n%n", allMotifs.size(), allRegions.size());

		// Get the set of motif matches for all peak calls		
		System.out.printf("Events within a %d bp window to a motif:%n", windowSize);
		for (int i=0;i<events.size();i++){
			System.out.printf("%s \t#events: ", methodNames.get(i));
			HashMap<Point, MotifHit> motifs = getNearestMotif2(events.get(i), allMotifs, allMotifScores);
			maps.add(motifs);
			System.out.printf("\t#motifs: %d", motifs.keySet().size());
			System.out.println();
		}
		System.out.println(CommonUtils.timeElapsed(tic));
		
		System.out.printf("\nMotifs covered by an event:\n");
		for(int i = 0; i < methodNames.size(); i++)
			System.out.printf("%s\t%d/%d (%.1f%%)\n", methodNames.get(i), maps.get(i).size(), allMotifs.size(), 100.0*((double)maps.get(i).size())/(double)allMotifs.size());
				
		// get the intersection (motifs shared by all methods, upto top rank)
		System.out.print("\nRunning Spatial Resolution analysis ...\n");
		SetTools<Point> setTools = new SetTools<Point>();
		Set<Point> motifs_shared =  getHighRankSites(0);
		for (int i=1;i<maps.size();i++){
			Set<Point> motifs_new =  getHighRankSites(i);
			motifs_shared = setTools.intersection(motifs_shared,motifs_new);
		}
		msg = String.format("Motif score cutoff=%.2f, window size=%d, %d shared motifs in top %d events.",
				motifThreshold, windowSize, motifs_shared.size(), rank);
		System.out.println(msg);
		
		StringBuilder args_sb = new StringBuilder();
		for (String arg:args){
			args_sb.append(arg).append(" ");
		}
		String args_str = args_sb.toString();
		
		// output results, the spatial resolution (offset) 
		StringBuilder sb = new StringBuilder();
		sb.append(args_str+"\t"+msg+"\n");
		sb.append("MotifHit\tChrom");
		for (int i=0;i<methodNames.size();i++){
			sb.append("\t"+methodNames.get(i));
		}
		sb.append("\n");
		for(Point motif:motifs_shared){
			sb.append(motif.toString()+"\t");
			sb.append(motif.getChrom()+"\t");
			for (int i=0;i<maps.size();i++){
				sb.append(maps.get(i).get(motif).offset);
				if (i==maps.size()-1)
					sb.append("\n");
				else
					sb.append("\t");
			}
		}
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_sharedMotifOffsets_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb.toString());
		
// ************************************************************************************
// ************************************************************************************
		
		// get the union (motifs at least by one of all methods)
		Set<Point> motifs_union = maps.get(0).keySet();
		for (int i=1;i<maps.size();i++){
			Set<Point> motifs_new = maps.get(i).keySet();
			motifs_union = setTools.union(motifs_union,motifs_new);
		}
		msg = String.format("Total %d motifs from all methods.", motifs_union.size());
		System.out.println(msg);
		
		// output results, the spatial resolution (offset) 
		sb = new StringBuilder();
		sb.append(args_str+"\t"+msg+"\n");
		sb.append("MotifHit\tChrom\t");
		for (int i=0;i<methodNames.size();i++){
			sb.append(methodNames.get(i)+"_offset\t");
			sb.append(methodNames.get(i)+"_rank\t");
		}
		sb.append("\n");
		for(Point motif:motifs_union){
			sb.append(motif.toString()+"\t");
			sb.append(motif.getChrom()+"\t");
			for (int i=0;i<maps.size();i++){
				HashMap<Point, MotifHit> m = maps.get(i);
				if (m.containsKey(motif)){
					sb.append(m.get(motif).offset).append("\t");
					sb.append(m.get(motif).rank);
				}
				else{
					sb.append(NOHIT_OFFSET).append("\t");
					sb.append(-1);
				}
				if (i==maps.size()-1)
					sb.append("\n");
				else
					sb.append("\t");
			}
		}
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_allMotifOffsets_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb.toString());
		
		System.out.println();
		
// ************************************************************************************
// ************************************************************************************
		// motif occurrence / motif coverage (sensitivity)
		// get the motif offset of the peaks (rank indexed by each method)
		// if no motif hit, offset=NOHIT=999
		// print out the offset, for Matlab processing and plotting
		
		System.out.println("Get ranked peak-motif offset list ... ");
		
		ArrayList<HashMap<Point, Integer>> allPeakOffsets = new ArrayList<HashMap<Point, Integer>>();
		for (int i=0; i<methodNames.size();i++){
			allPeakOffsets.add(peak2MotifOffset(events.get(i), allMotifs));
		}
		
		// output results
		sb = new StringBuilder();
		sb.append(args_str+"\t"+msg+"\n");
		sb.append("Rank\t");
		for (int i=0;i<methodNames.size();i++){
			sb.append(methodNames.get(i)+"\t");
		}
		sb.append("\n");
		for(int j=0;j<maxCount;j++){
			sb.append(j+"\t");
			for (int i=0;i<allPeakOffsets.size();i++){
				if (j<events.get(i).size()){
					Point peak = events.get(i).get(j);
					sb.append(allPeakOffsets.get(i).containsKey(peak)?allPeakOffsets.get(i).get(peak):NOHIT_OFFSET);
				}
				else
					sb.append(NOHIT_OFFSET);
				
				if (i==allPeakOffsets.size()-1)
					sb.append("\n");
				else
					sb.append("\t");
			}
		}
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_rankedMotifOffsets_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb.toString());
		System.out.println("Done! " + CommonUtils.timeElapsed(tic));
	}

	private void readPeakLists() throws IOException {
        Vector<String> peakTags=new Vector<String>();
        for(String s : args)
        	if(s.contains("peakCall"))
        		if(!peakTags.contains(s))
        			peakTags.add(s);
    	
        // each tag represents a peak caller output file
        List<File> peakFiles=new ArrayList<File>();
        for(String tag : peakTags){
        	String name="";
        	if(tag.startsWith("--peakCall")){
        		name = tag.replaceFirst("--peakCall", ""); 
        		methodNames.add(name);
        	}
        	List<File> files = Args.parseFileHandles(args, "peakCall"+name);
        	File file= null;
        	if (!files.isEmpty()){
        		file = files.get(0);
        	}
        	peakFiles.add(file);
        }

        for (int i=0;i<methodNames.size();i++){
        	String name = methodNames.get(i);
        	String filePath = peakFiles.get(i).getAbsolutePath();
			ArrayList<Point> peakPoints = new ArrayList<Point>(); 
        	if (name.contains("GPS")||name.contains("GEM")){
				List<GPSPeak> gpsPeaks = GPSParser.parseGPSOutput(filePath, genome);
				// sort by descending pValue (-log P-value)
				if (!isPreSorted){
					if (sortByStrength){
						Collections.sort(gpsPeaks, new Comparator<GPSPeak>(){
						    public int compare(GPSPeak o1, GPSPeak o2) {
						        return o1.compareByIPStrength(o2);
						    }
						});	
					}
					else{
						Collections.sort(gpsPeaks, new Comparator<GPSPeak>(){
						    public int compare(GPSPeak o1, GPSPeak o2) {
						        return o1.compareByPValue(o2);
						    }
						});
					}
				}
				for (GPSPeak p: gpsPeaks){
					peakPoints.add(p);
				}
        	}
        	else if (name.contains("SISSRS")){
        		// assume be sorted by pvalue if (!isPreSorted)
        		ArrayList<SISSRS_Event> SISSRs_events = CommonUtils.load_SISSRs_events(genome, filePath, isPreSorted);
        		for (SISSRS_Event se: SISSRs_events){
        			peakPoints.add(se.getPeak());
        		}
        	}
        	else if (name.contains("MACS")){
    			List<MACSPeakRegion> macsPeaks = MACSParser.parseMACSOutput(filePath, genome);
    			if (!isPreSorted)
	    			Collections.sort(macsPeaks, new Comparator<MACSPeakRegion>(){
					    public int compare(MACSPeakRegion o1, MACSPeakRegion o2) {
					        return o1.compareByPValue(o2);
					    }
					});   			

				for (MACSPeakRegion p: macsPeaks){
    				peakPoints.add(p.getPeak());
    			}
        	}
        	else{
				peakPoints = CommonUtils.loadCgsPointFile(filePath, genome);
        	}  

        	events.add(peakPoints);
        }
	}
	
	// counting number of event calls in the regions with clustered motifs
	private void proximalEventAnalysis() throws IOException {
		long tic = System.currentTimeMillis();

		readPeakLists();
		
		int minCount = Integer.MAX_VALUE;
		int maxCount = 0;
		for (int i=0;i<events.size();i++){
			minCount = Math.min(minCount, events.get(i).size());
			maxCount = Math.max(maxCount, events.get(i).size());
		}
		if (rank==0){
			rank = minCount;
		}
		// print some information
		String msg = String.format("Motif score cutoff=%.2f, window size=%d, top %d peaks.\n",
				motifThreshold, windowSize, rank);
		System.out.print(msg);
		for (int i=0;i<methodNames.size();i++){
			System.out.println(methodNames.get(i)+"\t"+events.get(i).size());
		}
		
		// get all regions that covered by any of the peak callers, 
		// so that the motif search will be done only once
		// process by chrom to be more efficient in sorting
		ArrayList<Region> allRegions = new ArrayList<Region>();
		if (restrictRegions.isEmpty()){	// search whole genome
			for (String chrom: genome.getChromList()){
				//divide by each chrom, merge regions, then add back to the list
				ArrayList<Region> byChrom = new ArrayList<Region>();
				for (ArrayList<Point> ps:events){
					for (Point p: ps){
						if (p.getChrom().equalsIgnoreCase(chrom))
							byChrom.add(p.expand(windowSize));
					}
				}
				allRegions.addAll( mergeRegions(byChrom));
			}
		}
		else{	// if only search in the restrict regions
			Set<String> restrictChroms = new HashSet<String>();
			for (Region r: restrictRegions){
				restrictChroms.add(r.getChrom());
			}
			for (String chrom: genome.getChromList()){
				//divide by each chrom, merge regions, then add back to the list
				if (!restrictChroms.contains(chrom))
					continue;
				ArrayList<Region> byChrom = new ArrayList<Region>();
				for (ArrayList<Point> ps:events){
					for (Point p: ps){
						boolean inRestrictRegion=false;
						for (Region r: restrictRegions){
							if (r.contains(p))
								inRestrictRegion=true;
						}
						if (!inRestrictRegion)
							continue;
						if (p.getChrom().equalsIgnoreCase(chrom))
							byChrom.add(p.expand(windowSize));
					}
				}
				allRegions.addAll( mergeRegions(byChrom));
			}
		}

		System.out.println(CommonUtils.timeElapsed(tic));
				
		// get all the strong motifs in these regions
		Pair<ArrayList<Point>, ArrayList<Double>> results = getAllMotifs(allRegions, motifThreshold);
		ArrayList<Point> allMotifs = results.car();
		ArrayList<Double> allMotifScores = results.cdr();
		System.out.println(CommonUtils.timeElapsed(tic));
		
		// get all the n-ary motifs (within interDistance)
		int interDistance = 500;
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		StringBuilder sb3 = new StringBuilder();
		for (String chrom: genome.getChromList()){
			ArrayList<ArrayList<Point>> motifClusters = new ArrayList<ArrayList<Point>>();
			
			//divide by each chrom, find cluster of motifs
			ArrayList<Point> byChrom = new ArrayList<Point>();
			for (Point motif:allMotifs){
				if (motif.getChrom().equalsIgnoreCase(chrom))
					byChrom.add(motif);
			}
			Collections.sort(byChrom);
			boolean isNewCluster = true;
			ArrayList<Point> cluster=null;
			for (int i=1; i<byChrom.size(); i++){
				if (byChrom.get(i).getLocation()-byChrom.get(i-1).getLocation()<=interDistance){
					if (isNewCluster){
						cluster = new ArrayList<Point>();
						cluster.add(byChrom.get(i-1));
						cluster.add(byChrom.get(i));
					}
					else{
						cluster.add(byChrom.get(i));
					}
					// look ahead
					if (i<byChrom.size()-1){
						if (byChrom.get(i+1).getLocation()-byChrom.get(i).getLocation()<=interDistance){
							isNewCluster = false;
						}
						else{
							motifClusters.add(cluster);
							isNewCluster = true;
						}
					}
					else{	// this is last motif
						motifClusters.add(cluster);
					}
				}
			}
			
			// print each motif clusters with event counts
			for (ArrayList<Point> tmpCluster: motifClusters){
				Region r = new Region(genome, chrom, tmpCluster.get(0).getLocation(), tmpCluster.get(tmpCluster.size()-1).getLocation());
				r=r.expand(windowSize, windowSize);
				sb.append(r.toString()).append("\t").append(tmpCluster.size());
				sb2.append(r.toString()).append("\t").append(tmpCluster.size());
				sb3.append(r.toString()).append("\t").append(tmpCluster.size());
				for (int i=0;i<events.size();i++){	// for each method
					int count=0;
					ArrayList<Point> ps = events.get(i);
					ArrayList<Point> events_local = new ArrayList<Point>();
					for (int j=0;j<Math.min(rank, ps.size());j++){
						if (r.contains(ps.get(j))){
							events_local.add(ps.get(j));
							count++;
						}
					}
					sb.append("\t").append(count);
					
					// more stringent criteria: the events should be less than 500bp apart
					int count2=0;
					if (count>=2){
						Collections.sort(events_local);
						HashSet<Point> unique = new HashSet<Point>();
						for (int k=1;k<events_local.size();k++){
							if (events_local.get(k).distance(events_local.get(k-1))<=interDistance){
								unique.add(events_local.get(k));
								unique.add(events_local.get(k-1));
							}
						}
						count2 = unique.size();
					}
					sb2.append("\t").append(count2);
					
					// more stringent criteria: the events should be within 10bp of motif
					int count3=0;
					for (Point event: events_local){
						for (Point motif: tmpCluster){
							if (event.distance(motif)<=10){
								count3++;
								break; // count once
							}
						}
					}
					sb3.append("\t").append(count3);
				}
				sb.append("\n");
				sb2.append("\n");
				sb3.append("\n");
			}
		}//each chrom
		
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_clusteredMotifEvents_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb.toString());
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_clusteredMotifEvents500bp_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb2.toString());
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_clusteredMotifEvents10bp_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb3.toString());	
	}
	
	// print the pairwise overlap percentage of different set of binding calls 
	private void printOverlapTable() throws IOException{
		int overlapWindowSize = windowSize;
		long tic = System.currentTimeMillis();
		readPeakLists();
		
		int numMethods = events.size();
		double[][] overlaps = new double[numMethods][numMethods];	//percentage of i are covered by j
		for (int i=0;i<numMethods;i++){
			for (int j=0;j<numMethods;j++){
				if (i==j)
					overlaps[i][j]=100;
				else{
					int count = countOverlaps(events.get(i), events.get(j), overlapWindowSize);
					overlaps[i][j] = 100*count/(double)events.get(i).size();
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("\nPercentage of events in rows that are covered by events in column.\n");
		sb.append(" ").append("\t");
		for (int i=0;i<methodNames.size()-1;i++){
			sb.append(methodNames.get(i)).append("\t");
		}
		sb.append(methodNames.get(methodNames.size()-1)).append("\n");
		
		sb.append("Total").append("\t");
		for (int i=0;i<events.size()-1;i++){
			sb.append(events.get(i).size()).append("\t");
		}
		sb.append(events.get(methodNames.size()-1).size()).append("\n");
		
		sb.append(CommonUtils.matrixToString(overlaps, 0, (String[])methodNames.toArray(new String[methodNames.size()])));
		sb.append("\nOverlapping window size is "+overlapWindowSize+"\n\n");
		sb.append(CommonUtils.timeElapsed(tic));
		System.out.println(sb.toString());
	}
	
	/** report number of events in list1 that is found within DISTNACE bp window of peaks in list 2
	 * @param list1
	 * @param list2
	 * @param windowSize
	 * @return
	 */
	private int countOverlaps(ArrayList<Point> list1, ArrayList<Point> list2, int windowSize){
		int overlap=0;
		ArrayList<Point> a = (ArrayList<Point> )list1.clone();
		ArrayList<Point> b = (ArrayList<Point> )list2.clone();
		Collections.sort(a);
		Collections.sort(b);
		
		int nextSearchIndex = 0;		// the index of first b match for previous peak in a
		// this will be use as start search position of the inner loop
		for(int i=0;i<a.size();i++){
			Point pa =a.get(i);
			// search for the match b event, if found, stop
			for(int j=nextSearchIndex;j<b.size();j++){
				Point pb =b.get(j);
				if (pa.getChrom().equalsIgnoreCase(pb.getChrom())){
					int offset = pa.offset(pb);
					if (offset>windowSize)	// pa is far right, continue with next b
						continue;
					else if (offset<-windowSize){	// pa is far left, no match, next a
						break;
					}
					else{					// match found, next a
						overlap++;
						nextSearchIndex=j;
						break;
					}
				}
				else{				// diff chrom, next a
					nextSearchIndex=j+1;
					break;
				}
			}
		}
		return overlap;
	}

	// counting number of event calls in the regions with SINGLE motif 
	// this is to check the false positives of joint event calls
	private void singleMotifEventAnalysis() throws IOException {
		long tic = System.currentTimeMillis();

		readPeakLists();
		
		int minCount = Integer.MAX_VALUE;
		int maxCount = 0;
		for (int i=0;i<events.size();i++){
			minCount = Math.min(minCount, events.get(i).size());
			maxCount = Math.max(maxCount, events.get(i).size());
		}
		if (rank==0){
			rank = minCount;
		}
		// print some information
		String msg = String.format("Motif score cutoff=%.2f, window size=%d, top %d peaks.\n",
				motifThreshold, windowSize, rank);
		System.out.print(msg);
		for (int i=0;i<methodNames.size();i++){
			System.out.println(methodNames.get(i)+"\t"+events.get(i).size());
		}
		
		// get all regions that covered by any of the peak callers, 
		// so that the motif search will be done only once
		// process by chrom to be more efficient in sorting
		ArrayList<Region> allRegions = new ArrayList<Region>();
		if (restrictRegions.isEmpty()){	// search whole genome
			for (String chrom: genome.getChromList()){
				//divide by each chrom, merge regions, then add back to the list
				ArrayList<Region> byChrom = new ArrayList<Region>();
				for (ArrayList<Point> ps:events){
					for (Point p: ps){
						if (p.getChrom().equalsIgnoreCase(chrom))
							byChrom.add(p.expand(windowSize));
					}
				}
				allRegions.addAll( mergeRegions(byChrom));
			}
		}
		else{	// if only search in the restrict regions
			Set<String> restrictChroms = new HashSet<String>();
			for (Region r: restrictRegions){
				restrictChroms.add(r.getChrom());
			}
			for (String chrom: genome.getChromList()){
				//divide by each chrom, merge regions, then add back to the list
				if (!restrictChroms.contains(chrom))
					continue;
				ArrayList<Region> byChrom = new ArrayList<Region>();
				for (ArrayList<Point> ps:events){
					for (Point p: ps){
						boolean inRestrictRegion=false;
						for (Region r: restrictRegions){
							if (r.contains(p))
								inRestrictRegion=true;
						}
						if (!inRestrictRegion)
							continue;
						if (p.getChrom().equalsIgnoreCase(chrom))
							byChrom.add(p.expand(windowSize));
					}
				}
				allRegions.addAll( mergeRegions(byChrom));
			}
		}

		System.out.println(CommonUtils.timeElapsed(tic));
				
		// get all the strong motifs in these regions
		Pair<ArrayList<Point>, ArrayList<Double>> results = getAllMotifs(allRegions, motifThreshold);
		ArrayList<Point> allMotifs = results.car();
		ArrayList<Double> allMotifScores = results.cdr();
		System.out.println(CommonUtils.timeElapsed(tic));
		
		// get all the single motifs (no neighbors within interDistance)
		int interDistance = 500;
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		for (String chrom: genome.getChromList()){
			ArrayList<Point> singleMotifs = new ArrayList<Point>();
			
			//divide by each chrom, find cluster of motifs
			ArrayList<Point> byChrom = new ArrayList<Point>();
			for (Point motif:allMotifs){
				if (motif.getChrom().equalsIgnoreCase(chrom))
					byChrom.add(motif);
			}
			Collections.sort(byChrom);
			int size = byChrom.size();
			if (size<=2)
				continue;
			if (byChrom.get(1).getLocation()-byChrom.get(0).getLocation()>interDistance)
				singleMotifs.add(byChrom.get(0));
			for (int i=1; i<size-1; i++){
				if (byChrom.get(i).getLocation()-byChrom.get(i-1).getLocation()>interDistance &&
						byChrom.get(i+1).getLocation()-byChrom.get(i).getLocation()>interDistance	){
					singleMotifs.add(byChrom.get(i));
				}
			}
			if (byChrom.get(size-1).getLocation()-byChrom.get(size-2).getLocation()>interDistance)
				singleMotifs.add(byChrom.get(size-1));
			
			// print each single motif sites with event counts
			for (Point p: singleMotifs){
				Region r = p.expand(windowSize);
				sb.append(r.toString()).append("\t").append(0);
				sb2.append(r.toString()).append("\t").append(0);
				for (int i=0;i<events.size();i++){
					int count=0;
					ArrayList<Point> ps = events.get(i);
					ArrayList<Point> events = new ArrayList<Point>();
					for (int j=0;j<rank;j++){
						if (r.contains(ps.get(j))){
							events.add(ps.get(j));
							count++;
						}
					}
					sb.append("\t").append(count);
					// more stringent criteria: the events should be less than 500bp apart
					if (count>=2){
						Collections.sort(events);
						HashSet<Point> unique = new HashSet<Point>();
						for (int k=1;k<events.size();k++){
							if (events.get(k).distance(events.get(k-1))<=interDistance){
								unique.add(events.get(k));
								unique.add(events.get(k-1));
							}
						}
						count = unique.size();
					}
					sb2.append("\t").append(count);
				}
				sb.append("\n");
				sb2.append("\n");
			}
		}//each chrom
		
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_singleMotifEvents_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb.toString());
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_singleMotifEvents500bp_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb2.toString());
	}
	private void getUnaryEventList() throws IOException{
		long tic = System.currentTimeMillis();
		readPeakLists();
		int minCount = Integer.MAX_VALUE;
		int maxCount = 0;
		for (int i=0;i<events.size();i++){
			System.out.println(methodNames.get(i)+"\t"+events.get(i).size());
			minCount = Math.min(minCount, events.get(i).size());
			maxCount = Math.max(maxCount, events.get(i).size());
		}
		if (rank==0){
			rank = maxCount;		// take all possible overlaps, ranking is ignored
		}
		
		// get all regions that covered by any of the peak callers, 
		// so that the motif search will be done only once
		// process by chrom to be more efficient in sorting
		ArrayList<Region> allRegions = new ArrayList<Region>();

		for (String chrom: genome.getChromList()){
			ArrayList<Region> byChrom = new ArrayList<Region>();
			for (ArrayList<Point> ps:events){
				for (Point p: ps){
					if (p.getChrom().equalsIgnoreCase(chrom))
						byChrom.add(p.expand(windowSize));
				}
			}
			allRegions.addAll( mergeRegions(byChrom));
		}
		System.out.println(CommonUtils.timeElapsed(tic));
				
		// get all the strong motifs in these regions
		Pair<ArrayList<Point>, ArrayList<Double>> results = getAllMotifs(allRegions, motifThreshold);
		ArrayList<Point> allMotifs = results.car();
		ArrayList<Double> allMotifScores = results.cdr();
		System.out.println(CommonUtils.timeElapsed(tic));
		
		// Get the set of motif matches for all peak calls
		for (int i=0;i<events.size();i++){
			ArrayList<Point> ps = events.get(i);
			ArrayList<Point> toRemove = new ArrayList<Point>();
			Collections.sort(ps);
			// remove proximal events
			for (int j=1;j<ps.size();j++){
				try{
					if (ps.get(j).distance(ps.get(j-1))<=500){
						toRemove.add(ps.get(j));
						toRemove.add(ps.get(j-1));
					}
				}
				catch (IllegalArgumentException e){
					continue; // ingore events on different chrom
				}
			}
			ps.removeAll(toRemove);
			System.out.println(methodNames.get(i)+": "+ps.size());
			maps.add(getNearestMotif2(ps, allMotifs, allMotifScores));
		}
		System.out.println(CommonUtils.timeElapsed(tic));
		
		// get the intersection (motifs shared by all methods, upto top rank)
		System.out.print("\nRunning Spatial Resolution analysis ...\n");
		SetTools<Point> setTools = new SetTools<Point>();
		Set<Point> motifs_shared =  getHighRankSites(0);
		for (int i=1;i<maps.size();i++){
			Set<Point> motifs_new =  getHighRankSites(i);
			motifs_shared = setTools.intersection(motifs_shared,motifs_new);
		}
		String msg = String.format("Motif score cutoff=%.2f, window size=%d, %d shared motifs in top %d peaks.",
				motifThreshold, windowSize, motifs_shared.size(), rank);
		System.out.println(msg);
		
		// output results
		StringBuilder sb = new StringBuilder();
		sb.append(msg+"\n");
		sb.append("MotifHit\tChrom\t");
		for (int i=0;i<methodNames.size();i++){
			sb.append(methodNames.get(i)+"\t");
		}
		sb.append("\n");
		for(Point motif:motifs_shared){
			sb.append(motif.toString()+"\t");
			sb.append(motif.getChrom()+"\t");
			for (int i=0;i<maps.size();i++){
				sb.append(maps.get(i).get(motif).offset);
				if (i==maps.size()-1)
					sb.append("\n");
				else
					sb.append("\t");
			}
		}
		CommonUtils.writeFile(outName+"_"+methodNames.size()+"methods_unaryEventLists_"
				+String.format("%.2f_",motifThreshold)
				+windowSize+".txt", sb.toString());
		
	}
	
	// merge the overlapped regions 
	// if "toExpandRegion"=true, expand each region on both side to leave enough space, 
	// to include every potential reads, then merge
	private ArrayList<Region> mergeRegions(ArrayList<Region> regions){
		ArrayList<Region> mergedRegions = new ArrayList<Region>();
		if (regions.isEmpty())
			return mergedRegions;
		Collections.sort(regions);
		Region previous = regions.get(0);
		mergedRegions.add(previous);
		
		for (Region region: regions){
			// if overlaps with previous region, combine the regions
			if (previous.overlaps(region)){
				mergedRegions.remove(previous);
				previous = previous.combine(region);
			}
			else{
				previous = region;
			}
			mergedRegions.add(previous);
		}
		return mergedRegions;
	}//end of mergeRegions method
	
	// get the top ranking sites of a set of calls
	private Set<Point> getHighRankSites(int method){
		HashMap<Point, MotifHit> m = maps.get(method);
		Set<Point> motifs = m.keySet();
		Set<Point> motifs_top = new HashSet<Point>();
		maps.get(0).keySet();
		for (Point p: motifs){
			if (m.get(p).rank < rank)
				motifs_top.add(p);
		}
		return motifs_top;
	}

	// get the all nearest motif hits in the region (windowSize) 
	// around each peak in the list
	// if a motif have more than one peaks nearby, associate it with the nearest peak
	private HashMap<Point, MotifHit> getNearestMotif(ArrayList<Point> peaks, int rank, double threshold){	
		HashMap<Point, MotifHit> motifs = new HashMap<Point, MotifHit>();
		WeightMatrixScorer scorer = new WeightMatrixScorer(motif);
		int range = Math.min(peaks.size(), rank); // only compare top #(rank) peaks
		for(int i=0; i<range; i++){
			if (i % 1000==0)
				System.out.println(i);
			Point peak = peaks.get(i);
			Region r= peak.expand(windowSize);
			WeightMatrixScoreProfile profiler = scorer.execute(r);
			//search for whole region
			for(int z=0; z<r.getWidth(); z++){		
				double score = profiler.getMaxScore(z);
				if(score >= threshold){
					Point motifPos = new Point(genome, peak.getChrom(), r.getStart()+z+profiler.getMatrix().length()/2);
					MotifHit hit = new MotifHit(motifPos, score, peak, i);
					//if other peaks also associated with this motif, keep the nearest one
					if (motifs.containsKey(motifPos)){
						if (Math.abs(hit.offset)<Math.abs(motifs.get(motifPos).offset))
							motifs.put(motifPos, hit);
					}
					else{
						motifs.put(motifPos, hit);
					}
				}
			}
		}
		return motifs;
	}
	
	// get the nearest motif hit in the region (windowSize) around each peak in the list
	// this is motif-centered, some peak may associate with a motif by then took away by other peaks
	// if a motif have more than one peaks nearby, associate it with the nearest peak
	// Assuming the motif positions are sorted
	private HashMap<Point, MotifHit> getNearestMotif2(ArrayList<Point> peaks, ArrayList<Point> allMotifs, ArrayList<Double>allMotifScores){
		// make a copy and sort
		ArrayList<Point> ps = (ArrayList<Point>) peaks.clone();
		Collections.sort(ps);
		
		HashMap<Point, MotifHit> motifs = new HashMap<Point, MotifHit>();
		int numPeaksWithinMotifs = 0;
		int firstMatchIndex = 0;		// the index of first motif match index for previous peak
										// this will be use as start search position of the inner loop
		for(int i=0;i<ps.size();i++){
			Point peak =ps.get(i);
			int nearestIndex = -1;
			int nearestDistance = windowSize;
			boolean firstMatchFound = false;
			// search for the position, not iterate everyone
			for(int j=firstMatchIndex;j<allMotifs.size();j++){
				Point motif =allMotifs.get(j);
				if (peak.getChrom().equalsIgnoreCase(motif.getChrom())){
					int distance = peak.distance(motif);
					if (distance<=nearestDistance){
						if (!firstMatchFound){		// update on the first match
							firstMatchIndex = j;
							firstMatchFound = true;
						}							
						nearestDistance = distance;
						nearestIndex = j;
					}
					else{		// if distance > nearest, and found a match already, it is getting farther away, stop search 
						if (firstMatchFound){	
							break;
						}
					}					
				}
			}
			
			if (nearestIndex !=-1){			// motif hit is within the window
				numPeaksWithinMotifs++;
				Point nearestMotif = allMotifs.get(nearestIndex);
				MotifHit hit = new MotifHit(nearestMotif, allMotifScores.get(nearestIndex), peak, i);
				//if other peaks also associated with this motif, keep the nearest one
				if (motifs.containsKey(nearestMotif)){
					if (Math.abs(hit.offset)<Math.abs(motifs.get(nearestMotif).offset))
						motifs.put(nearestMotif, hit);
				}
				else{
					motifs.put(nearestMotif, hit);
				}
			}
		}
		System.out.printf("%d/%d (%.1f%%)", numPeaksWithinMotifs, peaks.size(), 100.0*((double)numPeaksWithinMotifs)/(double)peaks.size(), windowSize);
		return motifs;
	}
	// get the nearest motif offset in the region (windowSize) around each peak in the list
	// this is peak-centered, all peaks will have a offset, NOHIT_OFFSET for no motif found.
	private HashMap<Point, Integer> peak2MotifOffset(ArrayList<Point> peaks, ArrayList<Point> allMotifs){
		// make a copy of the list and sort
		ArrayList<Point> ps = (ArrayList<Point>) peaks.clone();
		Collections.sort(ps);
		
		HashMap<Point, Integer> peaksOffsets = new HashMap<Point, Integer>();
		int firstMatchIndex = 0;		// the index of first motif match index for previous peak
										// this will be use as start search position of the inner loop
		for(int i=0;i<ps.size();i++){
			Point peak =ps.get(i);
			int nearestIndex = -1;
			int nearestDistance = windowSize;
			boolean firstMatchFound = false;
			// search for the position, not iterate everyone
			for(int j=firstMatchIndex;j<allMotifs.size();j++){
				Point motif =allMotifs.get(j);
				if (peak.getChrom().equalsIgnoreCase(motif.getChrom())){
					int distance = peak.distance(motif);
					if (distance<=nearestDistance){
						if (!firstMatchFound){		// update on the first match
							firstMatchIndex = j;
							firstMatchFound = true;
						}							
						nearestDistance = distance;
						nearestIndex = j;
					}
					else{		// if distance > nearest, and found a match already, it is getting farther away, stop search 
						if (firstMatchFound){	
							break;
						}
					}					
				}
			}
			
			if (nearestIndex !=-1){			// motif hit is within the window
				Point nearestMotif = allMotifs.get(nearestIndex);
				peaksOffsets.put(peak, peak.offset(nearestMotif));
			}
		}
		return peaksOffsets;
	}	
	// this is an alternative test to exclude the possibility that one method may 
	// take advantage by predicting multiple peaks around the motif position
	// therefore, if a motif have more than one peaks nearby, do not include it
	private HashMap<Point, MotifHit> getNearestMotif3(ArrayList<Point> peaks, ArrayList<Point> allMotifs, ArrayList<Double>allMotifScores){	
		HashMap<Point, MotifHit> motifs = new HashMap<Point, MotifHit>();
		for(int j=0;j<allMotifs.size();j++){
			Point motif =allMotifs.get(j);
			int count = 0;
			int peakIndex = -1;
			for(int i=0;i<peaks.size();i++){
				Point peak =peaks.get(i);
				int nearestDistance = windowSize;
				if (peak.getChrom().equalsIgnoreCase(motif.getChrom())){
					int distance = peak.distance(motif);
					if (distance<=nearestDistance){
						count++;
						nearestDistance = distance;
						peakIndex = i;
					}
				}
			}
			if (count!=1)	// not exactly one peak in the 100bp to the motif, exclude this motif
				continue;
			
			Point nearestMotif = allMotifs.get(j);
			MotifHit hit = new MotifHit(nearestMotif, allMotifScores.get(j), peaks.get(peakIndex), peakIndex);
			motifs.put(nearestMotif, hit);
		}
		return motifs;
	}
		
	// get the all nearest motif hits in the region (windowSize) 
	// around each peak in the list
	// if a motif have more than one peaks nearby, associate it with the nearest peak
	private Pair<ArrayList<Point>, ArrayList<Double>> getAllMotifs(ArrayList<Region> regions, double threshold){
		ArrayList<Point> allMotifs = new ArrayList<Point>();
		ArrayList<Double> scores = new ArrayList<Double>();
		WeightMatrixScorer scorer = new WeightMatrixScorer(motif);
		int length = motif.length();
		int count = regions.size();
		System.out.print("\nGetting motifs from "+count+" regions ...\n");
		for(int i=0; i<count; i++){
			if (i % 1000==0)
				System.out.println(i);
			Region r= regions.get(i);
			WeightMatrixScoreProfile profiler = scorer.execute(r);
			//search for whole region
			for(int z=0; z<r.getWidth(); z++){		
				double score = profiler.getMaxScore(z);
				int strand = profiler.getMaxStrand(z)=='+'?1:-1;
				if(score >= threshold){
					// get the strand-specific middle position of motif
					Point motifPos = new Point(genome, r.getChrom(), r.getStart()+z+(strand==1?length/2:length-1-length/2));
					allMotifs.add(motifPos);
					scores.add(score * strand); // positive score if motif match on '+' strand
				}
			}
		}
		return new Pair<ArrayList<Point>, ArrayList<Double>>(allMotifs, scores);
	}
	
	private class MotifHit{
		Point motif;
		double score;
		Point peak;
		int rank;	// rank of the peak in the prediction list, method dependent
		int offset;	// positive: peak is on 5' direction of motif, negative: otherwise
		MotifHit(Point motif, double score, Point peak, int rank){
			this.motif = motif;
			this.score = score;
			this.peak = peak;
			this.rank = rank;
			offset = peak.offset(motif)*(score>0?1:-1);
		}
	}




	///////////////////////////////////////////////////////////////
	// old code
	
	private void printMotifOffsetDistance(){
//		if (peaks_macs.size()>0)
//			motifOffsetDistance(peaks_macs, "MACS");
		if (peaks_gps.size()>0)
			motifOffsetDistance(peaks_gps, "GPS");
	}
	
	// here we only print out the offset distance between motif and peak
	// we use another matlab code to calculate 
	// 1. the percentage of motif occurence
	// 2. the spatial resolution
	private void motifOffsetDistance(ArrayList<Point> peaks, String method){
		System.out.print("\nRunning Motif Occurrence analysis ...\n");
		WeightMatrixScorer scorer = new WeightMatrixScorer(motif);

		// for each peak and motif threshold, the shortest distance between BS and motif
		int[][]distance = new int[motifThresholds.length][peaks.size()];
		for(int i=0; i<peaks.size(); i++){
			if (i % 100==0)
				System.out.println(i);
			Point peak = peaks.get(i);
			Region r= peak.expand(windowSize);
			WeightMatrixScoreProfile profiler = scorer.execute(r);
			for(int j=0;j<motifThresholds.length;j++){
				double threshold = motifThresholds[j];
				//search from BS outwards
				for(int z=0; z<=r.getWidth()/2; z++){
					double leftScore= profiler.getMaxScore(windowSize-z);
					double rightScore= profiler.getMaxScore(windowSize+z);				
					if(rightScore>=threshold){
						distance[j][i] = z;
						break;
					}
					if(leftScore>=threshold){
						distance[j][i] = -z;
						break;
					}
					// if motif score at this position is too small, and reach end of region
					if (z==r.getWidth()/2){
						distance[j][i] = NOHIT_OFFSET;
					}
				}
			}
		}
		
		// output results
		StringBuilder sb = new StringBuilder();
		sb.append("#"+motif.name+" "+ motif.version+" "+method+"\n");
		sb.append("#Motif Score");
		for(int j=0;j<motifThresholds.length;j++){
			sb.append("\t"+motifThresholds[j]);
		}
		sb.append("\n");
		for(int i=0; i<peaks.size(); i++){
			sb.append(peaks.get(i).getLocationString());
			for(int j=0;j<motifThresholds.length;j++){
				sb.append("\t"+distance[j][i]);
			}
			sb.append("\n");
		}
		CommonUtils.writeFile("CTCF_motif_distance_byScore_"+windowSize+"_"+method+".txt", sb.toString());
	}	
		
	private void motifBasedAnalysis(){
		long tic = System.currentTimeMillis();
		// Get the set of motif matches from all methods
		System.out.print("\nGetting motifs from GPS result ...\n");
		maps_gps = getAllNearestMotifs(peaks_gps, motifThresholds[0]);
		System.out.print("\nGetting motifs from MACS result ...\n");
		maps_macs = getAllNearestMotifs(peaks_macs, motifThresholds[0]);
		System.out.println(CommonUtils.timeElapsed(tic));
		sharedMotif_SpatialResolution();
//		bindingSpatialResolutionHistrogram();
//		motifCoverage();
	}
	
	// for each shared motif, average all the peaks that fits the criteria
	private void sharedMotif_SpatialResolution(){
		System.out.print("\nRunning Spatial Resolution analysis ...\n");
		long tic = System.currentTimeMillis();
		SetTools<Point> setTools = new SetTools<Point>();
		// for each peak and motif threshold, the shortest distance between BS and motif
		int peakCount = Math.min(peaks_gps.size(), peaks_macs.size());
		int step = 100;
		int stepCount = peakCount/step;

		// array to store average spatial resolution, [diff methods][motif scores][peak rank steps]
		double[][][]resolution = new double[2][motifThresholds.length][stepCount];

		for(int i=0;i<motifThresholds.length;i++){
			// get only the motifs that pass the score cutoff
			Set<Point> motifs_gps = new HashSet<Point>();
			Set<Point> motifs_macs = new HashSet<Point>();
			for (Point motif: maps_gps.keySet())
				if (maps_gps.get(motif).get(0).score >= motifThresholds[i])
					motifs_gps.add(motif);
			for (Point motif: maps_macs.keySet())
				if (maps_macs.get(motif).get(0).score >= motifThresholds[i])
					motifs_macs.add(motif);
			
			// intersection set for spatial resolution comparison
			// only consider the motif positions that are cover by all methods
			Set<Point> intersection = setTools.intersection(motifs_gps,motifs_macs);
			System.out.print(String.format("Motif score cutoff=%.2f,\t%d\t shared motifs\n",
					motifThresholds[i], intersection.size()));
			
			// calculate the average spatial resolution (offset) in the range of rank steps
			for (int j=0;j<stepCount;j++) {
				int total = 0;
				int count = 0;
				int total_macs = 0;
				int count_macs = 0;
				for (Point motif:intersection){				// for each shared motif
					for (MotifHit hit:maps_gps.get(motif))
						if (hit.rank<=(j+1)*step){			// if the peak is in top rank range
							total+= Math.abs(hit.offset);
							count++;
						}
					for (MotifHit hit:maps_macs.get(motif))
						if (hit.rank<=(j+1)*step){
							total_macs+= Math.abs(hit.offset);
							count_macs++;
						}
				}
				resolution[0][i][j] = total/(double)count;
				resolution[1][i][j] = total_macs/(double)count_macs;
			}
		}
		
		// output results
		StringBuilder sb = new StringBuilder();
		sb.append("#"+motif.name+" "+ motif.version+"\n");
		sb.append("#Motif Score");
		for(int j=0;j<motifThresholds.length;j++)
			sb.append("\t"+String.format("%.2f", motifThresholds[j]));
		sb.append("\n");
		
		for(int j=0; j<stepCount; j++){
			sb.append((j+1)*step);
			for(int i=0;i<motifThresholds.length;i++)
				sb.append("\t"+resolution[0][i][j]);
			sb.append("\n");
		}
		CommonUtils.writeFile("CTCF_GPS_SpatialResolution_"+windowSize+".txt", sb.toString());

		sb = new StringBuilder();
		sb.append("#"+motif.name+" "+ motif.version+"\n");
		sb.append("#Motif Score");
		for(int j=0;j<motifThresholds.length;j++)
			sb.append("\t"+String.format("%.2f", motifThresholds[j]));
		sb.append("\n");
		
		for(int j=0; j<stepCount; j++){
			sb.append((j+1)*step);
			for(int i=0;i<motifThresholds.length;i++)
				sb.append("\t"+resolution[1][i][j]);
			sb.append("\n");
		}
		CommonUtils.writeFile("CTCF_MACS_SpatialResolution_"+windowSize+".txt", sb.toString());
		System.out.println(CommonUtils.timeElapsed(tic));
	}
	
	// for all the shared motif, pick the nearest peaks
	// for all kinds of threshold
	private void bindingSpatialResolutionHistogram(){
		System.out.print("\nRunning Spatial Resolution analysis ...\n");
		SetTools<Point> setTools = new SetTools<Point>();
		// for each peak and motif threshold, the shortest distance between BS and motif
		for(int m=0;m<motifThresholds.length;m++){
			// get only the motifs that pass the score cutoff
			Set<Point> motifs_gps = new HashSet<Point>();
			Set<Point> motifs_macs = new HashSet<Point>();
			for (Point motif: maps_gps.keySet())
				if (maps_gps.get(motif).get(0).score >= motifThresholds[m])
					motifs_gps.add(motif);
			for (Point motif: maps_macs.keySet())
				if (maps_macs.get(motif).get(0).score >= motifThresholds[m])
					motifs_macs.add(motif);
			
			// intersection set for spatial resolution comparison
			// only consider the motif positions that are cover by all methods
			ArrayList<Point> sharedMotifs = new ArrayList<Point>();
			sharedMotifs.addAll( setTools.intersection(motifs_gps,motifs_macs));
			System.out.print(String.format("Motif score cutoff=%.2f,\t%d\t shared motifs\n",
					motifThresholds[m], sharedMotifs.size()));
			// calculate the spatial resolution (offset) 
			int rank = 5000;
			// array to store all spatial resolution values, [methods][shared peaks with motif]
			double[][]resolution = new double[2][sharedMotifs.size()];
			for (int i=0;i<sharedMotifs.size();i++){	// for each shared motif
				Point motif = sharedMotifs.get(i);
				int minDistance = Integer.MAX_VALUE;
				int minOffset=Integer.MAX_VALUE;
				for (MotifHit hit:maps_gps.get(motif)){
					if (hit.rank<=rank){			// if the peak is in top rank range
						if (minDistance > Math.abs(hit.offset)){
							minDistance = Math.abs(hit.offset);
							minOffset = hit.offset;
						}
					}
				}
				resolution[0][i]=minOffset;
				
				minDistance = Integer.MAX_VALUE;
				minOffset=Integer.MAX_VALUE;
				for (MotifHit hit:maps_macs.get(motif)){
					if (hit.rank<=rank){			// if the peak is in top rank range
						if (minDistance > Math.abs(hit.offset)){
							minDistance = Math.abs(hit.offset);
							minOffset = hit.offset;
						}
					}
				}
				resolution[1][i]=minOffset;
			}
			
			// output results
			StringBuilder sb = new StringBuilder();
			for(int j=0;j<sharedMotifs.size();j++){
				if (resolution[0][j]<=windowSize && resolution[1][j]<=windowSize){
					sb.append(sharedMotifs.get(j).toString()+"\t");
					sb.append(sharedMotifs.get(j).getChrom()+"\t");
					sb.append(String.format("%.2f", resolution[0][j])+"\t"+
							String.format("%.2f", resolution[1][j])+"\n");
				}
			}
			CommonUtils.writeFile("CTCF_SpatialResolutionHistogram_"
					+String.format("%.2f_",motifThresholds[m])
					+windowSize+".txt", sb.toString());
		}
	}
	

	// Motif Coverage analysis is to assess the sensitivity of the peak calling methods
	// We generate a high confident "true" binding motif list by all the strong motifs that
	// are covered by any of two methods, or covered by majority of 3+ methods.
	// Then for each motif score threshold, we determine how many of the motifs are covered 
	// by the top ranking peaks called by each method
	private void motifCoverage(){
		System.out.print("\nRunning Motif Coverage analysis ...\n");
		long tic = System.currentTimeMillis();
		SetTools<Point> setTools = new SetTools<Point>();
		
		int peakCount = Math.min(peaks_gps.size(), peaks_macs.size());
		int step = 100;
		int stepCount = peakCount/step;

		// array to store motif coverage count, [diff methods][motif scores][peak rank steps]
		int[][][]motifCovered = new int[2][motifThresholds.length][stepCount];
		int motifTotal[] = new int[motifThresholds.length];
		
		for(int i=0;i<motifThresholds.length;i++){
			// get only the motifs that pass the score cutoff
			Set<Point> motifs_gps = new HashSet<Point>();
			Set<Point> motifs_macs = new HashSet<Point>();
			for (Point motif: maps_gps.keySet())
				if (maps_gps.get(motif).get(0).score >= motifThresholds[i])
					motifs_gps.add(motif);
			for (Point motif: maps_macs.keySet())
				if (maps_macs.get(motif).get(0).score >= motifThresholds[i])
					motifs_macs.add(motif);
					
			//TODO
			// union set for sensitivity analysis
			// all the possible motif positions that are covered by any methods
			// if we have more methods, covered by majority of methods ...
			Set<Point> union = setTools.union(motifs_gps,motifs_macs);
			motifTotal[i]=union.size();
			System.out.print(String.format("Motif score cutoff=%.2f,\t%d\t total motifs\n",
					motifThresholds[i], union.size()));
			// calculate the average motif coverage in the range of rank steps
			for (int j=0;j<stepCount;j++) {
				int count = 0;
				int count_macs = 0;
				for (Point motif:union){				// for every motif
					if (maps_gps.containsKey(motif))
						for (MotifHit hit:maps_gps.get(motif))
							if (hit.rank<=(j+1)*step){			// if the peak is in top rank range
								count++;
								break;							// only one is enough
							}
					if (maps_macs.containsKey(motif))
						for (MotifHit hit:maps_macs.get(motif))
							if (hit.rank<=(j+1)*step){
								count_macs++;
								break;							
							}
				}
				motifCovered[0][i][j] = count;
				motifCovered[1][i][j] = count_macs;
			}
		}
		
		// output results
		StringBuilder sb = new StringBuilder();
		sb.append("#"+motif.name+" "+ motif.version+"\n");
		sb.append("#Motif Score");
		for(int j=0;j<motifThresholds.length;j++)
			sb.append("\t"+String.format("%.2f", motifThresholds[j]));
		sb.append("\n");		
		sb.append("#Total Motif");
		for(int j=0;j<motifThresholds.length;j++)
			sb.append("\t"+String.format("%d", motifTotal[j]));
		sb.append("\n");
		
		for(int j=0; j<stepCount; j++){
			sb.append((j+1)*step);
			for(int i=0;i<motifThresholds.length;i++)
				sb.append("\t"+motifCovered[0][i][j]);
			sb.append("\n");
		}
		CommonUtils.writeFile("CTCF_GPS_MotifCoverage_"+windowSize+".txt", sb.toString());

		sb = new StringBuilder();
		sb.append("#"+motif.name+" "+ motif.version+"\n");
		sb.append("#Motif Score");
		for(int j=0;j<motifThresholds.length;j++)
			sb.append("\t"+String.format("%.2f", motifThresholds[j]));
		sb.append("\n");		
		sb.append("#Total Motif");
		for(int j=0;j<motifThresholds.length;j++)
			sb.append("\t"+String.format("%d", motifTotal[j]));
		sb.append("\n");
		
		for(int j=0; j<stepCount; j++){
			sb.append((j+1)*step);
			for(int i=0;i<motifThresholds.length;i++)
				sb.append("\t"+motifCovered[1][i][j]);
			sb.append("\n");
		}
		CommonUtils.writeFile("CTCF_MACS_MotifCoverage_"+windowSize+".txt", sb.toString());
		System.out.println(CommonUtils.timeElapsed(tic));
	}	
	
	// get the all nearest motif hits in the region (within windowSize) 
	// around each peak in the list
	// if a motif have more than one peaks nearby, associate it with all the peaks
	
	// returns a map of motif -- map to all associated peaks
	private HashMap<Point, ArrayList<MotifHit>> getAllNearestMotifs(ArrayList<Point> peaks, double threshold){	
		HashMap<Point, ArrayList<MotifHit>> motifs = new HashMap<Point, ArrayList<MotifHit>>();
		WeightMatrixScorer scorer = new WeightMatrixScorer(motif);
		
		for(int i=0; i<peaks.size(); i++){
			if (i % 1000==0)
				System.out.println(i);
			Point peak = peaks.get(i);
			Region r= peak.expand(windowSize);
			WeightMatrixScoreProfile profiler = scorer.execute(r);
			//search for whole region
			for(int z=0; z<r.getWidth(); z++){		
				double score = profiler.getMaxScore(z);
				if(score >= threshold){
					Point motifPos = new Point(genome, peak.getChrom(), r.getStart()+z+profiler.getMatrix().length()/2);
					MotifHit hit = new MotifHit(motifPos, score, peak, i);
					//if other peaks also associate with this motif position, add to the list
					if (motifs.containsKey(motifPos))	
						motifs.get(motifPos).add(hit) ;
					else{
						ArrayList<MotifHit> hits = new ArrayList<MotifHit>();
						hits.add(hit);
						motifs.put(motifPos, hits);
					}
				}
			}
		}
		return motifs;
	}

}
