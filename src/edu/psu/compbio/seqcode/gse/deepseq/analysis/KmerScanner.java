package edu.psu.compbio.seqcode.gse.deepseq.analysis;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrixImport;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.deepseq.discovery.kmer.KMAC;
import edu.psu.compbio.seqcode.gse.deepseq.discovery.kmer.Kmer;
import edu.psu.compbio.seqcode.gse.deepseq.discovery.kmer.KMAC.KmerGroup;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.CommonUtils;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSParser;
import edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq.GPSPeak;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScorer;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.sequence.SequenceUtils;

public class KmerScanner {
	public static char[] letters = {'A','C','T','G'};
	private ArrayList<Kmer> kmers;
	private KMAC kEngine;
	// each element in the list is for one ChIP-Seq method
	private ArrayList<String> methodNames = new ArrayList<String>();
	private ArrayList<ArrayList<Point>> events = new ArrayList<ArrayList<Point>>();
	
	public KmerScanner(ArrayList<Kmer> kmers, int posSeqCount, int negSeqCount){
		this.kmers = kmers;
		kEngine = new KMAC(kmers, null);
		kEngine.setTotalSeqCount(posSeqCount, negSeqCount);
	}
	
	public KmerGroup[] query (String seq){
		return kEngine.query(seq);
	}
	public KmerGroup getBestKG (String seq){
		double minScore = 0;
		KmerGroup best = null;
		KmerGroup[] kgs = query(seq);
		for (KmerGroup kg:kgs)
			if (minScore >= kg.getHgp()){
				minScore = kg.getHgp();
				best = kg;
			}
		return best;
	}	
	/**
	 * Find the file in path that match the type string
	 */
	public static String getFileName(String path, String type){
		File pathAll = new File(path);
		String name = pathAll.getName();
		File dir = pathAll.getParentFile();
		
		final String suffix = name + type;
		File[] files = dir.listFiles(new FilenameFilter(){
			public boolean accept(File arg0, String arg1) {
				if (arg1.startsWith(suffix))
					return true;
				else
					return false;
			}
		});
		if (files.length==0){
			System.out.println(name+" does not have a "+type+" file.");
			return null;
		}
		else{				// if we have valid file
			return files[0].getAbsolutePath();
		}
	}
	public static void main(String[] args){
		// k-mer group info
		String outName= Args.parseString(args, "out", "out");
		String path = Args.parseString(args, "path", null);
		System.out.println(path);
		String kmer=null, pfm=null, event=null;
		if (path!=null){
			kmer = getFileName(path, "_kmer_");
			pfm = getFileName(path, "_PFM_");
			event = path+"_GEM_events.txt";
		}
		long t1 = System.currentTimeMillis();
		File file = new File(Args.parseString(args, "kmer", kmer));
		ArrayList<Kmer> kmers = Kmer.loadKmers(file);
		int clusterId = Args.parseInteger(args, "class", 0);
		ArrayList<Kmer> toRemove = new ArrayList<Kmer>();
		for (Kmer km:kmers)
			if (km.getClusterId()!=clusterId)
				toRemove.add(km);
		kmers.removeAll(toRemove);
		kmers.trimToSize();
		Pair<Integer, Integer> c = Kmer.getTotalCounts(file);
		KmerScanner scanner = new KmerScanner(kmers, c.car(), c.cdr());
		System.out.println("KCM loading:\t"+CommonUtils.timeElapsed(t1));
		// genome info and binding events
		Genome genome=null;
		Organism org=null;
		ArgParser ap = new ArgParser(args);
		Set<String> flags = Args.parseFlags(args);		
	    try {
	      Pair<Organism, Genome> pair = Args.parseGenome(args);
	      if(pair==null){
	        System.err.println("No genome provided; provide a Gifford lab DB genome name.");;System.exit(1);
	      }else{
	        genome = pair.cdr();
	        org = pair.car();
	      }
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }
	    
	    // PWM
//		Pair<WeightMatrix, Double> wm = CommonUtils.loadPWM(args, org.getDBID());
//		WeightMatrix motif = wm.car();
//		double motifThreshold = wm.cdr();
	    	    
	    long t = System.currentTimeMillis();
	    WeightMatrix motif = CommonUtils.loadPWM_PFM_file(Args.parseString(args, "pfm", pfm), Args.parseDouble(args, "gc", 0.41)); //0.41 human, 0.42 mouse
	    System.out.println("PWM loading:\t"+CommonUtils.timeElapsed(t));
	    
		// event locations
		int windowSize = Args.parseInteger(args, "win", 50);
		String eventFile = Args.parseString(args, "event", event);
		List<GPSPeak> gpsPeaks = null;
		try{
			gpsPeaks = GPSParser.parseGPSOutput(eventFile, genome);
		}
		catch(IOException e){
			e.printStackTrace();
		}
		SequenceGenerator<Region> seqgen = new SequenceGenerator<Region>();
		seqgen.useCache(!flags.contains("no_cache"));		
		seqgen.useLocalFiles(!flags.contains("use_db_genome"));
		StringBuilder sb = new StringBuilder();
		ArrayList<Region> posRegions = new ArrayList<Region>();
		for(int i=0;i<gpsPeaks.size();i++){
			GPSPeak p = gpsPeaks.get(i);
			posRegions.add(p.expand(windowSize));
		}
				
		int width = windowSize*2+1;
		TreeMap<Region, Region> reg2reg = new TreeMap<Region, Region>();
		each_event: for(int i=0;i<gpsPeaks.size();i++){
			GPSPeak p = gpsPeaks.get(i);
			if (p.getLocation()<1000+windowSize)
				continue each_event;
			Region rNeg = new Point(genome, p.getChrom(), p.getLocation()-1000).expand(windowSize);
			if (posRegions.get(i).getWidth()==width && rNeg.getWidth()==width){
				for(Region r:posRegions){
					if (rNeg.overlaps(r))
						continue each_event;
				}
				reg2reg.put(posRegions.get(i), rNeg);
			}
		}
		
		ArrayList<Double> pwm_scores = new ArrayList<Double>();
		ArrayList<Double> pwmN_scores = new ArrayList<Double>();
		ArrayList<Double> ksm_scores = new ArrayList<Double>();
		ArrayList<Double> ksmN_scores = new ArrayList<Double>();
		
		Random randObj = new Random(Args.parseInteger(args, "seed", 0));
		System.out.println("Scanning "+reg2reg.keySet().size()+" regions ...");
		int PWM_time = 0;
		int KCM_time = 0;
		for (Region r:reg2reg.keySet()){
			String seq = seqgen.execute(r).toUpperCase();
			
			String name_N = "----";
			String seqN;
			if (flags.contains("shuffle"))
				seqN = SequenceUtils.shuffle(seq, randObj);
			if (flags.contains("di-shuffle"))
				seqN = SequenceUtils.dinu_shuffle(seq, randObj);
			else{
				Region rN = reg2reg.get(r);
				seqN = seqgen.execute(rN).toUpperCase();
				name_N = rN.toString();
			}
			long pwm_t = System.currentTimeMillis();
			double pwm = WeightMatrixScorer.getMaxSeqScore(motif, seq);
			pwm_scores.add(pwm);
			double pwmN = WeightMatrixScorer.getMaxSeqScore(motif, seqN);
			pwmN_scores.add(pwmN);
			PWM_time += System.currentTimeMillis() - pwm_t;
			
			long kcm_t = System.currentTimeMillis();
			KmerGroup kg = scanner.getBestKG(seq);
			KmerGroup kgN = scanner.getBestKG(seqN);
			ksm_scores.add(kg==null?0:-kg.getHgp());
			ksmN_scores.add(kgN==null?0:-kgN.getHgp());
			KCM_time += System.currentTimeMillis() - kcm_t;
			sb.append(String.format("%s\t%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%d\n", r.toString(), name_N, pwm, pwmN, 
					kg==null?0:-kg.getHgp(), kgN==null?0:-kgN.getHgp(), 
					kg==null?0:-kg.getBestKmer().getHgp(), kgN==null?0:-kgN.getBestKmer().getHgp(), 
					kg==null?0:kg.getBestKmer().getPosHitCount(), kgN==null?0:kgN.getBestKmer().getPosHitCount()));
		}
		
		System.out.println("Total PWM scanning time:" + PWM_time);
		System.out.println("Total KCM scanning time:" + KCM_time);
		
		CommonUtils.writeFile(outName+"_w"+width+"_scores.txt", sb.toString());
		System.out.println(outName+"_w"+width+"_scores.txt");
		
		if (flags.contains("compute_enrichment")){
			ArrayList<ScoreEnrichment> pwm_se = computeScoreEnrichments(pwm_scores, pwmN_scores);
			sb = new StringBuilder();
			for (ScoreEnrichment se: pwm_se){
				sb.append(String.format("%.2f\t%d\t%d\t%.2f\n", se.score, se.posHit, se.negHit, se.hgp));
			}
			CommonUtils.writeFile(outName+"_w"+width+"_pwm_enrichment.txt", sb.toString());
			
			ArrayList<ScoreEnrichment> ksm_se = computeScoreEnrichments(ksm_scores, ksmN_scores);
			sb = new StringBuilder();
			for (ScoreEnrichment se: ksm_se){
				sb.append(String.format("%.2f\t%d\t%d\t%.2f\n", se.score, se.posHit, se.negHit, se.hgp));
			}
			CommonUtils.writeFile(outName+"_w"+width+"_ksm_enrichment.txt", sb.toString());
		}
//		KmerGroup[] kgs = scanner.query("TATTTACATGCAGTGTCCGGAAGACGCCAGAAGAGGGCAGTAGATGCCCTAGTAGTGGAGC");
//		for (KmerGroup kg:kgs){
//			System.out.println(kg.toString());
//		}
	}
	
	private static WeightMatrix loadPWM(File file, double gc ){
		WeightMatrix wm;
		try{
			List<WeightMatrix> wms = WeightMatrixImport.readTRANSFACFreqMatrices(file.getAbsolutePath(), "file");
			if (wms.isEmpty()){
				wm=null;
				System.out.println(file.getName()+" is not a valid motif file.");
			}
			else{		// if we have valid PFM, convert it to PWM
				wm = wms.get(0);		// only get primary motif
				float[][] matrix = wm.matrix;
				// normalize
		        for (int position = 0; position < matrix.length; position++) {
		            double sum = 0;
		            for (int j = 0; j < letters.length; j++) {
		                sum += matrix[position][letters[j]];
		            }
		            for (int j = 0; j < letters.length; j++) {
		                matrix[position][letters[j]] = (float)(matrix[position][letters[j]] / sum);
		            }
		        }
		        // log-odds
		        for (int pos = 0; pos < matrix.length; pos++) {
		            for (int j = 0; j < letters.length; j++) {
		                matrix[pos][letters[j]] = (float)Math.log(Math.max(matrix[pos][letters[j]], .000001) / 
		                		(letters[j]=='G'||letters[j]=='C'?gc/2:(1-gc)/2));
		            }
		        } 
			}
		}
		catch (IOException e){
			System.out.println(file.getName()+" motif PFM file reading error!!!");
			wm = null;
		}
		return wm;
	}
	
	private static ArrayList<ScoreEnrichment> computeScoreEnrichments(ArrayList<Double> posScores, ArrayList<Double> negScores){
		int total  = posScores.size();		
		double posSeqScores[] = new double[total];
		double negSeqScores[] = new double[total];
		for (int i=0;i<total;i++){
			posSeqScores[i]=posScores.get(i);
			negSeqScores[i]=negScores.get(i);			
		}
		ArrayList<ScoreEnrichment> ses = new ArrayList<ScoreEnrichment> ();
		Arrays.sort(posSeqScores);		
		Arrays.sort(negSeqScores);
		
		// find the threshold motif score
		TreeSet<Double> posScoreUnique = new TreeSet<Double>();
		for (double s:posSeqScores)
			posScoreUnique.add(s);
		Double[] posScores_u = new Double[posScoreUnique.size()];
		posScoreUnique.toArray(posScores_u);
		for (int i=0;i<posScores_u.length;i++){
			double score = posScores_u[i];
			if (score<=0)
				continue;
			ScoreEnrichment se = new ScoreEnrichment();
			se.score = score;
			int index = CommonUtils.findKey(posSeqScores, score);
			se.posHit = posSeqScores.length-index;
			index = CommonUtils.findKey(negSeqScores, score);
			se.negHit = negSeqScores.length-index;
			se.hgp = KMAC.computeHGP(total, total, se.posHit, se.negHit);
			ses.add(se);
		}
		return ses;
	}
	
	private static class ScoreEnrichment{
		double score;
		int posHit;
		int negHit;
		double hgp;
	}
}
