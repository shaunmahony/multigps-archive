package edu.psu.compbio.seqcode.gse.projects.readdb;

import java.util.Comparator;

public class PairedHitRightComparator implements Comparator<PairedHit> {

    public boolean equals(Object o) {
        return (o instanceof PairedHitRightComparator);
    }

    public int compare(PairedHit a, PairedHit b) {
        int result;
        if (a.rightChrom == b.rightChrom) {
            result = a.rightPos - b.rightPos;
        } else {
            result = a.rightChrom - b.rightChrom;
        }
        if (result == 0) {
            if (a.leftChrom == b.leftChrom) {
                result = a.leftPos - b.leftPos;
            } else {
            result = a.leftChrom - b.leftChrom;
            }            
        }
        if (result == 0) {
            result = a.rightLength - b.rightLength;
        }
        if (result == 0) {
            result = a.leftLength - b.leftLength;
        }
        return result;
    }
}