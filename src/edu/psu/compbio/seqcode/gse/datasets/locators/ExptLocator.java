/**
 * 
 */
package edu.psu.compbio.seqcode.gse.datasets.locators;

import java.io.*;
import java.util.*;

import edu.psu.compbio.seqcode.gse.datasets.*;
import edu.psu.compbio.seqcode.gse.datasets.chipchip.GenericExperiment;
import edu.psu.compbio.seqcode.gse.datasets.chipchip.NameVersion;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.utils.Factory;
import edu.psu.compbio.seqcode.gse.utils.preferences.*;

/**
 * @author Timothy Danford
 */
public interface ExptLocator 
    extends Preferences<GenericExperiment> {
    
    public static final String dbRole = "chipchip";
    
    public LinkedList<String> getTreeAddr();
    public NameVersion getNameVersion();
    
    public static class LoadingFactory implements Factory<ExptLocator> { 
        private DataInputStream dis;
        private Genome genome;
        public LoadingFactory(Genome g, DataInputStream d) { genome = g; dis = d; }
        public ExptLocator createObject() {
            try { 
                int type = dis.readInt();
                switch(type) { 
                case 0: return new ChipChipLocator(genome, dis);
                case 1: return new BayesLocator(genome, dis);
                case 2: return new MLELocator(genome, dis);
                case 3: return new MSPLocator(genome, dis);
                }
            } catch(IOException ie) { 
                ie.printStackTrace(System.err);
                throw new RuntimeException(ie);
            }
            return null;
        }
    }
}
