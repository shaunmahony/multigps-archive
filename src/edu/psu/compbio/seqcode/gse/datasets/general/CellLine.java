package edu.psu.compbio.seqcode.gse.datasets.general;

import java.util.*;
import java.sql.*;


import edu.psu.compbio.seqcode.gse.datasets.locators.ChipChipLocator;
import edu.psu.compbio.seqcode.gse.datasets.locators.ExptLocator;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;

/**
 * @author tdanford
 *
 * Represents an entry in the core.cellline table.  This is used as metadata
 * for sequencing experiments.  The name might be something
 * general (and not very useful) like 'S288C' or a cell line name or strain number.
 */
public class CellLine implements Comparable<CellLine> {
	
	private int dbid;
	private String name;
    
    /**
     * Creates a new <code>CellLine</code> object from
     * a ResultSet that is set to a row with two
     * values:
     *  an integer that is the database id
     *  a string that is the name
     */
    public CellLine(ResultSet rs) throws SQLException { 
        dbid = rs.getInt(1);
        name = rs.getString(2);
    }
	
	public String getName() { return name; }
	public int getDBID() { return dbid; }
    
    public String toString() { return name + " (#" + dbid + ")"; }
	
	public int hashCode() { 
		int code = 17;
		code += dbid; code *= 37;
		return code;
	}
	
	public boolean equals(Object o) { 
		if(!(o instanceof CellLine)) { return false; }
		CellLine c = (CellLine)o;
		if(dbid != c.dbid) { return false; }
		return true;
	}
    
    public int compareTo(CellLine c) { return name.compareTo(c.name); }
    
    public Collection<ExptLocator> loadLocators(java.sql.Connection cxn, Genome g) throws SQLException {
        LinkedList<ExptLocator> locs = new LinkedList<ExptLocator>();
        PreparedStatement ps = prepareLoadExperimentsByGenome(cxn);
        
        ps.setInt(1, g.getDBID());
        ps.setInt(2, dbid); ps.setInt(3, dbid);
        
        ResultSet rs = ps.executeQuery();
        while(rs.next()) { 
            String name = rs.getString(2);
            String version = rs.getString(3);
            ChipChipLocator loc = new ChipChipLocator(g, name, version);
            locs.addLast(loc);
        }
        
        rs.close();
        ps.close();
        return locs;
    }
    
    public static PreparedStatement prepareLoadExperimentsByGenome(java.sql.Connection cxn) throws SQLException { 
        return cxn.prepareStatement("select e.id, e.name, e.version from experiment e, exptToGenome e2g " +
                "where e.id=e2g.experiment and e2g.genome=? and e.active=1 and (e.cellsone=? or e.cellstwo=?)");
    }
    
    
}
