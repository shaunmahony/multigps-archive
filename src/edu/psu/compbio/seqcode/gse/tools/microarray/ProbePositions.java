package edu.psu.compbio.seqcode.gse.tools.microarray;

import edu.psu.compbio.seqcode.gse.datasets.chipchip.*;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.ewok.*;
import edu.psu.compbio.seqcode.gse.ewok.nouns.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.*;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.utils.database.*;

import java.sql.*;
import java.util.*;

/** prints the probe positions for an array design and
    the number of occurrences of the probe in the genome and
    the id of the probe

    java edu.psu.compbio.seqcode.gse.tools.microarray.ProbePositions --species "$SC;SGDv1" --design "Sc 244k"

    output format is:

    1:234-304<tab>3<tab>10010
*/

public class ProbePositions {

    public static void main (String args[]) throws Exception {
        String designName = Args.parseString(args,"design",null);
        Genome genome = Args.parseGenome(args).cdr();
		java.sql.Connection c = 
			DatabaseFactory.getConnection("chipchip");
        Statement s = c.createStatement();
        ChipChipMetadataLoader loader = new ChipChipMetadataLoader();
        ArrayDesign design = loader.loadArrayDesign(designName, genome);
        StringBuffer chromString = new StringBuffer();
        for (int i : genome.getRevChromIDMap().keySet()) {
            chromString.append(i + ",");
        }
        chromString.deleteCharAt(chromString.length() - 1);

        Map<Integer,Integer> counts = new HashMap<Integer,Integer>();
        ResultSet rs = s.executeQuery("select pl.id, count(*) from probelocation pl where pl.id in (select id from probedesign where " +
                                      " arraydesign = " + design.getDBID() + ") and pl.chromosome in (" + chromString + ") group by pl.id");
        while (rs.next()) {
            counts.put(rs.getInt(1), rs.getInt(2));
        }
        rs.close();
        
        rs = s.executeQuery("select pl.chromosome, pl.startpos, pl.stoppos, pl.id from probelocation pl, probedesign pd " +
                            " where pl.id = pd.id and pd.arraydesign =" + design.getDBID() + 
                            " and pl.chromosome in (" + chromString + ")");
        Map<Integer,String> chromID = genome.getRevChromIDMap();
        while (rs.next()) {
            System.out.println(String.format("%s:%d-%d\t%d\t%d",
                                             chromID.get(rs.getInt(1)),
                                             rs.getInt(2),
                                             rs.getInt(3),
                                             counts.get(rs.getInt(4)),
                                             rs.getInt(4)));
        }
        rs.close();
        s.close();
        
    }
}