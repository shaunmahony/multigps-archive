package edu.psu.compbio.seqcode.gse.viz.metagenes;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.general.StrandedPoint;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScoreProfile;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.WeightMatrixScorer;

public class MotifProfiler implements PointProfiler<Point, Profile>{

	private WeightMatrix motif;
	private WeightMatrixScorer scorer;
	private SequenceGenerator seqgen;
	private Genome gen;
	private BinningParameters params=null;
	private double minThreshold=0;
	
	public MotifProfiler(BinningParameters bp, Genome g, WeightMatrix wm, double minThres){
		minThreshold=minThres;
		gen=g;
		params=bp; 
		motif=wm;
		scorer = new WeightMatrixScorer(motif);
		seqgen = new SequenceGenerator();
	}

	public BinningParameters getBinningParameters() {
		return params;
	}

	public Profile execute(Point a) {
		double[] array = new double[params.getNumBins()];
		for(int i = 0; i < array.length; i++) { array[i] = 0; }
		
		int window = params.getWindowSize();
		int left = window/2;
		int right = window-left-1;
		
		int start = Math.max(1, a.getLocation()-left);
		int end = Math.min(a.getLocation()+right, a.getGenome().getChromLength(a.getChrom()));
		Region query = new Region(gen, a.getChrom(), start, end);
		boolean strand = (a instanceof StrandedPoint) ? 
				((StrandedPoint)a).getStrand() == '+' : true;
		
		String seq = seqgen.execute(query);
		WeightMatrixScoreProfile profiler = scorer.execute(seq);
		for(int i=query.getStart(); i<query.getEnd(); i+=params.getBinSize()){
			double maxScore=Double.MIN_VALUE;
			int maxPos=0;
			for(int j=i; j<i+params.getBinSize() && j<query.getEnd(); j++){
				int offset = j-query.getStart();
				
				if(profiler.getMaxScore(offset)>maxScore){
					maxScore= profiler.getMaxScore(offset); 
					maxPos=offset;					
				}
			}
			if(maxScore>=minThreshold){
				int startbin, stopbin;

				startbin = params.findBin(maxPos);
				stopbin = params.findBin(maxPos+motif.length()-1);
				
				if(!strand) { 
					int tmp = (params.getNumBins()-stopbin)-1;
					stopbin = (params.getNumBins()-startbin)-1;
					startbin = tmp;
				}
	
				//addToArray(startbin, stopbin, array, maxScore);
				maxToArray(startbin, stopbin, array, maxScore);
			}
		}
		
		return new PointProfile(a, params, array, (a instanceof StrandedPoint));
	}

	private void addToArray(int i, int j, double[] array, double value) { 
		for(int k = i; k <= j; k++) { 
			array[k] += value;
		}
	}
	private void maxToArray(int i, int j, double[] array, double value) { 
		for(int k = i; k <= j; k++) { 
			array[k] = Math.max(array[k],value);
		}
	}
	public void cleanup() {}
}
