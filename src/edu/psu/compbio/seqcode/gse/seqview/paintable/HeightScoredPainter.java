package edu.psu.compbio.seqcode.gse.seqview.paintable;

import java.awt.*;
import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.general.ScoredRegion;
import edu.psu.compbio.seqcode.gse.seqview.model.RegionExpanderModel;
import edu.psu.compbio.seqcode.gse.utils.*;

// thin painter that draws ScoredRegions.  It draws them as rectangles and uses
// a shade of gray that's proportional to the score.

public class HeightScoredPainter extends RegionPaintable {

    double initialMaxScore;
    private RegionExpanderModel<ScoredRegion> model;
    private HeightScoredProperties props;

    public HeightScoredPainter(RegionExpanderModel<ScoredRegion> model, double maxScore) {
        super();
        this.model = model;
        model.addEventListener(this);
        initialMaxScore = maxScore;
        props = new HeightScoredProperties();
    }

    public HeightScoredProperties getProperties() {
        return props;
    }

    public void cleanup() { 
        super.cleanup();
        model.removeEventListener(this);
    }

    public void removeEventListener(Listener<EventObject> l) {
        super.removeEventListener(l);
        if (!hasListeners()) {
            model.removeEventListener(this);
        }
    }

    public synchronized void eventRegistered(EventObject e) {
        if (e.getSource() == model &&
            model.isReady()) {
            setCanPaint(true);
            setWantsPaint(true);
            notifyListeners();
        }
    }    

    public int getMaxVertSpace() { 
        return 40;
    }
    public int getMinVertSpace() { 
        return 40;
    }
    
    public void paintItem(Graphics2D g, 
                          int x1, int y1, 
                          int x2, int y2) {
        if (!canPaint()) {
            return;
        }
        double maxScore = props.MaximumValue;
        Iterator<ScoredRegion> regions = model.getResults();
        if (regions == null) {
            System.err.println("HeightScoredPainter got back NULL regions");
            return;
        }

        g.setColor(Color.gray);
        g.drawRect(x1, y1, x2-x1-1, y2-y1-1);
        g.setColor(Color.black);
        while (regions.hasNext()) {
            ScoredRegion r = regions.next();
            int minx = getXPos(r.getStart(),
                               getRegion().getStart(),
                               getRegion().getEnd(),
                               x1,
                               x2);
            int maxx = getXPos(r.getEnd(),
                               getRegion().getStart(),
                               getRegion().getEnd(),
                               x1,
                               x2);
            if (maxx == minx) {maxx++;}
            float percent = (float)(r.getScore() / maxScore);
            if (percent > 1) {
                g.setColor(Color.red);
                percent = 1;
            } else {
                g.setColor(Color.black);
            }
            int height = (int)((y2 - y1) * percent);
            g.fillRect(minx,y2 - height,maxx-minx,height);
        }
        g.setColor(new Color((float)0.5,(float)0.5,(float)0.5,(float)1.0));
        g.drawString(getLabel(),x1,y2);
    }
}
    
