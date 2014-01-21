package edu.psu.compbio.seqcode.gse.ewok.verbs.motifs;

import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.motifs.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.Expander;
import edu.psu.compbio.seqcode.gse.ewok.verbs.Mapper;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.sequence.SequenceUtils;
import edu.psu.compbio.seqcode.gse.utils.stats.StatUtil;

public class WeightMatrixScorer implements Mapper<Region,WeightMatrixScoreProfile> {

    private WeightMatrix matrix;
    private SequenceGenerator seqgen;
    
    public WeightMatrixScorer(WeightMatrix m) {
    	matrix = m;
        seqgen = new SequenceGenerator();
    }
    
    public WeightMatrixScorer(WeightMatrix m, SequenceGenerator sg) {
    	matrix = m;
        seqgen = sg;
    }
    
    public WeightMatrixScoreProfile execute(Region r) { 
        String seq = seqgen.execute(r);
        seq = seq.toUpperCase();
        double[] fscores = null, rscores = null;

        try { 
        	fscores = score(matrix, seq.toCharArray(), '+');
            seq = SequenceUtils.reverseComplement(seq);
            rscores = score(matrix, seq.toCharArray(), '-');

        } catch (ArrayIndexOutOfBoundsException e) { 
            e.printStackTrace(System.err);
        }
       	
        return new WeightMatrixScoreProfile(matrix, fscores, rscores);
    }
    
    public WeightMatrixScoreProfile execute(String seq) { 
    	seq = seq.toUpperCase();
        double[] fscores = null, rscores = null;
    	fscores = score(matrix, seq.toCharArray(), '+');
        seq = SequenceUtils.reverseComplement(seq);
        rscores = score(matrix, seq.toCharArray(), '-');
        return new WeightMatrixScoreProfile(matrix, fscores, rscores);
    }

    public static double[] score(WeightMatrix matrix, char[] sequence, char strand) {
        double[] results = new double[sequence.length];
        /* scan through the sequence */
        int length = matrix.length();
       	for (int i = 0; i < sequence.length; i++) {
       		results[i] = (float)matrix.getMinScore();
       	}
       	if (sequence.length<length)
     		return results;
       	
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            
            if(strand=='-') { 
            	results[sequence.length-length-i] = score;
            } else { 
            	results[i] = score;
            }
        }
        return results;
    }

    /**
     * Return the maximum motif score of the input sequence (both direction)
     * @param matrix
     * @param sequence
     * @return
     */
    public static double getMaxSeqScore(WeightMatrix matrix, String sequence){
    	if (sequence.length()<matrix.length())
    		return matrix.getMinScore();
    	
    	double[] scores = score(matrix, sequence.toCharArray(), '+');
    	Pair<Double, TreeSet<Integer>> max = StatUtil.findMax(scores);
    	double maxScore = max.car();
    	scores = score(matrix, SequenceUtils.reverseComplement(sequence).toCharArray(), '-');
    	max = StatUtil.findMax(scores);
    	maxScore = Math.max(maxScore, max.car());
    	return maxScore;
    }
    
    /**
     * Return the highest scoring sequence in the region
     */
    public String getMaxScoreSequence(Region r, double threshold, int extend){
        int length = matrix.length();
        String seq = seqgen.execute(r.expand(0, length));
        seq = seq.toUpperCase();
        String hit=null;


        char[] sequence = seq.toCharArray();
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            if (score>threshold){
            	int start = i-extend;
            	if (start<0) continue;
            	int end = i+length-1+extend;
            	if (end>sequence.length - length) continue;
            	hit = seq.substring(start, end);
            	threshold = score;
            }
        }
        seq = SequenceUtils.reverseComplement(seq);
        sequence = seq.toCharArray();
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            if (score>threshold){
            	int start = i-extend;
            	if (start<0) continue;
            	int end = i+length-1+extend;
            	if (end>sequence.length - length) continue;
            	hit = seq.substring(start, end);
            	threshold = score;
            }
        }
        
        return hit;
    }
}
