/*
 * Created on April 1, 2009
 *
 */
package edu.psu.compbio.seqcode.gse.utils;

import java.util.*;

import edu.psu.compbio.seqcode.gse.utils.Interval;

/**
 * (Adapted from RunningOverlapSum)
 * 
 * This is a data structure originally designed to analyze the overlapping 
 * reads of a ChIP-Seq experiment, but since adapted to several more uses.  
 * 
 * Its purpose is to track a series of intervals over a region (in this case, 
 * a region identified with a particular chromosome and genome), and to then 
 * be able to answer queries about the number of times that locations within 
 * that region have been "covered" by on or more intervals.  
 * 
 * The method .collectRegions(int) is the most-used interface -- it collects 
 * a series of (disjoint) regions that are each continuously covered by 
 * *at least* 'int' intervals.  
 *  
 * @author Tim &amp; Alex
 */
public class OverlapSum {

    private TreeMap<Integer,Integer> changes;
    
    public OverlapSum() {
        changes = new TreeMap<Integer,Integer>();
    }
    
    public OverlapSum(Collection<Interval> rs) {
        changes = new TreeMap<Integer,Integer>();
    	 if(rs.isEmpty()) { throw new IllegalArgumentException(); }
    	 for(Interval r : rs) { 
    		 addInterval(r.start, r.end);
    	 }
    }
    
    public void clear() { 
        changes.clear();
    }
    
    public void combineWith(OverlapSum s) { 
    	for(Integer pt : s.changes.keySet()) { 
    		if(!changes.containsKey(pt)) { 
    			changes.put(pt, 0);
    		}
    		changes.put(pt, changes.get(pt) + s.changes.get(pt));
    	}
    }

    public void addInterval(int start, int end) {
        if(changes.containsKey(start)) { 
            changes.put(start, changes.get(start) + 1);
        } else { 
            changes.put(start, 1);
        }
        
        /**
         * I'm assuming that intervals, like regions, are inclusive of their
         * end points...
         * This needs to put the changepoint at one past the position where the
         * interval ends to account for intervals being inclusive. -Bob
         */
        if(changes.containsKey(end + 1)) { 
            changes.put(end + 1, changes.get(end + 1) - 1);
        } else {
            changes.put(end + 1, -1);
        }
    }
    
    public void addInterval(Interval intv) { 
    	addInterval(intv.start, intv.end);
    }
    
    public int[][] getChangePoints() { 
        int[][] array = new int[changes.size()][];
        int i = 0;
        for(int change : changes.keySet()) {
            int[] pair = new int[2];
            pair[0] = change;
            pair[1] = changes.get(change);
            array[i++] = pair;
        }
        return array;
    }

    /*
    public void addRegion(Region r) {
        if(!genome.equals(r.getGenome())) { throw new IllegalArgumentException(r.getGenome().toString()); }
        if(!chrom.equals(r.getChrom())) { throw new IllegalArgumentException(r.getChrom()); }
        
        int start = r.getStart();
        int end = r.getEnd();
        if(changes.containsKey(start)) { 
            changes.put(start, changes.get(start) + 1);
        } else { 
            changes.put(start, 1);
        }
        
         // This needs to put the changepoint at one past the position where the
         // region ends to account for regions being inclusive. -Bob
        if(changes.containsKey(end + 1)) { 
            changes.put(end + 1, changes.get(end + 1) - 1);
        } else {
            changes.put(end + 1, -1);
        }
    }
	*/
    
    public int getMaxOverlap() {
        int max = 0;
        int running = 0;
        for(int pc : changes.keySet()) { 
            int delta = changes.get(pc);
            running += delta;
            max = Math.max(max, running);
        }
        return max;
    }
    
    public int getMaxOverlap(int start, int end) { 
        int max = 0;
        int running = 0;
        Map<Integer,Integer> map = changes.headMap(end);
        for(int pc : map.keySet()) { 
            int delta = map.get(pc);
            running += delta;
            if(pc >= start && pc <= end) { 
            	max = Math.max(max, running);
            }
        }
        return max;
    }
    
    public int countOverlapping(int start, int end) { 
    	int count = 0;
    	Map<Integer,Integer> map = changes.headMap(end);
    	int running = 0;
    	for(int pc : map.keySet()) { 
    		int delta = map.get(pc);
    		running += delta;
    		if(pc >= start && delta == -1) { 
    			count += 1;
    		}
    	}
    	count += running;
    	return count;
    }

    public boolean hasOverlap(OverlapSum sum, int thresh) { 
    	Iterator<Integer> chs = 
    		new MergingIterator<Integer>(changes.keySet().iterator(),
    				sum.changes.keySet().iterator());
    	int thisCount = 0, thatCount = 0;
    	while(chs.hasNext()) { 
    		Integer change = chs.next();
    		if(changes.containsKey(change)) { 
    			thisCount += changes.get(change);
    		}
    		if(sum.changes.containsKey(change)) { 
    			thatCount += sum.changes.get(change);
    		}
    		
    		if(thisCount >= thresh && thatCount >= thresh) { 
    			return true;
    		}
    	}
    	return false;
    }
    
    public Collection<Interval> collect(int threshold) {
        if(threshold < 1) { throw new IllegalArgumentException(String.valueOf(threshold)); }
        
        LinkedList<Interval> regions = new LinkedList<Interval>();
        int runningSum = 0;
        
        int rstart = -1, rend = -1;
        
        for(int pc : changes.keySet()) {
            int delta = changes.get(pc);
            if(runningSum < threshold && runningSum + delta >= threshold) { 
                rstart = pc;
            }
            
            if(runningSum >= threshold && runningSum + delta < threshold) { 
                //subtract 1 from the endpoint because regions are inclusive
            	rend = pc - 1;
            	Interval intv = new Interval(rstart, rend);
                regions.addLast(intv);
                rstart = rend = -1;
            }
            
            runningSum += delta;
            if(runningSum < 0) { throw new IllegalStateException(String.valueOf(runningSum)); }
        }
        
        if(runningSum > 0) { throw new IllegalStateException(String.valueOf(runningSum)); }
        return regions;
    }
    
    
    public TreeMap<Integer, Integer> getChangeMap() {
    	return changes;
    }
}


