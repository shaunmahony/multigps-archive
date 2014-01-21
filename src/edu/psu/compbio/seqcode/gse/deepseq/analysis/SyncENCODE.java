package edu.psu.compbio.seqcode.gse.deepseq.analysis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class SyncENCODE {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		HashMap<String, String> expRep2Line = new HashMap<String, String>();	
		HashMap<String, ArrayList<String>> expRep2date = new HashMap<String, ArrayList<String>>();	
        TreeMap<String, ArrayList<String>>expt2rep = new TreeMap<String, ArrayList<String>>();
        int countDownloaded = 0;
		try {	
			BufferedReader bin = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));
	        String line;
	        while((line = bin.readLine()) != null) { 
	            line = line.trim();
	            String[] f = line.split("\t");
	            String expt = f[0];
	            String date = f[1];
	            String lab = f[2];
	            String pi = f[3];
	            String type = f[4];
	            String cond = f[5];
	            String tf = f[6];
	            String rep = f[7];
//	            String rep = f[7].replaceAll("[a-z]", "");
	            String cell = f[8];
	            String key = date+" "+pi+" "+cond+" "+tf+" "+cell+" "+rep;
	            if (expRep2Line.containsKey(key))
	            	System.out.println(key+"\t"+f[7]);
	            expRep2Line.put(key, line);
	            String key2 = date+" "+pi+" "+cond+" "+tf+" "+cell;
	            if (!expt2rep.containsKey(key2)){
	            	expt2rep.put(key2, new ArrayList<String>());
	        	}
	            expt2rep.get(key2).add(rep);
	            String key3 = pi+" "+cond+" "+tf+" "+cell;
	            if (!expRep2date.containsKey(key3)){
	            	expRep2date.put(key3, new ArrayList<String>());
	        	}
	            expRep2date.get(key3).add(date);
	            countDownloaded++;
	        }	
	        if (bin != null) {
	            bin.close();
	        }	        
        } catch (IOException e) {
        	System.err.println("Error when processing "+args[0]);
            e.printStackTrace(System.err);
        }
        System.out.println("Downloaded\t"+countDownloaded+"\t"+expRep2Line.keySet().size());
        

		HashMap<String, String> newExptRep = new HashMap<String, String>();		
        int countSame = 0;
        StringBuilder sbSame = new StringBuilder();
        int countMissRep = 0;
        StringBuilder sbMissRep = new StringBuilder();
        int countNewExpt = 0;
        StringBuilder sbNewExpt = new StringBuilder();
        int allCount = 0;
        int countDiffDate = 0;
        try {	
			BufferedReader bin = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
	        String line;
	        while((line = bin.readLine()) != null) { 
	            line = line.trim();
	            String[] f = line.split("\t");
	            String expt = f[0];
	            String date = f[1];
	            String lab = f[2];
	            String pi = f[3];
	            String type = f[4];
	            String cond = f[5];
	            String tf = f[6];
	            String rep = f[7];
	            //	            String rep = f[7].replaceAll("[a-z]", "");
	            String cell = f[8];
	            String key = date+" "+pi+" "+cond+" "+tf+" "+cell+" "+rep;
	            String key2 = date+" "+pi+" "+cond+" "+tf+" "+cell;
	            String key3 = pi+" "+cond+" "+tf+" "+cell;
	            newExptRep.put(key, line);
	            if (expt2rep.containsKey(key2) && expRep2Line.containsKey(key)){		// same expt, same rep (ignoring a, b .. suffix)
	            	countSame++;
	            	sbSame.append(key).append("\t").append(expt).append("\t").append(expRep2Line.get(key)).append("\n");
	            }
	            else if (expt2rep.containsKey(key2) && !expRep2Line.containsKey(key)){	// has expt, but replicates do not match up
	            	countMissRep++;
	            	sbMissRep.append(key).append("\t").append(expt).append("\t");
	            	for (String r:expt2rep.get(key2))
	            		sbMissRep.append("\n").append(expRep2Line.get(key2+" "+r));
	            	sbMissRep.append("\n");
	            }
	            else if (!expt2rep.containsKey(key2)){									// new expt
	            	countNewExpt++;
	            	sbNewExpt.append(key).append("\n");
	            	if (expRep2date.containsKey(key3)){		// new expt b/c diff date
	            		System.out.print(key3+'\t'+date);
	            		for (String d:expRep2date.get(key3))
	            			System.out.print("\t"+d);
	            		System.out.println("\n"+line);
	            		countDiffDate++;
	            	}
	            }
	            allCount++;
	        }	
	        System.out.println("To be downloaded\t"+allCount+"\t"+newExptRep.keySet().size());
	        System.out.println("******************************");
	        System.out.println("Same Expt\t"+countSame);
//	        System.out.println(sbSame.toString());
	        System.out.println("******************************");
	        System.out.println("Missing rep\t"+countMissRep);
//	        System.out.println(sbMissRep.toString());
	        System.out.println("******************************");
	        System.out.println("New Expt\t"+countNewExpt);
//	        System.out.println(sbNewExpt.toString());
	        System.out.println("New Expt b/c diff date "+countDiffDate);
	        if (bin != null) {
	            bin.close();
	        }	        
        } catch (IOException e) {
        	System.err.println("Error when processing "+args[1]);
            e.printStackTrace(System.err);
        }
	}

}
