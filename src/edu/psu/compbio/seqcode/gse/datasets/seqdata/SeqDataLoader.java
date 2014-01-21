/*
 * Created as ChipSeqLoader on May 16, 2007
 */
package edu.psu.compbio.seqcode.gse.datasets.seqdata;

import java.util.*;
import java.sql.*;
import java.io.*;


import edu.psu.compbio.seqcode.gse.datasets.general.AlignType;
import edu.psu.compbio.seqcode.gse.datasets.general.CellLine;
import edu.psu.compbio.seqcode.gse.datasets.general.ExptCondition;
import edu.psu.compbio.seqcode.gse.datasets.general.ExptTarget;
import edu.psu.compbio.seqcode.gse.datasets.general.ExptType;
import edu.psu.compbio.seqcode.gse.datasets.general.Lab;
import edu.psu.compbio.seqcode.gse.datasets.general.MetadataLoader;
import edu.psu.compbio.seqcode.gse.datasets.general.ReadType;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.general.StrandedRegion;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.projects.readdb.Client;
import edu.psu.compbio.seqcode.gse.projects.readdb.ClientException;
import edu.psu.compbio.seqcode.gse.projects.readdb.SingleHit;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseException;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseFactory;

/**
 * @author tdanford
 * @author mahony
 * 
 * SeqDataLoader serves as a clearinghouse for interacting with the seqdata database and
 * associated metadata from tables in the core database (via the MetadataLoader). 
 * 
 * Implements a simple access control by checking if the username is in the permissions field
 * in each SeqAlignment. Note that while this is access-control-lite for experiment metadata, 
 * access to the data stored in the underlying readdb entries has more robust access-control.
 */
public class SeqDataLoader implements edu.psu.compbio.seqcode.gse.utils.Closeable {

	public static String role = "seqdata";


	public static void main(String[] args) throws Exception{
		try {
			SeqDataLoader loader = new SeqDataLoader();
			Collection<SeqExpt> expts = loader.loadAllExperiments();
			for (SeqExpt expt : expts) {
				Collection<SeqAlignment> aligns = loader.loadAllAlignments(expt);
				for (SeqAlignment align : aligns) {
					System.out.println(expt.getDBID() + "\t" + expt.getName() + ";"+ expt.getReplicate()+"\t"+align.getName()+"\t"+align.getDBID()+"\t"+align.getGenome());
				}				
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private MetadataLoader metaLoader;
	private boolean closeMetaLoader;
	private java.sql.Connection cxn=null;
	private String myusername = "";
    private Client client=null;
    
    //Experiment descriptors
    private Collection<ExptType> exptTypes = null;
    private Collection<Lab> labs=null;
    private Collection<ExptTarget> exptTargets = null;
    private Collection<ExptCondition> exptConditions = null;
    private Collection<CellLine> celllines = null;
    private Collection<ReadType> readTypes = null;
    private Collection<AlignType> alignTypes = null;
    public Collection<ExptType> getExptTypes() throws SQLException {if(exptTypes==null){exptTypes=getMetadataLoader().loadAllExptTypes();} return exptTypes;}
    public Collection<Lab> getLabs() throws SQLException {if(labs==null){labs=getMetadataLoader().loadAllLabs();} return labs;}
    public Collection<ExptTarget> getExptTargets() throws SQLException {if(exptTargets==null){exptTargets = getMetadataLoader().loadAllExptTargets();} return exptTargets;}
    public Collection<ExptCondition> getExptConditions() throws SQLException {if(exptConditions==null){exptConditions = getMetadataLoader().loadAllExptConditions();} return exptConditions;} 
    public Collection<CellLine> getCellLines() throws SQLException {if(celllines==null){celllines = getMetadataLoader().loadAllCellLines();} return celllines;}
	public Collection<ReadType> getReadTypes() throws SQLException {if(readTypes==null){readTypes = getMetadataLoader().loadAllReadTypes();} return readTypes;}
	public Collection<AlignType> getAlignTypes() throws SQLException {if(alignTypes==null){alignTypes = getMetadataLoader().loadAllAlignTypes();} return alignTypes;}
        
    public SeqDataLoader() throws SQLException, IOException{this(true);}
	public SeqDataLoader(boolean openClient) throws SQLException, IOException {
		if(openClient){
	        try {
	            client = new Client();
	        } catch (ClientException e) {
	            throw new IllegalArgumentException(e);
	        }
		}
	}
    public java.sql.Connection getConnection() {
        if (cxn == null) {
            try {
                cxn = DatabaseFactory.getConnection(role);
                myusername = DatabaseFactory.getUsername(role);
            } catch (SQLException e) {
                throw new DatabaseException(e.toString(),e);
            }
        }
        return cxn;
    }
    
	public MetadataLoader getMetadataLoader() {
        if (metaLoader == null) {
            try {
                metaLoader = new MetadataLoader();
                closeMetaLoader = true;                   
            } catch (SQLException e) {
                throw new DatabaseException(e.toString(),e);
            }
        }
		return metaLoader;
	}


	public Collection<Genome> loadExperimentGenomes(SeqExpt expt) throws SQLException {
		LinkedList<Genome> genomes = new LinkedList<Genome>();
		String query = String.format("select genome from seqalignment where expt=%d", expt.getDBID());
		Statement s = getConnection().createStatement();
		ResultSet rs = s.executeQuery(query);
		while (rs.next()) {
			int gid = rs.getInt(1);
			try {
				Genome g = Organism.findGenome(gid);
				genomes.add(g);
			}
			catch (NotFoundException e) {
				e.printStackTrace();
			}
		}
		rs.close();
		s.close();
		return genomes;
	}


	public Collection<SeqExpt> loadAllExperiments() throws SQLException {
		System.err.println("Loading core experiment metadata...");
		labs=getMetadataLoader().loadAllLabs();
		exptTypes=getMetadataLoader().loadAllExptTypes();
		exptTargets = getMetadataLoader().loadAllExptTargets();
		exptConditions = getMetadataLoader().loadAllExptConditions();
        celllines = getMetadataLoader().loadAllCellLines();
        readTypes = getMetadataLoader().loadAllReadTypes();
        alignTypes = getMetadataLoader().loadAllAlignTypes();
        System.err.println("Loading seqdata experiment info...");
        PreparedStatement ps = SeqExpt.createLoadAll(getConnection());
        ps.setFetchSize(1000);
		LinkedList<SeqExpt> expts = new LinkedList<SeqExpt>();
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			expts.addLast(new SeqExpt(rs, this));
		}
		rs.close();
		ps.close();

		return expts;
	}


	public SeqExpt loadExperiment(String name, String rep) throws NotFoundException, SQLException {
		PreparedStatement ps = SeqExpt.createLoadByNameReplicate(getConnection());
		ps.setString(1, name);
		ps.setString(2, rep);
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		if (rs.next()) {
			expt = new SeqExpt(rs, this);
		}
		rs.close();
		ps.close();

		if (expt == null) { throw new NotFoundException(name+";"+rep); }
		return expt;
	}


	public Collection<SeqExpt> loadExperiments(String name) throws SQLException {
		PreparedStatement ps = SeqExpt.createLoadByName(getConnection());
		LinkedList<SeqExpt> expts = new LinkedList<SeqExpt>();
		ps.setString(1, name);
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		while (rs.next()) {
			expt = new SeqExpt(rs, this);
			expts.add(expt);
		}
		rs.close();
		ps.close();

		return expts;
	}
	
	public Collection<SeqExpt> loadExperiments(Lab lab) throws SQLException {
		PreparedStatement ps = SeqExpt.createLoadByLab(getConnection());
		LinkedList<SeqExpt> expts = new LinkedList<SeqExpt>();
		ps.setInt(1, lab.getDBID());
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		while (rs.next()) {
			expt = new SeqExpt(rs, this);
			expts.add(expt);
		}
		rs.close();
		ps.close();
		return expts;
	}
	
	public Collection<SeqExpt> loadExperiments(ExptCondition cond) throws SQLException {
		PreparedStatement ps = SeqExpt.createLoadByCondition(getConnection());
		LinkedList<SeqExpt> expts = new LinkedList<SeqExpt>();
		ps.setInt(1, cond.getDBID());
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		while (rs.next()) {
			expt = new SeqExpt(rs, this);
			expts.add(expt);
		}
		rs.close();
		ps.close();
		return expts;
	}
	
	public Collection<SeqExpt> loadExperiments(ExptTarget target) throws SQLException {
		PreparedStatement ps = SeqExpt.createLoadByTarget(getConnection());
		LinkedList<SeqExpt> expts = new LinkedList<SeqExpt>();
		ps.setInt(1, target.getDBID());
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		while (rs.next()) {
			expt = new SeqExpt(rs, this);
			expts.add(expt);
		}
		rs.close();
		ps.close();
		return expts;
	}

	public Collection<SeqExpt> loadExperiments(CellLine cell) throws SQLException {
		PreparedStatement ps = SeqExpt.createLoadByCellline(getConnection());
		LinkedList<SeqExpt> expts = new LinkedList<SeqExpt>();
		ps.setInt(1, cell.getDBID());
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		while (rs.next()) {
			expt = new SeqExpt(rs, this);
			expts.add(expt);
		}
		rs.close();
		ps.close();
		return expts;
	}

	public SeqExpt loadExperiment(int dbid) throws NotFoundException, SQLException {
		PreparedStatement ps = SeqExpt.createLoadByDBID(getConnection());
		ps.setInt(1, dbid);
		ResultSet rs = ps.executeQuery();
		SeqExpt expt = null;
		if (rs.next()) {
			expt = new SeqExpt(rs, this);
		}
		rs.close();
		ps.close();

		if (expt == null) {
			String err = String.format("No such SeqExpt experiment with ID= %d", dbid);
			throw new NotFoundException(err);
		}
		return expt;
	}

    public Collection<SeqAlignment> loadAlignments (Genome g) throws SQLException {
        //long start = System.currentTimeMillis();

        Collection<SeqExpt> allexpts = loadAllExperiments();
        Map<Integer,SeqExpt> exptmap = new HashMap<Integer,SeqExpt>();
        for (SeqExpt e : allexpts) {
            exptmap.put(e.getDBID(), e);
        }

        //long expttime = System.currentTimeMillis();
        //System.err.println("Got expts in " + (expttime - start) + "ms");

		Collection<SeqAlignment> aligns = new LinkedList<SeqAlignment>();
		PreparedStatement ps = SeqAlignment.createLoadAllByGenomeStatement(getConnection());
        ps.setFetchSize(1000);
		ps.setInt(1, g.getDBID());
        ResultSet rs = ps.executeQuery();
		while (rs.next()) {
            SeqAlignment align = new SeqAlignment(rs, exptmap.get(rs.getInt(2)), getMetadataLoader().loadAlignType(rs.getInt(6)));
            aligns.add(align);
		}
		rs.close();
		ps.close();
        
        //long end = System.currentTimeMillis();
        //System.err.println("Got all alignments in " + (end - start) + " ms");
		return filterAlignmentsByPermission(aligns);
    }

	public Collection<SeqAlignment> loadAllAlignments(SeqExpt expt) throws SQLException {
		Collection<SeqAlignment> aligns = new LinkedList<SeqAlignment>();
		PreparedStatement ps = SeqAlignment.createLoadAllByExptStatement(getConnection());
		ps.setInt(1, expt.getDBID());

		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			SeqAlignment align = new SeqAlignment(rs, expt, getMetadataLoader().loadAlignType(rs.getInt(6)));
			aligns.add(align);
		}
		rs.close();

		ps.close();
		return filterAlignmentsByPermission(aligns);
	}
	

	public SeqAlignment loadAlignment(SeqExpt expt, String n, Genome g) throws SQLException {
		SeqAlignment align = null;
		PreparedStatement ps = SeqAlignment.createLoadByNameAndExptStatement(getConnection());
		ps.setString(1, n);
		ps.setInt(2, expt.getDBID());

		ResultSet rs = ps.executeQuery();        
		while (align == null && rs.next()) {
			align = new SeqAlignment(rs, expt, getMetadataLoader().loadAlignType(rs.getInt(6)));
            if (!align.getGenome().equals(g))
                align = null;
            if(align!=null && !align.getPermissions().contains("public") && !align.getPermissions().contains(myusername))
				align=null;
		}
		rs.close();
		ps.close();
        if (align == null) {
        	//Don't throw exception because sometimes we have replicates that don't match all alignment names
            //throw new NotFoundException("Couldn't find alignment " + n + " for " + expt + " in genome " + g);
        }
        return align;

	}
	public SeqAlignment loadAlignment(int dbid) throws NotFoundException, SQLException {
		SeqAlignment align = null;
		PreparedStatement ps = SeqAlignment.createLoadByIDStatement(getConnection());
		ps.setInt(1, dbid);

		ResultSet rs = ps.executeQuery();
		if (rs.next()) {
			align = new SeqAlignment(rs, this);
			if(!align.getPermissions().contains("public") && !align.getPermissions().contains(myusername))
				align=null;
		}
		else {
			throw new NotFoundException("Couldn't find alignment with ID = " + dbid);
		}
		rs.close();
		ps.close();
		return align;
	}


	public Collection<SeqAlignment> loadAlignments(SeqLocator locator, Genome genome) throws SQLException, NotFoundException {
		List<SeqAlignment> output = new ArrayList<SeqAlignment>();
        if (locator.getReplicates().size() == 0) {
            for (SeqExpt expt : loadExperiments(locator.getExptName())) {
                SeqAlignment align = loadAlignment(expt, locator.getAlignName(), genome);
                if (align != null) {
                    output.add(align);
                }
            }
        } else {
            for (String rep : locator.getReplicates()) {
                try {
                    SeqExpt expt = loadExperiment(locator.getExptName(), rep);
                    SeqAlignment align = loadAlignment(expt, locator.getAlignName(), genome);
                    if (align != null) {
                        output.add(align);
                    }
                }
                catch (IllegalArgumentException e) {
                    throw new NotFoundException("Couldn't find alignment for " + locator);
                }
            }
        }
		return filterAlignmentsByPermission(output);
	}
                                                             
	public Collection<SeqAlignment> loadAlignments(String name, String replicate, String align,
                                                       Integer exptType, Integer lab, Integer condition,
                                                       Integer target, Integer cells, Integer readType,
                                                       Genome genome) throws SQLException {
        String query = "select id, expt, name, genome from seqalignment";
        if (name != null || replicate != null || align != null || exptType!=null || lab!=null || condition!=null || target != null || cells != null || readType != null || genome != null) {
            query += " where ";
        }
        boolean and = false;
        if (name != null || replicate != null || exptType!=null || lab!=null || condition!=null || target != null || cells != null || readType != null) {
            query += " expt in ( select id from seqexpt where ";
            if (name != null) { query += " name = ? "; and = true;}
            if (replicate != null) { query += (and ? " and " : " ") + " replicate = ? "; and = true;}
            if (exptType != null) { query += (and ? " and " : " ") + " expttype = " + exptType; and = true;}
            if (lab != null) { query += (and ? " and " : " ") + " lab = " + lab; and = true;}
            if (condition != null) { query += (and ? " and " : " ") + " exptcondition = " + condition; and = true;}
            if (target != null) { query += (and ? " and " : " ") + " expttarget = " + target; and = true;}
            if (cells != null) { query += (and ? " and " : " ") + " cellline = " + cells; and = true;}
            if (readType != null) { query += (and ? " and " : " ") + " readtype = " + readType; and = true;}
            query += ")";
            and = true;
        }
        if (genome != null) {query += (and ? " and " : " ") + " genome = " + genome.getDBID(); and = true; }
        if (align != null) {query += (and ? " and " : " ") + " name = ? "; and = true; }

        PreparedStatement ps = getConnection().prepareStatement(query);
        int index = 1;
        if (name != null || replicate != null) {
            if (name != null) { ps.setString(index++,name);}
            if (replicate != null) { ps.setString(index++,replicate);}
        }
        if (align != null) {ps.setString(index++,align);}
        
        ResultSet rs = ps.executeQuery();
        Collection<SeqAlignment> output = new ArrayList<SeqAlignment>();
        while (rs.next()) {
            try {
                output.add(new SeqAlignment(rs,this));
            } catch (NotFoundException e) {
                throw new DatabaseException(e.toString(),e);
            }
        }
        rs.close();
        ps.close();
        return filterAlignmentsByPermission(output);
    }
	
	/**
	 * Filter out alignments that the user shouldn't see
	 * @param aligns
	 * @return
	 */
	private Collection<SeqAlignment> filterAlignmentsByPermission(Collection<SeqAlignment> aligns){
		Collection<SeqAlignment> output = new ArrayList<SeqAlignment>();
		for(SeqAlignment a : aligns)
			if(a.getPermissions().contains("public") || a.getPermissions().contains(myusername))
				output.add(a);
		return output;
	}

	 public static Map<String,String> readParameters(BufferedReader reader) throws IOException {
		Map<String, String> params = new HashMap<String, String>();
		String line = null;
		while ((line = reader.readLine()) != null) {
			int p = line.indexOf('=');
			String key = line.substring(0, p);
			String value = line.substring(p + 1);
			params.put(key, value);
		}
		reader.close();
        return params;
    }

	public void addAlignmentParameters(SeqAlignment align, File paramsfile) throws SQLException, IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(paramsfile)));
		addAlignmentParameters(align, readParameters(reader));
	}


	public void addAlignmentParameters(SeqAlignment align, Map<String, ? extends Object> params) throws SQLException {
		PreparedStatement insert = getConnection().prepareStatement("insert into alignmentparameters(alignment,name,value) values(?,?,?)");
		insert.setInt(1, align.getDBID());
		for (String k : params.keySet()) {
			insert.setString(2, k);
			Object val = params.get(k);
			if (val == null) {
				val = "";
			} else {
				val = val.toString();
				if (val == null) {
					val = "";
				}
			}


			insert.setString(3, (String)val);
			insert.execute();
		}
		insert.close();
	}


	public Map<String, String> getAlignmentParameters(SeqAlignment align) throws SQLException {
		Statement get = getConnection().createStatement();
		ResultSet rs = get.executeQuery("select name, value from alignmentparameters where alignment = " + align.getDBID());
		Map<String, String> output = new HashMap<String, String>();
		while (rs.next()) {
			output.put(rs.getString(1), rs.getString(2));
		}
		rs.close();
		get.close();
		return output;

	}

	public void deleteAlignmentParameters(SeqAlignment align) throws SQLException {
		Statement del = getConnection().createStatement();
		del.execute("delete from alignmentparameters where alignment = " + align.getDBID());
	}
    
	
	/*
	 * SeqHit loading: problem with this is that SingleHits are assumed.  
	 */
	
	public List<SeqHit> convert(Collection<SingleHit> input, SeqAlignment align) {
        Genome g = align.getGenome();
        ArrayList<SeqHit> output = new ArrayList<SeqHit>();
        for (SingleHit s : input) {
            int start = s.pos;
            int end = s.strand ? s.pos + s.length : s.pos - s.length;
            output.add(new SeqHit(g, g.getChromName(s.chrom), Math.min(start,end), Math.max(start,end),
                                      s.strand ? '+' : '-', s.weight));
        }
        return output;
    }
	
                                 
	public List<SeqHit> loadByChrom(SeqAlignment a, int chromid) throws IOException {
		List<SeqHit> data = new ArrayList<SeqHit>();
        String alignid = Integer.toString(a.getDBID());
        try {
            data.addAll(convert(client.getSingleHits(alignid, chromid,null,null,null,null),a));
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
		return data;
	}
			
	public List<SeqHit> loadByRegion(SeqAlignment align, Region r) throws IOException {
        try {
            return convert(client.getSingleHits(Integer.toString(align.getDBID()),
                                                r.getGenome().getChromID(r.getChrom()),
                                                r.getStart(),
                                                r.getEnd(),
                                                null,
                                                null), align);
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
	}
			
	public Collection<SeqHit> loadByRegion(List<SeqAlignment> alignments, Region r) throws IOException {
		if (alignments.size() < 1) {
			throw new IllegalArgumentException("Alignment List must not be empty.");
		}
        Collection<SeqHit> output = null;
        for (SeqAlignment a : alignments) {
            if (output == null) {
                output = loadByRegion(a,r);
            } else {
                output.addAll(loadByRegion(a,r));
            }
        }
		return output;
	}
	
	    
    /* if Region is a StrandedRegion, then the positions returned are only for that strand */
    public List<Integer> positionsByRegion(List<SeqAlignment> alignments, Region r) throws IOException, ClientException {
		if (alignments.size() < 1) {
			throw new IllegalArgumentException("Alignment List must not be empty.");
		}
        List<Integer> output = new ArrayList<Integer>();
        for (SeqAlignment a : alignments) {
            int[] pos = client.getPositions(Integer.toString(a.getDBID()),
                                            r.getGenome().getChromID(r.getChrom()),
                                            false,
                                            r.getStart(),
                                            r.getEnd(),
                                            null,
                                            null,
                                            r instanceof StrandedRegion ? null : (((StrandedRegion)r).getStrand() == '+'));
            for (int i = 0; i < pos.length; i++) {
                output.add(pos[i]);
            }                                            
        }
        return output;
    }
    public List<Integer> positionsByRegion(SeqAlignment alignment, Region r) throws IOException {
        List<Integer> output = new ArrayList<Integer>();
        try {
            int[] pos = client.getPositions(Integer.toString(alignment.getDBID()),
                                            r.getGenome().getChromID(r.getChrom()),
                                            false,
                                            r.getStart(),
                                            r.getEnd(),
                                            null,
                                            null,
                                            r instanceof StrandedRegion ? null : (((StrandedRegion)r).getStrand() == '+'));
            for (int i = 0; i < pos.length; i++) {
                output.add(pos[i]);
            }                                            
            return output;
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
    }

	public int countByRegion(SeqAlignment align, Region r) throws IOException {
        try {
            return client.getCount(Integer.toString(align.getDBID()),
                                   r.getGenome().getChromID(r.getChrom()),
                                   false,
                                   r.getStart(),
                                   r.getEnd(),
                                   null,
                                   null,
                                   null);
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
    }
    

	public int countByRegion(List<SeqAlignment> alignments, Region r) throws IOException {
		if (alignments.size() < 1) { 
			throw new IllegalArgumentException("Alignment List must not be empty."); 
		}
        int total = 0;
        for (SeqAlignment a : alignments) {
            total += countByRegion(a,r);
        }
        return total;
	}
	public int countByRegion(SeqAlignment align, StrandedRegion r) throws IOException {
        try {
            return client.getCount(Integer.toString(align.getDBID()),
                                   r.getGenome().getChromID(r.getChrom()),
                                   false,
                                   r.getStart(),
                                   r.getEnd(),
                                   null,
                                   null,
                                   r.getStrand() == '+');
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
	}
	public int countByRegion(List<SeqAlignment> alignments, StrandedRegion r) throws IOException {
		if (alignments.size() < 1) { 
			throw new IllegalArgumentException("Alignment List must not be empty."); 
		}
        int total = 0;
        for (SeqAlignment a : alignments) {
            total += countByRegion(a,r);
        }
        return total;
	}

	
	public double weightByRegion(List<SeqAlignment> alignments, Region r) throws IOException {
		if (alignments.size() < 1) { 
			throw new IllegalArgumentException("Alignment List must not be empty."); 
		}
        double total = 0;
        for (SeqAlignment a : alignments) {
            try {
                total += client.getWeight(Integer.toString(a.getDBID()),
                                          r.getGenome().getChromID(r.getChrom()),
                                          false,
                                          r.getStart(),
                                          r.getEnd(),
                                          null,
                                          null,
                                          null);
            } catch (ClientException e) {
                throw new IllegalArgumentException(e);
            }            
        }
        return total;
	}
	public double weightByRegion(List<SeqAlignment> alignments, StrandedRegion r) throws IOException {
		if (alignments.size() < 1) { 
			throw new IllegalArgumentException("Alignment List must not be empty."); 
		}
        double total = 0;
        for (SeqAlignment a : alignments) {
            try {
                total += client.getWeight(Integer.toString(a.getDBID()),
                                          r.getGenome().getChromID(r.getChrom()),
                                          false,
                                          r.getStart(),
                                          r.getEnd(),
                                          null,
                                          null,
                                          r.getStrand() == '+');
            } catch (ClientException e) {
                throw new IllegalArgumentException(e);
            }            
        }
        return total;
	}


	/**
	 * @param align
	 * @return
	 * @throws SQLException
	 */
	public int countAllHits(SeqAlignment align) throws IOException {
        try {
            return client.getCount(Integer.toString(align.getDBID()),
                                   false,false,null);
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
	}


	public double weighAllHits(SeqAlignment align) throws IOException {
        try {
            return client.getWeight(Integer.toString(align.getDBID()),
                                    false,false,null);
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
	}

	/** Generates a histogram of the total weight of reads mapped to each bin.
     * Output maps bin center to weight centered around that bin.  Each read
     * is summarized by its start position.
     */
    public Map<Integer,Float> histogramWeight(SeqAlignment align, char strand, Region r, int binWidth) throws IOException {
        try {
            return client.getWeightHistogram(Integer.toString(align.getDBID()),
                                             r.getGenome().getChromID(r.getChrom()),
                                             false,
                                             false,
                                             binWidth,
                                             r.getStart(),
                                             r.getEnd(),
                                             null,
                                             strand == '+');
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
    }
    /** Generates a histogram of the total number of reads mapped to each bin.
     * Output maps bin center to weight centered around that bin.  Each read
     * is summarized by its start position.
     */
    public Map<Integer,Integer> histogramCount(SeqAlignment align, char strand, Region r, int binWidth) throws IOException {        
        try {
            return client.getHistogram(Integer.toString(align.getDBID()),
                                       r.getGenome().getChromID(r.getChrom()),
                                       false,
                                       false,
                                       binWidth,
                                       r.getStart(),
                                       r.getEnd(),
                                       null,
                                       strand == '+');
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
    }
    /**
     * Get the total # of hits and weight for an alignment but only include reads
     * on the specified strand.  
     */
    public Pair<Long,Double> getAlignmentStrandedCountWeight(SeqAlignment align, char strand) throws IOException {
        try {
            long count = client.getCount(Integer.toString(align.getDBID()), false, false, strand=='+');
            double weight = client.getWeight(Integer.toString(align.getDBID()), false, false, strand=='+');
            Pair<Long,Double> output = new Pair<Long,Double>(count,weight);
            return output;
        } catch (ClientException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
	public void close() {
		if (closeMetaLoader && !metaLoader.isClosed()) {
			metaLoader.close();
            metaLoader = null;
		}
        if (client != null) {
            client.close();
            client = null;
        }
        if (cxn != null) {
            DatabaseFactory.freeConnection(cxn);
            cxn = null;
        }
	}


	public boolean isClosed() {
		return cxn == null;
	}

}