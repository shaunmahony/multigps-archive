package edu.psu.compbio.seqcode.gse.seqview.paintable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.chipchip.ChipChipData;
import edu.psu.compbio.seqcode.gse.datasets.chipchip.GenericExperiment;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.SeqHit;
import edu.psu.compbio.seqcode.gse.datasets.seqdata.WeightedRunningOverlapSum;
import edu.psu.compbio.seqcode.gse.datasets.species.ExonicGene;
import edu.psu.compbio.seqcode.gse.datasets.species.Gene;
import edu.psu.compbio.seqcode.gse.ewok.nouns.*;
import edu.psu.compbio.seqcode.gse.seqview.model.ChipChipDataModel;
import edu.psu.compbio.seqcode.gse.seqview.model.ChipChipScaleModel;
import edu.psu.compbio.seqcode.gse.seqview.model.ChipSeqDataModel;
import edu.psu.compbio.seqcode.gse.seqview.model.ChipSeqScaleModel;
import edu.psu.compbio.seqcode.gse.seqview.model.Model;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.viz.DynamicAttribute;

public class SeqAboveBelowStrandPainter extends SeqPainter {

    private Color color;
    protected Vector<SeqHit> watsonLayoutHits = new Vector<SeqHit>();
    protected Vector<SeqHit> crickLayoutHits = new Vector<SeqHit>();
    protected NonOverlappingLayout<SeqHit> watsonLayout = new NonOverlappingLayout<SeqHit>();
    protected NonOverlappingLayout<SeqHit> crickLayout = new NonOverlappingLayout<SeqHit>();
    private Color plusHitColor = Color.blue;
    private Color minusHitColor = Color.blue;

    public SeqAboveBelowStrandPainter(ChipSeqDataModel model) {
        super(model);
        attrib = DynamicAttribute.getGlobalAttributes();
    }
    
    protected void setLayoutHits() {        
        if (canPaint()) {        	
            if (getProperties().Overlapping) {
                /* don't do anything.  The model can provide us with the RunningOverlapSums */
            } else {
                Iterator<SeqHit> itr = model.getResults();
                watsonLayoutHits = new Vector<SeqHit>();
                crickLayoutHits = new Vector<SeqHit>();
                Region extended = null;
                while (itr.hasNext()) {
                    SeqHit hit = itr.next();
                    if(getProperties().DrawNonUnique || hit.getWeight()>=1.0){
                    	if (hit.getStrand() == '+') {
                    		watsonLayoutHits.add(hit);
                    	} else {
                    		crickLayoutHits.add(hit);
                    	}
                    }
                }
                watsonLayout.setRegions(watsonLayoutHits);
                crickLayout.setRegions(crickLayoutHits);
            }
        }
    }
    
    
    protected void paintOverlapping(Graphics2D g, 
                          int x1, int y1, 
                          int x2, int y2) {
        
        int width = x2 - x1, height = y2 - y1;
        Region r = model.getRegion();
                
        WeightedRunningOverlapSum watsonOverlap = model.getWatsonRunningOverlap();
        WeightedRunningOverlapSum crickOverlap = model.getCrickRunningOverlap();
        double watsonMaxOverlap = watsonOverlap.getMaxOverlap();
        double crickMaxOverlap = crickOverlap.getMaxOverlap();
        
        int baseline = y1 + height/2;
        int availHeight = (int)Math.round(0.475 * height);
        
        g.setColor(Color.black);
        g.drawLine(x1, baseline, x2, baseline);

        double maxobsoverlap = Math.max(watsonMaxOverlap, crickMaxOverlap);
        int propmaxoverlap = ((SeqProperties)getProperties()).MaxReadCount;
        /* maxoverlap is the maximum height of the reads scale
           If the property is negative, use the observed maximum (ie, dynamic scaling).
           Otherwise, use the value from the property and clip peaks
           higher than that value
        */
        double maxoverlap;
        if (propmaxoverlap > 0) {
            maxoverlap = propmaxoverlap;
        } else {
            maxoverlap = maxobsoverlap;
        }
        maxoverlap = Math.max(maxoverlap, 4);
        
        double trackHeight = (double)availHeight / (double)(maxoverlap+1.0);
        int pixTrackHeight = Math.max(1, (int)Math.floor(trackHeight));
        
        g.setColor(plusHitColor);
        Stroke oldStroke = g.getStroke();
        //g.setStroke(new BasicStroke((float)2.0));

        for(int over = 1; over <= Math.min(Math.ceil(watsonMaxOverlap), Math.ceil(maxoverlap)); over++) { 
            Collection<Region> watsonRegs = watsonOverlap.collectRegions(over);
            for(Region or : watsonRegs) { 
                int orx1 = calcX(or.getStart(), r.getStart(), r.getEnd(), x1, x2);
                int orx2 = calcX(or.getEnd(), r.getStart(), r.getEnd(), x1, x2);
                int ory1 = baseline - 1 - (int)Math.floor(trackHeight * (double)over);
                g.drawRect(orx1, ory1, (orx2-orx1), pixTrackHeight);
            }
        }

        g.setColor(minusHitColor);
        for(int over = 1; over <= Math.min(Math.ceil(crickMaxOverlap), Math.ceil(maxoverlap)); over++) { 
            Collection<Region> crickRegs = crickOverlap.collectRegions(over);
            for(Region or : crickRegs) { 
                int orx1 = calcX(or.getStart(), r.getStart(), r.getEnd(), x1, x2);
                int orx2 = calcX(or.getEnd(), r.getStart(), r.getEnd(), x1, x2);
                int ory1 = baseline + 1 - pixTrackHeight + (int)Math.floor(trackHeight * (double)over);
                g.drawRect(orx1, ory1, (orx2-orx1), pixTrackHeight);
            }
        }
        /* draw the scale */
        g.setColor(Color.black);
        g.setFont(attrib.getLargeLabelFont(width,height));
        int step = Math.max(1,(int)Math.round(maxoverlap / 5));
        for (int i = step; i <= Math.ceil(maxoverlap); i += step) {
            int ypos = baseline + 1 - pixTrackHeight + (int)Math.floor(trackHeight * (double)i);
            g.drawString(Integer.toString(i),
                         5,
                         ypos);
            ypos = baseline - 1 - (int)Math.floor(trackHeight * (double)i);
            g.drawString(Integer.toString(i),
                         5,
                         ypos);
        }        
        
        g.setStroke(oldStroke);
    }
    
    
    protected void paintNonOverlapping(Graphics2D g, 
            int x1, int y1, 
            int x2, int y2) {

        int width = x2 - x1;
        int height = y2 - y1;
        
        int baseline = y1 + height/2;
        int availHeight = (int)Math.round(0.475 * height);
        
        g.setColor(Color.black);
        g.drawLine(x1, baseline, x2, baseline);

        /* if the user has asked for a maxcount greater than
           the maximum number of tracks into which NonOverlappingLayout
           would have done the layout, tell NOL to use more tracks
        */
        int propmaxTracks = ((SeqProperties)getProperties()).MaxReadCount;
        if (watsonLayout.getMaxTracks() <= propmaxTracks) {
            watsonLayout.setMaxTracks(propmaxTracks);
        }
        if (crickLayout.getMaxTracks() <= propmaxTracks) {
            crickLayout.setMaxTracks(propmaxTracks);
        }
        int watsonNumTracks = watsonLayout.getNumTracks();
        int crickNumTracks = crickLayout.getNumTracks();
        int maxobsTracks = Math.max(watsonNumTracks, crickNumTracks);
        /* maxoverlap is the maximum height of the reads scale
           If the property is negative, use the observed maximum (ie, dynamic scaling).
           Otherwise, use the value from the property and clip peaks
           higher than that value
        */
        int numTracks;
        if (propmaxTracks > 0) {
            numTracks = propmaxTracks;
        } else {
            numTracks = maxobsTracks;
        }
        numTracks = Math.max(numTracks, 4);
        
        double trackHeight = (double)availHeight / (double)(numTracks+1.0);
        int pixTrackHeight = Math.max(2, (int)Math.floor(trackHeight));
        int halfTrackHeight = Math.max(1, pixTrackHeight/2);
        
        Region region = model.getRegion();
        int rs = region.getStart();
        int re = region.getEnd();
        int rw = re - rs + 1;
        double xScale = (double)width / (double)rw;

        int hitHeight = Math.max(2, (int)Math.floor((double)pixTrackHeight * 0.90));
        int halfHitHeight = hitHeight / 2;
        
        g.setColor(plusHitColor);
        Stroke oldStroke = g.getStroke();
        //g.setStroke(new BasicStroke((float)2.0));

        for(SeqHit watsonHit : watsonLayoutHits) {
            int track = 0;
            if(getProperties().DrawNonUnique || watsonHit.getWeight()>=1.0){
	            if(!watsonLayout.hasTrack(watsonHit)) { 
	                System.err.println("No track assigned to hit: " + watsonHit.getLocationString());
	            } 
	            else { 
	                track = Math.min(numTracks,watsonLayout.getTrack(watsonHit));
	            }
	
	            int hy1 = baseline - (pixTrackHeight * (track + 1));
	            int hy2 = hy1 + pixTrackHeight;
	            int hmy = hy1 + halfTrackHeight;
	                        
	            int htop = hmy - halfHitHeight;
	            int hbottom = hmy + halfHitHeight;
	            
	            int hitStart = watsonHit.getStart();
	            int hitEnd = watsonHit.getEnd();
	            
	            int hx1 = xcoord(hitStart, x1, rs, xScale);
	            int hx2 = xcoord(hitEnd, x1, rs, xScale);
	            int hleft = Math.max(x1, hx1);
	            int hright = Math.min(x2, hx2);
	
	            int rectwidth = hright - hleft + 1;
	
	            Color c = new Color(plusHitColor.getRed(), plusHitColor.getGreen(), plusHitColor.getBlue(), (int)(watsonHit.getWeight()*255));
	            g.setColor(c);
	            if (watsonHit.getWeight() == 1) {
	              
	            }
	            else {              
	              //g.setColor(Color.cyan);
	            }
	            g.fillRect(hleft, htop, rectwidth, hbottom - htop);
            }
        }
        
        
        g.setColor(minusHitColor);        
        for(SeqHit crickHit : crickLayoutHits) {
        	if(getProperties().DrawNonUnique || crickHit.getWeight()>=1.0){
	        	int track = 0;
	            
	            if(!crickLayout.hasTrack(crickHit)) { 
	                System.err.println("No track assigned to hit: " + crickHit.getLocationString());
	            } 
	            else { 
	                track = Math.min(numTracks,crickLayout.getTrack(crickHit));
	            }
	
	            int hy1 = baseline + (pixTrackHeight * track);
	            int hy2 = hy1 + pixTrackHeight;
	            int hmy = hy1 + halfTrackHeight;           
	            
	            int htop = hmy - halfHitHeight;
	            int hbottom = hmy + halfHitHeight;
	            
	            int hitStart = crickHit.getStart();
	            int hitEnd = crickHit.getEnd();
	            
	            int hx1 = xcoord(hitStart, x1, rs, xScale);
	            int hx2 = xcoord(hitEnd, x1, rs, xScale);
	            int hleft = Math.max(x1, hx1);
	            int hright = Math.min(x2, hx2);
	
	            int rectwidth = hright - hleft + 1;
	
	            Color c = new Color(plusHitColor.getRed(), plusHitColor.getGreen(), plusHitColor.getBlue(), (int)(crickHit.getWeight()*255));
	            g.setColor(c);
	            if (crickHit.getWeight() == 1) {
	              
	            }
	            else {              
	              //g.setColor(Color.pink);
	            }
	            g.fillRect(hleft, htop, rectwidth, hbottom - htop);
        	}
        }
        
        /* draw the scale */
        g.setColor(Color.black);
        g.setFont(attrib.getLargeLabelFont(width,height));
        int step = Math.max(1,numTracks / 5);
        for (int i = step; i <= numTracks; i += step) {
            int ypos = baseline - (pixTrackHeight * (i + 1));
            g.drawString(Integer.toString(i),
                         5,
                         ypos);
            ypos = baseline + (pixTrackHeight * i);
            g.drawString(Integer.toString(i),
                         5,
                         ypos);
        }

        g.setStroke(oldStroke);
    }
}
