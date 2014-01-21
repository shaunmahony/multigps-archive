package edu.psu.compbio.seqcode.gse.deepseq.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.deepseq.discovery.kmer.Kmer;
import edu.psu.compbio.seqcode.gse.deepseq.utilities.CommonUtils;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.sequence.SequenceUtils;
import edu.psu.compbio.seqcode.gse.utils.stats.StatUtil;

public class CountKmers {
	static Genome genome = null;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		long tic = System.currentTimeMillis();
		
		// get genome info
		ArgParser ap = new ArgParser(args);
		Set<String> flags = Args.parseFlags(args);		
	    try {
	      Pair<Organism, Genome> pair = Args.parseGenome(args);
	      if(pair==null){
	        //Make fake genome... chr lengths provided???
	        if(ap.hasKey("geninfo")){
	          genome = new Genome("Genome", new File(ap.getKeyValue("geninfo")), true);
	            }else{
	              System.err.println("No genome provided; provide a Gifford lab DB genome name or a file containing chromosome name/length pairs.");System.exit(1);
	            }
	      }else{
	        genome = pair.cdr();
	      }
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }
	    
	    // read input file
		ArrayList<String> texts = CommonUtils.readTextFile(Args.parseString(args, "events", null));
		Point[] points = new Point[texts.size()];
		double[][] eventStrength = new double[texts.size()][];
		for (int i=0;i<texts.size();i++){
			String line = texts.get(i);
			if (line.length()==0)
				continue;
			String[] f = line.split("\t");
			points[i] = Point.fromString(genome, f[0]);
			double[] values = new double[f.length-1];
			for (int j=1;j<f.length;j++){
				values[j-1]=Double.parseDouble(f[j]);
			}
			eventStrength[i]=values;
		}
		int numCond = eventStrength[0].length;
		
		// get sequences
		int k = Args.parseInteger(args, "k", 8);
	    int window = Args.parseInteger(args, "window", 60);
	    int top = Args.parseInteger(args, "top", points.length);
	    
	    SequenceGenerator<Region> seqgen = new SequenceGenerator<Region>();
		seqgen.useCache(!flags.contains("no_cache"));
		seqgen.useLocalFiles(!flags.contains("use_db_genome"));
		
		String[] seqs = new String[points.length];
		HashMap<String, HashSet<Integer>> kmerstr2seqs = new HashMap<String, HashSet<Integer>>();
		for (int i=0;i<points.length;i++){
			if (points[i]==null)
				continue;
			Region r = points[i].expand(window/2);
			if (r.getWidth()!=2*(window/2)+1)		// if at the end of chromosome, skip
				continue;
			String seq = seqgen.execute(r).toUpperCase();
			seqs[i]=seq;
			HashSet<String> uniqueKmers = new HashSet<String>();	// only count repeated kmer once in a sequence
			for (int j=0;j<seq.length()-k;j++){
				if ((j+k)>seq.length()) 
					break;
				String kstring = seq.substring(j, j+k);				// endIndex of substring is exclusive
				if (kstring.contains("N"))							// ignore 'N', converted from repeat when loading the sequences
					continue;
				uniqueKmers.add(kstring);
			}
			for (String s: uniqueKmers){
				if (!kmerstr2seqs.containsKey(s)){
					 kmerstr2seqs.put(s, new HashSet<Integer>());
				}
				kmerstr2seqs.get(s).add(i);
			}
		}
		
		// Merge kmer and its reverse compliment (RC)	
		ArrayList<Kmer> kms = new ArrayList<Kmer>();
		ArrayList<String> kmerStrings = new ArrayList<String>();
		kmerStrings.addAll(kmerstr2seqs.keySet());
		
		// create kmers from its and RC's counts
		for (String key:kmerStrings){
			if (!kmerstr2seqs.containsKey(key))		// this kmer has been removed, represented by RC
				continue;
			// consolidate kmer and its reverseComplment kmer
			String key_rc = SequenceUtils.reverseComplement(key);				
			if (!key_rc.equals(key)){	// if it is not reverse compliment itself
				if (kmerstr2seqs.containsKey(key_rc)){
					int kCount = kmerstr2seqs.get(key).size();
					int rcCount = kmerstr2seqs.get(key_rc).size();
					String winner = kCount>=rcCount?key:key_rc;
					String loser = kCount>=rcCount?key_rc:key;
					kmerstr2seqs.get(winner).addAll(kmerstr2seqs.get(loser));	// winner take all
					kmerstr2seqs.remove(loser);					// remove the loser kmer because it is represented by its RC
				}
			}
		}

		// create the kmer objects
		for (String key:kmerstr2seqs.keySet()){	
			Kmer kmer = new Kmer(key, kmerstr2seqs.get(key));
			kms.add(kmer);
		}
		Collections.sort(kms);
		kmerstr2seqs=null;
		System.gc();
		System.out.println("k="+k+", mapped "+kms.size()+" k-mers, "+CommonUtils.timeElapsed(tic));
		
		// weight k-mers
		double[][] kmerWeights = new double[kms.size()][];
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<kms.size();i++){
			Kmer km = kms.get(i);
			double[] values = new double[numCond];
			for (int c=0;c<numCond;c++){
				double sum=0;
				for (int id : km.getPosHits())
					sum += eventStrength[id][c];
				values[c]=sum;
			}
//			System.out.println(km.toShortString());
			kmerWeights[i] = values;
			sb.append(km.getKmerString()+"\t"+km.getPosHitCount()+"\t"+CommonUtils.arrayToString(values, 1)).append("\n");
		}
		CommonUtils.writeFile(Args.parseString(args, "out", "out")+"_win"+window+"_kmer.txt", sb.toString());
	}

}
