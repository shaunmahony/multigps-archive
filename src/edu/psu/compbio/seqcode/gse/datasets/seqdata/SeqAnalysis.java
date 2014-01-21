package edu.psu.compbio.seqcode.gse.datasets.seqdata;

import java.util.*;
import java.io.*;
import java.sql.*;

import edu.psu.compbio.seqcode.gse.datasets.general.*;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.database.*;

/**
 * A SeqAnalysis represents the results of running some binding-call or 
 * peak finding program on a set of SeqAlignments.  The name and version of 
 * the analysis are independent of the name and version of the alignments, though
 * in practice they should be related; the name and version are
 * used as they DB key.
 *
 * The analysis stores a set of parameters (Map<String,String>) that can be 
 * any relevant data about how the binding calls were generated.
 */

public class SeqAnalysis implements Comparable<SeqAnalysis> {

    private Map<String,String> params;
    private Set<SeqAlignment> foreground, background;
    private String name, version, program;
    private Integer dbid;
    private List<SeqAnalysisResult> results;
    private boolean active;

    /* these methods (through store()) are primarily for constructing a 
       ChipSeqAnalysis and saving it to the DB */
    public SeqAnalysis (String name, String version, String program) {
        this.name = name;
        this.version = version;
        this.program = program;
        dbid = null;
        params = null;
        foreground = null;
        background = null;
        results = new ArrayList<SeqAnalysisResult>();
        this.active = true;
    }
    public SeqAnalysis (String name, String version, String program, boolean active) {
        this.name = name;
        this.version = version;
        this.program = program;
        dbid = null;
        params = null;
        foreground = null;
        background = null;
        results = new ArrayList<SeqAnalysisResult>();
        this.active = active;
    }
    public void setActive(boolean b) {active = b;}
    public void setParameters(Map<String,String> params) {
        this.params = params;
    }
    public void setInputs(Set<SeqAlignment> foreground,
                          Set<SeqAlignment> background) {
        this.foreground = foreground;
        this.background = background;
    }
    public void addResult(SeqAnalysisResult result) {
        results.add(result);
    }
    private void storeinputs(PreparedStatement ps,
                             String type,
                             int analysis,
                             int alignment) throws SQLException {
        ps.setInt(1,analysis);
        ps.setInt(2,alignment);
        ps.setString(3, type);
        ps.execute();
    }
                             
    public void store() throws SQLException {
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        cxn.setAutoCommit(false);
        String q = "insert into chipseqanalysis (id, name, version, program, active) values (%s,?,?,?,?)";
        PreparedStatement ps = cxn.prepareStatement(String.format(q,edu.psu.compbio.seqcode.gse.utils.database.Sequence.getInsertSQL(cxn, "chipseqanalysis_id")));
        ps.setString(1, name);
        ps.setString(2, version);
        ps.setString(3, program);
        ps.setInt(4,active ? 1 : 0);
        ps.execute();
        ps.close();
        String sql = edu.psu.compbio.seqcode.gse.utils.database.Sequence.getLastSQLStatement(cxn, "chipseqanalysis_id");
        ps = cxn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        rs.next();
        dbid = rs.getInt(1);
        rs.close();
        ps.close();
        ps = null;
        
        if (params != null && params.size() > 0) {
            ps = cxn.prepareStatement("insert into analysisparameters(analysis,name,value) values (?,?,?)");
            ps.setInt(1,dbid);
            for (String k : params.keySet()) {
                ps.setString(2,k);
                ps.setString(3,params.get(k));
                ps.execute();
            }
            ps.close();
        }
        ps = null;
        if (foreground != null && foreground.size() > 0) {
            ps = cxn.prepareStatement("insert into analysisinputs(analysis, alignment, inputtype) values (?,?,?)");
            for (SeqAlignment a : foreground) {
                storeinputs(ps, "foreground", dbid, a.getDBID());
            }
        }
        if (background != null && background.size() > 0) {
            if (ps == null) {
                ps = cxn.prepareStatement("insert into analysisinputs(analysis, alignment, inputtype) values (?,?,?)");
            }
            for (SeqAlignment a : background) {
                storeinputs(ps, "background", dbid, a.getDBID());
            }
        }
        if (ps != null) {
            ps.close();
        }
        if (results != null && results.size() > 0) {
            ps = cxn.prepareStatement("insert into analysisresults(analysis,chromosome,startpos,stoppos,position,fgcount,bgcount,strength,peak_shape,pvalue,fold_enrichment) " +
                                     " values (?,?,?,?,?,?,?,?,?,?,?)");
            ps.setInt(1,dbid);
            for (SeqAnalysisResult r : results) {
                ps.setInt(2, r.getGenome().getChromID(r.getChrom()));
                ps.setInt(3, r.getStart());
                ps.setInt(4, r.getEnd());
                if (r.position == null) {
                    ps.setNull(5, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(5, r.position);
                }
                if (r.foregroundReadCount == null) {
                    ps.setNull(6,java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(6,r.foregroundReadCount);
                }
                if (r.backgroundReadCount == null) {
                    ps.setNull(7,java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(7,r.backgroundReadCount);
                }
                if (r.strength == null) {
                    ps.setNull(8,java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(8,r.strength);
                }
                if (r.shape == null) {
                    ps.setNull(9,java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(9,r.shape);
                }
                if (r.pvalue == null) {
                    ps.setNull(10,java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(10,r.pvalue);
                }
                if (r.foldEnrichment == null) {
                    ps.setNull(11,java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(11,r.foldEnrichment);
                }
                ps.execute();
            }
            ps.close();
        }
        cxn.commit();
		DatabaseFactory.freeConnection(cxn);
    }
    /* stores the active flag to the database.  Must be done
       on an object that has already been stored such that it has a DBID */
    public void storeActiveDB() throws SQLException {
        if (dbid == null) {
            throw new RuntimeException("Must have a dbid");
        }
        String sql = "update chipseqanalysis set active = ? where id = ?";
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        PreparedStatement ps = cxn.prepareStatement(sql);
        
        ps.setInt(1, active ? 1 : 0);
        ps.setInt(2, dbid);
        ps.execute();
        cxn.commit();
        DatabaseFactory.freeConnection(cxn);
        
    }

    /* these methods are primarily for querying an object that you've gotten back
       from the database */
    public String toString() {
        return name + ";" + version + ";" + program;
    }
    public Integer getDBID() {return dbid;}
    public String getName() {return name;}
    public String getVersion() {return version;}
    public String getProgramName() {return program;}
    public boolean isActive() {return active;}
    public Map<String,String> getParams() {
        if (params == null) {
            try {
                loadParams();
            } catch (SQLException e) {
                throw new DatabaseException(e.toString(),e);
            }
        }
        return params;
    }
    public Set<SeqAlignment> getForeground() {
        if (foreground == null) {
            try {
                loadInputs();
            } catch (SQLException e) {
                throw new DatabaseException(e.toString(),e);
            }
        }
        return foreground;
    }
    public Set<SeqAlignment> getBackground() {
        if (background == null) {
            try {
                loadInputs();
            } catch (SQLException e) {
                throw new DatabaseException(e.toString(),e);
            }
        }
        return background;
    }
    /** fills in the parameters from the database */
    private void loadParams() throws SQLException {
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        PreparedStatement ps = cxn.prepareStatement("select name,value from analysisparameters where analysis = ?");
        ps.setInt(1,dbid);
        ResultSet rs = ps.executeQuery();
        HashMap<String,String> params = new HashMap<String,String>();
        while (rs.next()) {
            params.put(rs.getString(1), rs.getString(2));
        }
        setParameters(params);
        rs.close();
        ps.close();        
        DatabaseFactory.freeConnection(cxn);
    }
    /** fills in the input experiment fields from the database */
    private void loadInputs() throws SQLException {
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        PreparedStatement ps = cxn.prepareStatement("select alignment, inputtype from analysisinputs where analysis = ?");
        ps.setInt(1,dbid);
        HashSet<SeqAlignment> fg = new HashSet<SeqAlignment>();
        HashSet<SeqAlignment> bg = new HashSet<SeqAlignment>();
        ResultSet rs = ps.executeQuery();
        try {
            SeqDataLoader loader = new SeqDataLoader(false);
            while (rs.next()) {
                if (rs.getString(2).equals("foreground")) {
                    fg.add(loader.loadAlignment(rs.getInt(1)));
                } else if (rs.getString(2).equals("background")) {
                    bg.add(loader.loadAlignment(rs.getInt(1)));
                }
            }
            setInputs(fg,bg);
            rs.close();
            ps.close();
            loader.close();

        } catch (IOException e) {
            /* IOException comes from the loader trying to connect to readdb,
               but we told it not to do that.  So this shouldn't
               happen
            */
            throw new RuntimeException(e.toString(),e);
        } catch (NotFoundException e) {
            /* the loader throws a NotFoundException if it can't
               find the alignment.  But the database constraints should
               have prevented us from getting an invalid alignment id back.
            */
            throw new DatabaseException(e.toString(),e);
        }
        DatabaseFactory.freeConnection(cxn);
    }
    public List<SeqAnalysisResult> getResults(Genome g) throws SQLException {
        return getResults(g,null);
    }
    public List<SeqAnalysisResult> getResults(Region queryRegion) throws SQLException {
        return getResults(queryRegion.getGenome(), queryRegion);
    }
    private Integer isnullint(ResultSet r, int index) throws SQLException {
        Integer i = r.getInt(index);
        if (i == 0 && r.wasNull()) {
            return null;
        } else {
            return i;
        }
    }
    private Double isnulldouble(ResultSet r, int index) throws SQLException {
        Double i = r.getDouble(index);
        if (i == 0 && r.wasNull()) {
            return null;
        } else {
            return i;
        }
    }
    public List<SeqAnalysisResult> getResults(Genome genome, Region queryRegion) throws SQLException {
        
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        String query = "select chromosome, startpos, stoppos, position, fgcount, bgcount, strength, peak_shape, pvalue, fold_enrichment " +
            " from analysisresults where analysis = ? ";
        if (queryRegion != null) {
            query += " and chromosome = ? and startpos >= ? and stoppos <= ?";
        }
        PreparedStatement ps = cxn.prepareStatement(query);
        ps.setInt(1, dbid);
        if (queryRegion != null) {
            ps.setInt(2, queryRegion.getGenome().getChromID(queryRegion.getChrom()));
            ps.setInt(3, queryRegion.getStart());
            ps.setInt(4, queryRegion.getEnd());
        }
        ResultSet rs = ps.executeQuery();
        List<SeqAnalysisResult> result = new ArrayList<SeqAnalysisResult>();
        while (rs.next()) {
            SeqAnalysisResult r = new SeqAnalysisResult(genome,
                                                                genome.getChromName(rs.getInt(1)),
                                                                rs.getInt(2),
                                                                rs.getInt(3),
                                                                isnullint(rs,4),
                                                                isnulldouble(rs,5),
                                                                isnulldouble(rs,6),
                                                                isnulldouble(rs,7),
                                                                isnulldouble(rs,8),
                                                                isnulldouble(rs,9),
                                                                isnulldouble(rs,10));
            if (Double.isInfinite(r.foldEnrichment)) {
                r.foldEnrichment = r.foregroundReadCount / Math.max(.1, r.backgroundReadCount);
            }

            result.add(r);                                                                
        }
        rs.close();
        ps.close();
		DatabaseFactory.freeConnection(cxn);
        return result;        
    }
    public int countResults(Genome genome) throws SQLException {
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        String chrstring = "";
        Map<String,Integer> map = genome.getChromIDMap();
        Iterator<Integer> iter = map.values().iterator();
        if (iter.hasNext()) {
            chrstring = Integer.toString(iter.next());
        }
        while (iter.hasNext()) {
            chrstring = chrstring + "," + Integer.toString(iter.next());
        }

        String query = "select count(*) from analysisresults where analysis = ? and chromosome in (" +chrstring+")";
        PreparedStatement ps = cxn.prepareStatement(query);
        ps.setInt(1,dbid);
        ResultSet rs = ps.executeQuery();
        rs.next();
        int count = rs.getInt(1);
        rs.close();
        ps.close();
		DatabaseFactory.freeConnection(cxn);
        return count;
    }

    /** retrieves all ChipSeqAnalysis objects from the database */
    public static Collection<SeqAnalysis> getAll() throws DatabaseException, SQLException {
        return getAll(true);
    }
    public static Collection<SeqAnalysis> getAll(Boolean active) throws DatabaseException, SQLException {
        ArrayList<SeqAnalysis> output = new ArrayList<SeqAnalysis>();
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        String sql = "select id, name, version, program, active from chipseqanalysis";
        if (active != null) {
            sql = sql + " where active = " + (active ? 1 : 0);
        }

        PreparedStatement ps = cxn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            SeqAnalysis a = new SeqAnalysis(rs.getString(2),
                                                    rs.getString(3),
                                                    rs.getString(4),
                                                    rs.getInt(5) != 0 ? true : false);
            a.dbid = rs.getInt(1);
            output.add(a);
        }
        rs.close();
        ps.close();
		DatabaseFactory.freeConnection(cxn);
        return output;
        
    }
    /** Retrieves the ChipSeqAnalysis with the specified name and version */
    public static SeqAnalysis get(SeqDataLoader loader, String name, String version) throws NotFoundException, DatabaseException, SQLException {
        return get(loader,name,version,true);
    }
    public static SeqAnalysis get(SeqDataLoader loader, String name, String version, Boolean active) throws NotFoundException, DatabaseException, SQLException {
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        String sql = "select id, program, active from chipseqanalysis where name = ? and version = ?";
        if (active != null) {
            sql = sql + " and active = " + (active ? 1 : 0);
        }
        PreparedStatement ps = cxn.prepareStatement(sql);
        ps.setString(1,name);
        ps.setString(2,version);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            throw new NotFoundException("Couldn't find analysis " + name + "," + version);
        }
        SeqAnalysis result = new SeqAnalysis(name,version, rs.getString(2), rs.getInt(3) != 0 ? true : false);
        result.dbid = rs.getInt(1);
        rs.close();
        ps.close();
		DatabaseFactory.freeConnection(cxn);
        return result;
    }
    /** returns the collection of active analyses that have a result in the specified region
     */
    public static Collection<SeqAnalysis> withResultsIn(SeqDataLoader loader, Region r) throws SQLException {
        java.sql.Connection cxn = DatabaseFactory.getConnection(SeqDataLoader.role);
        String sql = "select id, name, version, program, active from chipseqanalysis where id in (select unique(analysis) from analysisresults where chromosome = ? and startpos >= ? and stoppos <= ?) and active = 1";
        ArrayList<SeqAnalysis> output = new ArrayList<SeqAnalysis>();
        PreparedStatement ps = cxn.prepareStatement(sql);
        ps.setInt(1, r.getGenome().getChromID(r.getChrom()));
        ps.setInt(2, r.getStart());
        ps.setInt(3, r.getEnd());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            SeqAnalysis a = new SeqAnalysis(rs.getString(2),
                                                    rs.getString(3),
                                                    rs.getString(4),
                                                    rs.getInt(5) != 0 ? true : false);
            a.dbid = rs.getInt(1);
            output.add(a);
        }
        rs.close();
        ps.close();
		DatabaseFactory.freeConnection(cxn);
        return output;
        
    }
 
    public int compareTo(SeqAnalysis other) {
        int c = name.compareTo(other.name);
        if (c == 0) {
            c = version.compareTo(other.version);
            if (c == 0) {
                c = program.compareTo(other.program);
            }
        }
        return c;
    }


}