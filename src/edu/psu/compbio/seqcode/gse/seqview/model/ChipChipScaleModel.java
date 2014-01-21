package edu.psu.compbio.seqcode.gse.seqview.model;

import java.util.EventObject;
import java.util.ArrayList;

import edu.psu.compbio.seqcode.gse.datasets.chipchip.GenericExperiment;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.utils.*;

public class ChipChipScaleModel extends SeqViewModel implements RegionModel, Listener<EventObject> {
    
    private ArrayList<ChipChipDataModel> models;
    private ArrayList<ChipChipDifferenceDataModel> diffmodels;
    private Region region;    
    private double maxval;

    public ChipChipScaleModel() {
        models = new ArrayList<ChipChipDataModel>();
        diffmodels = new ArrayList<ChipChipDifferenceDataModel>();
        maxval = -1;
    }
    
    public void addModel(ChipChipDataModel m) {
        models.add(m);
        m.addEventListener(this);
    }
    
    public void addModel(ChipChipDifferenceDataModel m) { 
        diffmodels.add(m);
        m.addEventListener(this);
    }
    
    
    public boolean isReady() {
        boolean isready = true;
        for (int i = 0; i < models.size(); i++) {
            isready = isready && models.get(i).isReady();
        }
        for(int i = 0; i < diffmodels.size(); i++) { 
            isready = isready && diffmodels.get(i).isReady();
        }
        return isready;
    }
    
    public void setRegion (Region r) {
        maxval = -1;
        region = r;
    }
    public void resetRegion (Region r) {
        maxval = -1;
        region = r;
    }
    public Region getRegion() {return region;}
    public boolean connectionOpen(){return true;}
    public void reconnect(){}
    
    public double getMaxVal() {
        if (region == null) {
            System.err.println("Null region in CCSM\n");
            return 2;
        }
        if (maxval < 0) {
            if (isReady()) {
                for (int i = 0; i < models.size(); i++) {
                    GenericExperiment e = models.get(i).getGenericExperiment();
                    try {
                        double m = e.getMax(region.getChrom(),
                                            region.getStart(),
                                            region.getEnd());
                        if (m > maxval) {
                            maxval = m;
                        }
                    } catch (NotFoundException ex) {
                        // just eat it.  Nothing else is going to work either.
                    }
                }
                
                for(int i = 0; i < diffmodels.size(); i++) { 
                    double m = diffmodels.get(i).getMax();
                    if (m > maxval) {
                        maxval = m;
                    }
                }
            }
        }        
        return maxval;
    }
    public void eventRegistered(EventObject o) {
        notifyListeners();
    }

}
