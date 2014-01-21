package edu.psu.compbio.seqcode.gse.deepseq.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.SeqLocator;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.deepseq.BindingModel;
import edu.psu.compbio.seqcode.gse.deepseq.DeepSeqExpt;
import edu.psu.compbio.seqcode.gse.deepseq.StrandedBase;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.CommonUtils;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.ReadCache;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSParser;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSPeak;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.stats.StatUtil;

public class EnhancerChromatinAnalysis {
	private Genome genome;
	private String[] args;
	
	private boolean dev = false;
	private boolean isPreSorted = false;	
	private boolean fromFile = false;
	
	private int windowSize = 1000;	
	private int modelRange = 1500;	
	private int rank = 1000;
	private int smooth_step = 30;
	private boolean sortByStrength = false;
	private String outName="out";
	
	// each element in the list is for one ChIP-Seq method
	private ArrayList<String> markNames = new ArrayList<String>();
	private HashMap<String, Integer> markIDs = new HashMap<String, Integer>();
	private ArrayList<ReadCache> caches = null;
	private ArrayList<Point> events = new ArrayList<Point>();
	ArrayList<Point> classI = new ArrayList<Point>();
	ArrayList<Point> classII = new ArrayList<Point>();
	private double[][] profiles_I;
	private double[][] profiles_II;

	/**
	 * @param args
	 */
	// --species "Homo sapiens;hg18" --peakCallGPS "p300_esc_2_GPS_significant.txt" --analysisType 0 --out "ESC" --dev --from_file
	public static void main(String[] args) throws IOException {
		EnhancerChromatinAnalysis analysis = new EnhancerChromatinAnalysis(args);

		int type = Args.parseInteger(args, "analysisType", 0);
		switch(type){
		case 0:analysis.findEnhancers();break;
		default: System.err.println("Unrecognize analysis type: "+type);
		}
	}
	
	EnhancerChromatinAnalysis(String[] args){
		this.args = args;
		ArgParser ap = new ArgParser(args);
		Set<String> flags = Args.parseFlags(args);		
	    try {
	      Pair<Organism, Genome> pair = Args.parseGenome(args);
	      if(pair==null){
	        if(ap.hasKey("geninfo")){
	          genome = new Genome("Genome", new File(ap.getKeyValue("geninfo")), true);
	        }else{
              System.err.println("No genome provided; provide a Gifford lab DB genome name or a file containing chromosome name/length pairs.");;System.exit(1);
            }
	      }else{
	        genome = pair.cdr();
	      }
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }
	    	    
		// some parameters
		windowSize = Args.parseInteger(args, "windowSize", windowSize);
		modelRange = Args.parseInteger(args, "modelRange", modelRange);
		rank = Args.parseInteger(args, "rank", rank);
		smooth_step = Args.parseInteger(args, "smooth", smooth_step);
		outName = Args.parseString(args,"out",outName);
		sortByStrength = flags.contains("ss");	// only for GPS
		isPreSorted = flags.contains("sorted");
		dev = flags.contains("dev");
		fromFile = flags.contains("from_file");
		
		markNames.add("H3K4me1");
		markNames.add("H3K27ac");
		markNames.add("H3K27me3");
		markNames.add("H3K4me3");
		markNames.add("input");
		for (int i=0;i<markNames.size();i++){
			markIDs.put(markNames.get(i), i);
		}
		try{
			events = readPeakLists();
		}
		catch (IOException e){
			e.printStackTrace(System.err);
			System.exit(-1);
		}

		loadChIPSeqData();
	}
	/**
	 * Find 2 classes of enhancers using P300 binding events and chromatin marks
	 * @throws IOException
	 */
	// This method print 2 text files: class I and II enhancer coordinates
	private void findEnhancers() throws IOException {
		long tic = System.currentTimeMillis();
		for (Point p: events){
			Region r = p.expand(windowSize);
			if (isEnriched(r, "H3K4me1", "input", 10) && isEnriched(r, "H3K27ac", "input", 10) && (!isEnriched(r, "H3K4me3", "input", 30)))
				classI.add(p);
			if (isEnriched(r, "H3K27me3", "input", 8) && (!isEnriched(r, "H3K27ac", "input", 10)) && (!isEnriched(r, "H3K4me3", "input", 30)))
//				if (isEnriched(r, "H3K4me1", "input", 10) && isEnriched(r, "H3K27me3", "input", 8) && (!isEnriched(r, "H3K27ac", "input", 10)) && (!isEnriched(r, "H3K4me3", "input", 30)))
				classII.add(p);
		}
		System.out.println(outName+"_enhancer_I:\t"+classI.size());
		StringBuilder sb = new StringBuilder();
		for (Point p:classI)
			sb.append(p.toString()).append("\n");
		CommonUtils.writeFile(outName+"_I_coords.txt", sb.toString());
		
		ArrayList<Integer> marksToClassify = new ArrayList<Integer>();
		marksToClassify.add(1); 	//H3K27ac
		marksToClassify.add(2); 	//H3K27me3
		profiles_I=new double[marksToClassify.size()][modelRange*2+1];
		generateProfiles("I", classI, profiles_I, marksToClassify, modelRange, modelRange);
		
		System.out.println(outName+"_enhancer_II:\t"+classII.size());
		sb = new StringBuilder();
		for (Point p:classII)
			sb.append(p.toString()).append("\n");
		CommonUtils.writeFile(outName+"_II_coords.txt", sb.toString());
		
		profiles_II=new double[marksToClassify.size()][modelRange*2+1];
		generateProfiles("II", classII, profiles_II, marksToClassify, modelRange, modelRange);

		computeLikelihoodRatio("I", classI, marksToClassify);
		computeLikelihoodRatio("II", classII, marksToClassify);
		
		System.out.println("Done! " + CommonUtils.timeElapsed(tic));
	}
	
	private boolean isEnriched(Region region, String ip, String ctrl, double ip_min){
		float ip_count = caches.get(markIDs.get(ip)).countHits(region);
		float ctrl_count = caches.get(markIDs.get(ctrl)).countHits(region);
		if (ctrl_count==0)
			ctrl_count = 1;
		return ip_count>ip_min && ip_count/ctrl_count>2.5;
	}

	private ArrayList<Point> readPeakLists() throws IOException {
        String peakTag=null;
        for(String s : args)
        	if(s.contains("peakCall"))
        		peakTag=s;
    	
        // each tag represents a peak caller output file
    	String name="";
    	if(peakTag.startsWith("--peakCall")){
    		name = peakTag.replaceFirst("--peakCall", ""); 
    	}
    	List<File> files = Args.parseFileHandles(args, "peakCall"+name);
    	File file= null;
    	if (!files.isEmpty()){
    		file = files.get(0);
    	}

    	String filePath = file.getAbsolutePath();
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
				if (p.getStrength()>30)				// p300 strength > 30
					peakPoints.add(p);
			}
    	}
    	else{
			peakPoints = CommonUtils.loadCgsPointFile(filePath, genome);
    	}  
    	System.out.println(peakPoints.size()+" p300 events are loaded.");
    	return peakPoints;
	}
	
	/*****************************************************
	 * Load ChIP-Seq data (adapted from KPPMixture.java)
	 * ***************************************************/
	private void loadChIPSeqData(){
		final int MAXREAD = 1000000;
		String cellStr = "hESC";
		
		this.caches = new ArrayList<ReadCache>();
		for (int i=0;i<markNames.size();i++){
			ReadCache ipCache = new ReadCache(genome, markNames.get(i));
			caches.add(ipCache);
			
			if (!fromFile){
				String expt = "Wysocka "+cellStr+" "+markNames.get(i)+" H9";
				String align = "prealigned_unique";
				List<SeqLocator> locators = new ArrayList<SeqLocator>();
				locators.add(new SeqLocator(expt, align));
				DeepSeqExpt ip = new DeepSeqExpt(genome, locators, "readdb", -1);
	
				// cache sorted start positions and counts of all positions
				long tic = System.currentTimeMillis();
				System.out.print("Loading "+ipCache.getName()+" data from ReadDB ... \t");
				List<String> chroms = genome.getChromList();
				if (dev){
					chroms = new ArrayList<String>();
					chroms.add("22");
				}
				for (String chrom: chroms ){
					// load  data for this chromosome.
					int length = genome.getChromLength(chrom);
					Region wholeChrom = new Region(genome, chrom, 1, length);
					int count = ip.countHits(wholeChrom);
					ArrayList<Region> chunks = new ArrayList<Region>();
					// if there are too many reads in a chrom, read smaller chunks
					if (count>MAXREAD){
						int chunkNum = count/MAXREAD*2+1;
						int chunkLength = length/chunkNum;
						int start = 0;
						while (start<=length){
							int end = Math.min(length, start+chunkLength-1);
							Region r = new Region(genome, chrom, start, end);
							start = end+1;
							chunks.add(r);
						}
					}else
						chunks.add(wholeChrom);
	
					for (Region chunk: chunks){
						Pair<ArrayList<Integer>,ArrayList<Float>> hits = ip.loadStrandedBaseCounts(chunk, '+');
						ipCache.addHits(chrom, '+', hits.car(), hits.cdr());
						hits = ip.loadStrandedBaseCounts(chunk, '-');
						ipCache.addHits(chrom, '-', hits.car(), hits.cdr());
					}
				} // for each chrom
	
				ipCache.populateArrays(true);
				ip.closeLoaders();
				ip=null;
				System.gc();
				ipCache.displayStats();
				ipCache.writeRSC();
				System.out.println(CommonUtils.timeElapsed(tic));
			}
			else{
				try{
					ipCache.readRSC(ipCache.getName()+".rsc");
				}
				catch(IOException e){
					e.printStackTrace(System.err);
				}
				ipCache.displayStats();
			}
		}
	}

	private void generateProfiles(String classType, ArrayList<Point> coords, double[][] profiles, ArrayList<Integer> marksToClassify, int left, int right){
		int width = left+right+1;
		int numMarks = marksToClassify.size();
        double[][] profile_plus=new double[numMarks][width];
        double[][] profile_minus=new double[numMarks][width];
		// sum the read profiles
		for (int c=0;c<numMarks;c++){
			ReadCache ip = caches.get(marksToClassify.get(c));
			for (int i=0;i<Math.min(rank, coords.size());i++){
				Point p = coords.get(i);
				Region region = p.expand(0).expand(left, right);
				List<StrandedBase> bases = ip.getStrandedBases(region, '+');
				for (StrandedBase base:bases){
					profile_plus[c][base.getCoordinate()-region.getStart()]+=base.getCount();
				}
				region = p.expand(0).expand(right, left);
				bases = ip.getStrandedBases(region, '-');
				for (StrandedBase base:bases){
					profile_minus[c][region.getEnd()-base.getCoordinate()]+=base.getCount();
				}
			}

			// smooth the read profile
			if (smooth_step>0){
				profile_plus[c] = StatUtil.cubicSpline(profile_plus[c], smooth_step, smooth_step);
				profile_minus[c] = StatUtil.cubicSpline(profile_minus[c], smooth_step, smooth_step);
			}

			//compare profiles from 2 strands, shift binding position if needed
			BindingModel.minKL_Shift(profile_plus[c], profile_minus[c] );
		}
		
		// output the read density
		double[] scaleFactors = new double[numMarks];
		for (int c=0;c<numMarks;c++){
			scaleFactors[c] = caches.get(marksToClassify.get(c)).getHitCount();
		}
		StatUtil.mutate_normalize(scaleFactors);
		
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<width;i++){
			sb.append(i-left).append("\t");
			for (int c=0;c<numMarks;c++){
				double sum = (profile_plus[c][i]+profile_minus[c][i])/4.0/scaleFactors[c];
				sum = sum>=0?sum:2.0E-300;
				profiles[c][i] = sum;
				sb.append(String.format("%.2f\t",sum));
			}
			sb.append("\n");
		}
		
		// normalize profiles across all marks
		double total = 0;
		for (int i=0;i<width;i++){
			for (int c=0;c<numMarks;c++){
				total += profiles[c][i];
			}
		}	
		if (total>0){
			for (int i=0;i<width;i++){
				for (int c=0;c<numMarks;c++){
					profiles[c][i] /= total;
				}
			}
		}
		CommonUtils.writeFile(outName+"_"+classType+"_profiles.txt", sb.toString());
	}
	
	// Compute the likelihood ratio of a p300 event region fitting to class I and II enhancer profiles
	private void computeLikelihoodRatio(String classType, ArrayList<Point> coords, ArrayList<Integer> marksToClassify) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("Event   \t");
		for (int c=0;c<markNames.size();c++){
			sb.append(markNames.get(c)+"\t");
		}
		sb.append("ll_I\tll_II\tllr\n");
		for (Point p: coords){
			sb.append(p.toString()+"\t");
			int pos = p.getLocation();
			double ll_I = 0;
			double ll_II = 0;
			for (int c=0;c<marksToClassify.size();c++){
				List<StrandedBase> bases = caches.get(marksToClassify.get(c)).getStrandedBases(p.expand(modelRange), '+');
				bases.addAll(caches.get(marksToClassify.get(c)).getStrandedBases(p.expand(modelRange), '-'));
				for(StrandedBase b : bases){
					int dist = b.getStrand()=='+' ? b.getCoordinate()-pos:pos-b.getCoordinate();
					int idx = dist + modelRange;
					ll_I += Math.log(profiles_I[c][idx])*b.getCount();
					ll_II += Math.log(profiles_II[c][idx])*b.getCount();
				}
			}
			// output the strength of each mark and input in range of +-1kb
			for (int c=0;c<markNames.size();c++){
				sb.append(String.format("%.0f\t", caches.get(c).countHits(p.expand(windowSize))));
			}
			// output log likelihood, ratio
			double llr = ll_I - ll_II;
			sb.append(String.format("%.1f\t%.1f\t%.1f\n", ll_I, ll_II, llr));
		}
		CommonUtils.writeFile(outName+"_"+classType+"_likelihood_ratio.txt", sb.toString());
	}
}
