package edu.psu.compbio.seqcode.gse.deepseq.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.general.StrandedPoint;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.SeqLocator;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.deepseq.BindingModel;
import edu.psu.compbio.seqcode.gse.deepseq.DeepSeqExpt;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.CommonUtils;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSParser;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSPeak;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScoreProfile;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScorer;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;

public class GPS_ReadDistribution {
	static final int MOTIF_DISTANCE = 50;	
	
	private Genome genome;
	private Organism org;
	
	private ArrayList<Point> points = new ArrayList<Point>(); 
	private String GPSfileName;
	private int strength=40;
	private int mrc = 10;

	// build empirical distribution
	private DeepSeqExpt chipSeqExpt = null;
	private int range = 250;
	private int smooth_step = 10;
	private int top = 1000;
	
	private WeightMatrix motif = null;
	private double motifThreshold;
	
	private String name = null;
	private String motif_strand = null;
	
	public static void main(String[] args) throws IOException {
		GPS_ReadDistribution analysis = new GPS_ReadDistribution(args);
		analysis.printEmpiricalDistribution(analysis.points);;
	}
	
	public GPS_ReadDistribution(String[] args) throws IOException {
		ArgParser ap = new ArgParser(args);
		
		try {
			Pair<Organism, Genome> pair = Args.parseGenome(args);
			if(pair==null){
				//Make fake genome... chr lengths provided???
				if(ap.hasKey("g")){
					genome = new Genome("Genome", new File(ap.getKeyValue("g")), true);
	        	}else{
	        		System.err.println("No genome information provided."); 
	        		System.exit(1);
	        	}
			}else{
				org = pair.car();
				genome = pair.cdr();
			}
		} catch (NotFoundException e) {
			e.printStackTrace();
		}
		
//		Map<String, Integer> map = genome.getChromLengthMap();
//		for (String chr:map.keySet()){
//			System.out.println(chr+"\t"+map.get(chr));
//		}
		
		// parameter for building empirical distribution
		String chipSeqFile = Args.parseString(args, "chipseq", null);
		if (chipSeqFile!=null){
			String fileFormat = Args.parseString(args, "f", "BED");  
			List<File> expts = new ArrayList<File>();
			expts.add(new File(chipSeqFile));
			chipSeqExpt = new DeepSeqExpt(genome, expts, false, fileFormat, -1);
		}
		else{
			List<SeqLocator> rdbexpts = Args.parseSeqExpt(args,"rdb");
			chipSeqExpt = new DeepSeqExpt(genome, rdbexpts, "readdb", -1);
		}
			
		range = Args.parseInteger(args, "range", range);
		top = Args.parseInteger(args, "top", top);			// number of top point positions to use
		mrc = Args.parseInteger(args, "mrc", mrc);			// max read count
		name = Args.parseString(args, "name", "noname");
		motif_strand = Args.parseString(args, "motif_strand", null);
		smooth_step = Args.parseInteger(args, "smooth", smooth_step);
		// load points
		String coordFile = Args.parseString(args, "coords", null);
		ArrayList<Point> coords = null;
		if (coordFile!=null){
			coords = loadCgsPointFile(coordFile, top);
		}
		else{
			// load GPS results
			GPSfileName = Args.parseString(args, "GPS", null);
			if (GPSfileName==null){
				System.err.println("Coordinate file not found!");
				System.exit(0);
			}
			File gpsFile = new File(GPSfileName);
			List<GPSPeak>gpsPeaks = GPSParser.parseGPSOutput(gpsFile.getAbsolutePath(), genome);
			strength = Args.parseInteger(args,"strength",40);
			coords = new ArrayList<Point>();
			for (GPSPeak gps: gpsPeaks){
				if ((!gps.isJointEvent()) && gps.getStrength()>strength )
					coords.add(gps);
			}
		}
		
		// load motif
	    Pair<WeightMatrix, Double> wm = null;
	    WeightMatrixScorer scorer=null;
	    if (Args.parseString(args, "motif", null)!=null||Args.parseString(args, "pfm", null)!=null){
			wm = CommonUtils.loadPWM(args, org.getDBID());
			System.out.println("Using motif "+wm.getFirst().name);
			scorer = new WeightMatrixScorer(wm.getFirst());
			motifThreshold = wm.cdr().doubleValue();
	    }		
		// get points or motif points
		for (Point p: coords){
			if (points.size()>=top)
				break;
			
			if (wm!=null){
				Region r= p.expand(MOTIF_DISTANCE);
				WeightMatrixScoreProfile profiler = scorer.execute(r);
				int halfWidth = profiler.getMatrix().length()/2;
				//search from BS outwards, to find the nearest strong motif
				for(int z=0; z<=r.getWidth()/2; z++){
					if (MOTIF_DISTANCE+z>=profiler.length())
						continue;
					double leftScore= profiler.getMaxScore(MOTIF_DISTANCE-z);
					double rightScore= profiler.getMaxScore(MOTIF_DISTANCE+z);	
					int position = -Integer.MAX_VALUE;	// middle of motif, relative to GPS peak
					char strand = '*';
					if(rightScore>=motifThreshold){
						position = z+halfWidth;
						strand = profiler.getMaxStrand(MOTIF_DISTANCE+z);
					}
					if(leftScore>=motifThreshold && leftScore>rightScore){
						position = -z+halfWidth;
						strand = profiler.getMaxStrand(MOTIF_DISTANCE-z);
					}
					if(position > -Integer.MAX_VALUE){
						Set<String> flags = Args.parseFlags(args);
						Point motifPos = null;
						if (flags.contains("stranded"))
							motifPos = new StrandedPoint(genome, p.getChrom(), p.getLocation()+position, strand);
						else if (motif_strand==null || (motif_strand.equalsIgnoreCase("F")&& strand=='+')||(motif_strand.equalsIgnoreCase("R")&& strand=='-'))
							motifPos = new Point(genome, p.getChrom(), p.getLocation()+position);
						else
							break;
						points.add(motifPos);
						break; 	// break from the motif search of this peak
					}
				}
			}
			else
				points.add(p);
		}	// each point
		System.out.println(points.size()+" coordinates are used.");
	}

	private void printEmpiricalDistribution(ArrayList<Point> points){
		BindingModel model_plus = getStrandedDistribution (points, '+');
		model_plus.printToFile(String.format("Read_Distribution_%s_%d_plus.txt", name, points.size()));
		BindingModel model_minus = getStrandedDistribution (points, '-');
		model_minus.printToFile(String.format("Read_Distribution_%s_%d_minus.txt", name, points.size()));
		
		double[] prob_plus = model_plus.getProbabilities();
		double[] prob_minus = model_minus.getProbabilities();
		BindingModel.minKL_Shift(prob_plus, prob_minus);
		
		ArrayList<Pair<Integer, Double>> dist = new ArrayList<Pair<Integer, Double>>();
		for(int i=model_plus.getMin();i<=model_plus.getMax();i++){
			int index = i-model_plus.getMin();
			dist.add(new Pair<Integer, Double>(i, prob_plus[index]+prob_minus[index]));
		}
		BindingModel model=new BindingModel(dist);
		String outFile = String.format("Read_Distribution_%s_%d.txt", name, points.size());
		model.printToFile(outFile);
		System.out.println(outFile+" is written.");
	}	
	
	private BindingModel getStrandedDistribution (ArrayList<Point> points, char strand){
		Map<String,Integer> chromLengthMap = genome.getChromLengthMap();
		float[] sum = new float[2*range+1];
		Pair<ArrayList<Integer>,ArrayList<Float>> pair = null;
		for (Point p:points){
			int pos = p.getLocation();
			if (!chromLengthMap.containsKey(p.getChrom()) || pos>chromLengthMap.get(p.getChrom()))
				continue;
			if (p instanceof StrandedPoint){
				char point_strand = ((StrandedPoint) p).getStrand();
				pair = chipSeqExpt.loadStrandedBaseCounts(p.expand(range), point_strand=='+'?strand:(char)(88-strand));
				// convert absolute coordinates to relative offset
				ArrayList<Integer> coords = pair.car();
				for (int i=0;i<coords.size();i++){
					int offset = coords.get(i)-pos;
					if (point_strand=='-')
						offset = -offset;
					coords.set(i, offset);
				}
			}
			else{
				pair = chipSeqExpt.loadStrandedBaseCounts(p.expand(range), strand);
				// convert absolute coordinates to relative offset
				ArrayList<Integer> coords = pair.car();
				for (int i=0;i<coords.size();i++){
					int offset = coords.get(i)-pos;
					coords.set(i, offset);
				}
			}
			for (int i=0;i<pair.car().size();i++){
				sum[pair.car().get(i)+range] += Math.min(pair.cdr().get(i), mrc);
			}
		}

		ArrayList<Pair<Integer, Double>> dist = new ArrayList<Pair<Integer, Double>>();
		if (strand=='-')
			for(int i=sum.length-1;i>=0;i--){
				int pos = -(i-sum.length/2);
				dist.add(new Pair<Integer, Double>(pos, (double)sum[i]));
			}
		else
			for(int i=0;i<sum.length;i++){
				int pos = i-sum.length/2;
				dist.add(new Pair<Integer, Double>(pos, (double)sum[i]));
			}
		BindingModel model=new BindingModel(dist);
		if (smooth_step>0)
			model.smooth(smooth_step, smooth_step);
		return model;
	}
	
	private ArrayList<Point> loadCgsPointFile(String filename, int ptCount) {

		File file = new File(filename);
		if(!file.isFile()){
			System.err.println("\nCannot find coordinate file!");
			System.exit(1);
		}
		FileReader in = null;
		BufferedReader bin = null;
		ArrayList<Point> points = new ArrayList<Point>();
		try {
			in = new FileReader(file);
			bin = new BufferedReader(in);
			String line;
			while((line = bin.readLine()) != null) { 
				if (points.size()>=ptCount)
					break;
				line = line.trim();
				Region point = Region.fromString(genome, line);
				if (point!=null)
					points.add(new Point(genome, point.getChrom(),point.getStart()));
			}
		}
		catch(IOException ioex) {
			System.err.println("Error when parsing coordinate file! ");
			ioex.printStackTrace(System.err);
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
}
