package edu.psu.compbio.seqcode.gse.seqview.paintable;

import java.io.File;
import java.awt.*;
import java.util.*;


import edu.psu.compbio.seqcode.gse.projects.readdb.PairedHit;
import edu.psu.compbio.seqcode.gse.seqview.model.SeqPairedEndModel;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.viz.DynamicAttribute;

public class PairedEndPainter extends RegionPaintable {

	private SeqPairedEndModel model;
	private PairedEndProperties props;
	private DynamicAttribute attrib;

	public PairedEndPainter (SeqPairedEndModel model) {
		super();
		this.model = model;
		props = new PairedEndProperties();
		model.addEventListener(this);
		attrib = DynamicAttribute.getGlobalAttributes();
	}
	public PairedEndProperties getProperties() {return props;}
	public void setProperties(PairedEndProperties p) {props = p;}
	public void savePropsInDir(File dir) {
		super.savePropsInDir(dir);
		saveModelPropsInDir(dir,model);
	}
	public void loadPropsInDir(File dir) {
		super.loadPropsInDir(dir);
		loadModelPropsInDir(dir,model);
	}    
	public void cleanup() { 
		super.cleanup();
		model.removeEventListener(this);
	}
	public synchronized void eventRegistered(EventObject e) {        
		if (e.getSource() == model && model.isReady()) {
			setCanPaint(true);
			setWantsPaint(true);
			notifyListeners();
		}
	}
	public void removeEventListener(Listener<EventObject> l) {
		super.removeEventListener(l);
		if (!hasListeners()) {
			model.removeEventListener(this);
		}
	}
	public void paintItem(Graphics2D g, 
			int x1, int y1, 
			int x2, int y2) {
		if (!canPaint()) {
			return;
		}
		if(!model.isReady()) { return; }

		int width = x2 - x1;
		int height = Math.max(y2 - y1,1);
		int regionStart = model.getRegion().getStart();
		int regionEnd = model.getRegion().getEnd();
		int linewidth = Math.max(getProperties().LineWidth,1);
		Stroke oldStroke = g.getStroke();
		g.setStroke(new BasicStroke((float)linewidth));        
		java.util.List<PairedHit> hits = model.getResults();
		if (getProperties().DrawTrackLabel) {
			g.setFont(attrib.getLargeLabelFont(width,height));
			g.setColor(Color.BLACK);
			g.drawString("Paired " +getLabel(),x1 + g.getFont().getSize()*2,y1 + g.getFont().getSize());
		}
		//        int alphastep = Math.min(255, Math.max(255 / (height / (hits.size() * linewidth)), 4));
		int alphastep = 255;
		int h = height;
		Color plusplus = new Color(0, 0, 255, alphastep / 2);
		Color minusminus = new Color(255,0,0,alphastep/2);
		Color plusminus = new Color(100,0,255,alphastep/2);
		Color minusplus = new Color(255,0,100,alphastep/2);

		for (int i = 0; i < hits.size(); i++) {
			PairedHit hit = hits.get(i);
			
			int leftx1 = getXPos(hit.leftPos, regionStart, regionEnd, x1, x2);
			int leftx2 = getXPos(hit.leftStrand ? hit.leftPos + hit.leftLength : hit.leftPos - hit.leftLength,
					regionStart, regionEnd, x1, x2);
			if (leftx1==x1 || leftx2==x1) {
				System.err.println(hit);
			}
			if (leftx1 > leftx2) {
				int x = leftx2;
				leftx2 = leftx1;
				leftx1 = x;
			}
			int rightx1 = getXPos(hit.rightPos, regionStart, regionEnd, x1, x2);
			int rightx2 = getXPos(hit.rightStrand ? hit.rightPos + hit.rightLength : hit.rightPos - hit.rightLength,
					regionStart, regionEnd, x1, x2);            
			if (rightx1 > rightx2) {
				int x = rightx2;
				rightx2 = rightx1;
				rightx1 = x;
			}
			//Shaun thinks there was a problem with the way this was coded. 
			//For the case when "left" should mean "first read off the machine", the color of the edge should not depend on leftx2<rightx1.
			//For the case when "left" means lower coordinate, it should depend on leftx2<rightx1, but this is enforced by the leftAlwaysLesser option and it's call to the flip method.
			g.setColor(hit.leftStrand ? (hit.rightStrand ? plusplus : plusminus) : (hit.rightStrand ? minusplus : minusminus));

			if (leftx2 < rightx1) {
				//g.setColor(hit.leftStrand ? (hit.rightStrand ? plusplus : plusminus) : (hit.rightStrand ? minusplus : minusminus));
				g.drawLine(leftx2, y1+h, rightx1, y1+h);
			} else {
				//g.setColor(hit.rightStrand ? (hit.leftStrand ? plusplus : plusminus) : (hit.leftStrand ? minusplus : minusminus));
				g.drawLine(rightx2, y1+h, leftx1, y1+h);
			}

			h -= (linewidth == 1 ? 2 : linewidth);
			if (h < 0) {
				h = height;
			}            
		}
		if (getProperties().DrawOtherChromHits) {
			int onlyx1, onlyx2;
			java.util.List<PairedHit> otherc = model.getOtherChromResults();
			int thischrom = getRegion().getGenome().getChromID(getRegion().getChrom());
			g.setColor(Color.GRAY);
			for (int i = 0; i < otherc.size(); i++) {
				PairedHit hit = otherc.get(i);
				if (hit.leftChrom == thischrom) {
					onlyx1 = getXPos(hit.leftPos, regionStart, regionEnd, x1,x2);
					onlyx2 = getXPos(hit.leftStrand ? hit.leftPos + hit.leftLength :
						hit.leftPos - hit.leftLength, 
						regionStart, regionEnd, x1,x2);
				} else {
					onlyx1 = getXPos(hit.rightPos, regionStart, regionEnd, x1,x2);
					onlyx2 = getXPos(hit.rightStrand ? hit.rightPos + hit.rightLength :
						hit.rightPos - hit.rightLength, regionStart, regionEnd, x1,x2);
				}
				if (onlyx2 > onlyx1) {
					int t = onlyx2;
					onlyx2 = onlyx1;
					onlyx1 = t;
				}
				g.drawLine(onlyx1,y1+h,onlyx2,y1+h);
				h -= (linewidth == 1 ? 2 : linewidth);
				if (h < 0) {
					h = height;
				}            
			}
		}

		g.setStroke(oldStroke);
	}

}
