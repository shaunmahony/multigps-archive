package edu.psu.compbio.seqcode.gse.conservation;

import java.util.*;
import java.io.*;

import edu.psu.compbio.seqcode.gse.datasets.general.Named;
import edu.psu.compbio.seqcode.gse.datasets.general.NamedRegion;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.ewok.nouns.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.utils.Saveable;

public class LocationMappedID implements Saveable {

	private Region location;
	private String id;
	
	public LocationMappedID(String _id, Region r) { 
		id = _id;
		location = r;
	}
	
	public LocationMappedID(NamedRegion nr) { 
		id = nr.getName();
		location = nr;
	}

    /* dangerous constructor.  nr had better implement Named */
	public LocationMappedID(Region nr) { 
		id = ((Named)nr).getName();
		location = nr;
	}
	
	public LocationMappedID(Genome g, DataInputStream dis) throws IOException { 
		id = dis.readUTF();
		String c = dis.readUTF();
		int s = dis.readInt();
		int e = dis.readInt();
		location = new Region(g, c, s, e);
	}
	
	public void save(DataOutputStream dos) throws IOException { 
		dos.writeUTF(id);
		dos.writeUTF(location.getChrom());
		dos.writeInt(location.getStart());
		dos.writeInt(location.getEnd());
	}
	
	public Region getLocation() { return location; }
	public String getID() { return id; }

	public void outputFASTA(PrintStream ps) { outputFASTA("", ps); }
    
    public void outputFASTA(String newID, PrintStream ps) { 
        String name = newID + ";" + id + ";" + location.getLocationString();
        SequenceGenerator seqMapper = new SequenceGenerator();
        String seq = seqMapper.execute(location);
        ps.println(">" + name);
        ps.println(seq);
    }
	
	public int hashCode() { 
		int code = 17;
		code += location.hashCode(); code *= 37;
		code += id.hashCode(); code *= 37;
		return code; 
	}
	
	public boolean equals(Object o) { 
		if(!(o instanceof LocationMappedID)) { return false; }
		LocationMappedID lmi = (LocationMappedID)o;
		if(!location.equals(lmi.location)) { return false; }
		if(!id.equals(lmi.id)) { return false; }
		return true;
	}
	
	public String toString() { 
		return id + " (" + location.getLocationString() + ")"; 
	}
}
