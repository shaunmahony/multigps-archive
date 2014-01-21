package edu.psu.compbio.seqcode.gse.seqview.model;

import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.general.Point;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.general.StrandedPoint;
import edu.psu.compbio.seqcode.gse.utils.Pair;

public class InteractionAnalysisModel extends SeqViewModel implements RegionModel,
		Runnable {
	
	private Region region;
	private boolean newinput;
	private Map<Point,Float> events;
	private Map<Pair<Point,Point>,Float> interactions;
	private SortedMap<Point,Float> allevents;
	private SortedMap<Pair<Point,Point>,Float> allinteractions;
	private InteractionAnalysisProperties props;
	
	public InteractionAnalysisModel(SortedMap<Point,Float> allevents, SortedMap<Pair<Point,Point>,Float> allinteractions) {
		this.allinteractions = allinteractions;
		this.allevents = allevents;
		props = new InteractionAnalysisProperties();
		region = null;
		newinput = false;
	}


	public boolean isReady() {
		return !newinput;
	}

	public synchronized void run() {
		while(keepRunning()) {
			try {
				if (!newinput) {
					wait();
				}
			} catch (InterruptedException ex) {

			}
			if (newinput) {
				events = allevents.subMap(region.startPoint(), region.endPoint());
				Point minPoint = new Point(region.getGenome(), "", 0);
				Point maxPoint = new Point(region.getGenome(), "Z", 0);
				Point startPoint = new Point(region.getGenome(), region.getChrom(), region.getStart());
				Point endPoint = new Point(region.getGenome(), region.getChrom(), region.getEnd());
				interactions = allinteractions.subMap(new Pair<Point,Point>(startPoint,minPoint), new Pair<Point,Point>(endPoint,maxPoint));
			}
			newinput = false;
			notifyListeners();
		}
	}

	public void setRegion(Region r) {
		if (newinput == false) {
			if (!r.equals(region)) {
				region = r;
				newinput = true;
			} else {
				notifyListeners();
			}
		}
	}
	public void resetRegion(Region r) {
		if (newinput == false) {
			region = r;
			newinput = true;
		}
	}

	public Region getRegion() {return region;}
	public boolean connectionOpen(){return true;}
	public void reconnect(){}
	
	public Map<Pair<Point, Point>, Float> getInteractions() {
		return interactions;
	}

	public Map<Point, Float> getEvents() {
		return events;
	}
	
	public InteractionAnalysisProperties getProperties() {
		return props;
	}

}
