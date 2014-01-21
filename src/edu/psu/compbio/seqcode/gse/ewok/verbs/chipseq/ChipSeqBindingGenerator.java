package edu.psu.compbio.seqcode.gse.ewok.verbs.chipseq;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import edu.psu.compbio.seqcode.gse.datasets.binding.BindingEvent;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.SeqHit;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.RunningOverlapSum;
import edu.psu.compbio.seqcode.gse.ewok.verbs.Expander;
import edu.psu.compbio.seqcode.gse.utils.Closeable;

public class ChipSeqBindingGenerator 
	implements Expander<Region,BindingEvent>, Closeable {
	
	private SeqExpander expander;
	private int readExtension, threshold;
	private String name;
	
	public ChipSeqBindingGenerator(SeqExpander e, int ext, int thresh, String n) { 
		expander = e;
		readExtension = ext;
		threshold = thresh;
		name = n;
	}
	
	public boolean isClosed() { 
		return expander == null;
	}
	
	public void close() { 
		expander.close();
		expander = null;
	}

	public Iterator<BindingEvent> execute(Region r) {
		int ws = Math.max(0, r.getStart() - readExtension);
		int we = r.getEnd() + readExtension;
		Region w = new Region(r.getGenome(), r.getChrom(), ws, we);
		
		Iterator<SeqHit> hits = expander.execute(w);
		RunningOverlapSum summer = new RunningOverlapSum(r.getGenome(), r.getChrom());
		while(hits.hasNext()) { 
			SeqHit hit = hits.next();
			int ehs = hit.getStrand()=='+' ?
					hit.getStart() : 
					hit.getStart()-readExtension ;
			int ehe = hit.getStrand() == '+' ? 
					hit.getEnd() + readExtension : 
					hit.getEnd();
			Region ehit = new Region(hit.getGenome(), hit.getChrom(), ehs, ehe);
			summer.addRegion(ehit);
		}
		
		Collection<Region> threshs = summer.collectRegions(threshold);
		LinkedList<BindingEvent> evts = new LinkedList<BindingEvent>();
		
		for(Region thresh : threshs) {
            int maxOverlap = summer.getMaxOverlap(thresh.getStart(), thresh.getEnd());
			BindingEvent evt = 
				new BindingEvent(thresh, (double)maxOverlap, 
                        (double)threshold , name);
			evts.add(evt);
		}
		
		return evts.iterator();
	}

}
