package edu.psu.compbio.seqcode.gse.datasets.function;

import java.util.Comparator;

import edu.psu.compbio.seqcode.gse.utils.Enrichment;

public class EnrichmentPvalueComparator implements Comparator<Enrichment> {

    public boolean equals(Object o) {
        return o instanceof EnrichmentPvalueComparator;
    }
    public int compare(Enrichment a, Enrichment b) {
        double diff = b.getLogPValue() - a.getLogPValue();
        if (diff < 0) {
            return -1;
        } else if (diff > 0) {
            return 1;
        } else {
            return 0;
        }

    }
}