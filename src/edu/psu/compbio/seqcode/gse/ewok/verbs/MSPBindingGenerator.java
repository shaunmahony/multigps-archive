package edu.psu.compbio.seqcode.gse.ewok.verbs;

import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.*;
import edu.psu.compbio.seqcode.gse.datasets.binding.BindingEvent;
import edu.psu.compbio.seqcode.gse.datasets.chipchip.ChipChipMSP;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.ewok.*;
import edu.psu.compbio.seqcode.gse.ewok.nouns.*;
import edu.psu.compbio.seqcode.gse.utils.*;

public class MSPBindingGenerator implements Expander<Region,BindingEvent> {

    private ChipChipMSP data;
    private double sizethresh, pvalthresh;
    private boolean MOR, peaks;

    public MSPBindingGenerator (ChipChipMSP data, double pvalthresh, 
                                double sizethresh, boolean MOR, boolean peaks) {
        this.data = data;
        this.sizethresh = sizethresh;
        this.pvalthresh = pvalthresh;
        this.MOR = MOR;
        this.peaks = peaks;
    }
    public void setPeaks(boolean peaks) {
        this.peaks = peaks;
    }

    public Iterator <BindingEvent> execute (Region r) {
        ArrayList results = new ArrayList<BindingEvent>();
        try {
            data.window(r.getChrom(),
                        r.getStart(),
                        r.getEnd());
            for (int i = 0; i < data.getCount(); i++) {
                if ((data.getPval3(i) <= pvalthresh) &&
                    ((MOR && (data.getMedianOfRatios(i) >= sizethresh)) ||
                     (!MOR && (data.getRatio(i) >= sizethresh)))) {
                    if (!peaks || 
                        (((i == 0) || (data.getPval3(i) <= data.getPval3(i-1))) &&
                         ((i == data.getCount() - 1) || (data.getPval(i) <= data.getPval3(i+1))))) {
                        results.add(new BindingEvent(r.getGenome(),
                                                     r.getChrom(),
                                                     data.getPos(i)+30,
                                                     data.getPos(i)+30,
                                                     MOR ? data.getMedianOfRatios(i) : data.getRatio(i),
                                                     data.getPval3(i),
                                                     "MSP"));
                    }                    
                } 
            }
        } catch (NotFoundException ex) {
            throw new RuntimeException("Couldn't window()" +ex,ex);
        }
        return results.iterator();        
    }
}
