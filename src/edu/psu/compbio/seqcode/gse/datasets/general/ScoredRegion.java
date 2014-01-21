package edu.psu.compbio.seqcode.gse.datasets.general;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.utils.Saveable;

public class ScoredRegion extends Region implements Saveable, Scored {
    private double score;
    
    public ScoredRegion(ScoredRegion copied) { 
        super(copied);
        score = copied.score;
    }
    
    public ScoredRegion (Genome g, String c, int start, int end, double score) {
        super(g,c,start,end);
        this.score = score;
    }
    
    public ScoredRegion(Genome g, DataInputStream dis) throws IOException { 
        super(g,dis);
        score = dis.readDouble();
    }
    
    public void save(DataOutputStream dos) throws IOException { 
        super.save(dos);
        dos.writeDouble(score);
    }

    public double getScore() {return score;}

    public String toString() {
        return String.format("%s (%f)",
                             regionString(),
                             score);
    }

    public boolean equals(Object o) {
        if (o instanceof ScoredRegion) {
            ScoredRegion other = (ScoredRegion)o;
            return super.equals(other) && other.getScore() == getScore();
        } else {
            return false;
        }
    }
}

