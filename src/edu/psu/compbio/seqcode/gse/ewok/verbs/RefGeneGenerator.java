package edu.psu.compbio.seqcode.gse.ewok.verbs;

import java.util.*;
import java.io.*;
import java.sql.*;

import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.species.ExonicGene;
import edu.psu.compbio.seqcode.gse.datasets.species.Gene;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.ewok.types.*;
import edu.psu.compbio.seqcode.gse.utils.database.*;

/** Generator that returns Gene objects from the refGene table (or table with a similar structure, eg sgdGene) in
 *   a UCSC annotation database.  RefGeneGenerator can work on two tables- the first contains the coordinates
 *   for the genes and the second contains a mapping from gene name to aliases.
 *   
 *   reads the properties file edu.psu.compbio.seqcode.gse.ewok.verbs.gene_names which is formatted as 
 *     genomeversion,tablename=symboltablename,namecolumn,symbolcolumn
 *
 *     namecolumn is the column in the symboltable that should match the name.  aliascolumn
 *     is the column in the alias table that contains the alias
 *
 *   RefGeneGenerator can query in several modes:
 *   - genes whose orf overlaps the query region
 *   - genes whose 5' IGR overlaps the query region
 *   - the closest n genes to a region, regardless of distance
 * 
 *   Most of the SQL generation code here could probably be generalized out and then reused to query a larger number of table
 *   types
 *   
 *   Note from Shaun: One quirk of the UCSC tables is that the starting coordinates of genes, exons, etc
 *   is 0-based, while the end coordinate is 1-based. Correcting for that here. 
 *
 * @author Alex Rolfe
 */

public class RefGeneGenerator<X extends Region> 
    implements Expander<X,Gene>, SelfDescribingVerb, DefaultConstantsParameterized, edu.psu.compbio.seqcode.gse.utils.Closeable {

    private PreparedStatement ps, nameps, getalias, getgenesym, getallps;
    private java.sql.Connection cxn;
    private Genome genome;
    private String tablename, symboltable, namecolumn, symbolcolumn;
    private int aliastype;
    private boolean wantalias, wantsymbol, flipstrand, wantCoding, wantsExons;
    private static final int YEAST = 1, MAMMAL = 2, FLY = 3, WORM = 4;    
    private static final int TOSTART = 1, TOEND = 2, TOWHOLE = 3;
    private int upstream, downstream, closestN, toBoundary;

    /**
     * Creates a <code>RefGeneGenerator</code> for the default gene type/table
     * for the specified genome.
     */
    public RefGeneGenerator(Genome g) {
        ps = null;
        nameps = null;
        getalias = null;
        getgenesym = null;
        wantsExons = true;
        setGenome(g, null);
        wantalias = true;
        upstream = 0;
        downstream = 0;
        closestN = 0;
        wantCoding = false;
        toBoundary = TOSTART;
        flipstrand = false;
        wantsymbol = true;
    }
    
    /**
     * @param t the tablename from which gene information is retrieved 
     */
    public RefGeneGenerator(Genome g, String t) {
        ps = null;
        nameps = null;
        getalias = null;
        getgenesym = null;
        wantsExons = true;
        setGenome(g, t);
        wantalias = true;
        upstream = 0;
        downstream = 0;
        closestN = 0;
        wantCoding = false;
        toBoundary = TOSTART;  
        flipstrand = false;
        wantsymbol = true;
    }
    
    public RefGeneGenerator() { 
        ps = null;
        nameps = null;
        getalias = null;
        getgenesym = null;
        wantsExons = true;
        wantalias = true;
        genome = null;
        tablename = symboltable = null;
        aliastype = -1;
        upstream = 0;
        downstream = 0;
        closestN = 0;
        wantCoding = false;
        toBoundary = TOSTART;        
        flipstrand = false;
        wantsymbol = true;
    }
    
    public void setGenome(Genome g, String t) { 
        genome = g;
        ResourceBundle res = ResourceBundle.getBundle("edu.psu.compbio.seqcode.gse.ewok.gene_names");
        Enumeration<String> keys = res.getKeys();
        tablename = t;
        /* if no table name was given, then we'll end up using the first table listed in the 
           gene_names properties file */
        String targetkey = g.getVersion() + "," + tablename;
        if (tablename == null) {
            targetkey = null;
        }

        while(keys.hasMoreElements()) { 
            String key = keys.nextElement();
            if (targetkey == null) {
                if (key.matches(g.getVersion() + ".*")) {
                    String p[] = key.split(",");
                    tablename = p[1];
                } else {
                    continue;
                }
            } else {
                if (!key.equals(targetkey)) { continue;}
            }
            String props[] = res.getString(key).split(",");
            symboltable = props[0];
            namecolumn = props[1];
            symbolcolumn = props[2];
        }
        if (symboltable == null) {
            wantalias = false;
        }
        prepare();
    }
    
    /* fill in exon fields of Genes */
    public boolean isRetrievingExons() { return wantsExons; }
    public void retrieveExons(boolean b) {
        wantsExons = b;
        prepare();
    }
    /** if retrieveCoding is true, then the start and 
     *  stop will be of the coding region rather than the transcribed region
     */
    public boolean isRetrievingCodingRegion() {return wantCoding;}
    public void retrieveCoding(boolean b) {
        wantCoding = b;
        prepare();
    }
    public String getStartField() {
        return wantCoding ? "cdsStart" : "txStart";
    }
    public String getEndField() {
        return wantCoding ? "cdsEnd" : "txEnd";
    }
    public Genome getGenome() {return genome;}
    public String getTable() {return tablename;}
    /* retrieve aliases with Genes */
    public boolean getWantAlias() {return wantalias;}
    public void setWantAlias(boolean b) {
        wantalias = symboltable != null && b;        
    }
    /* retrieve the gene symbol rather than, eg, NM_123456 */
    public boolean getWantSymbol() {return wantsymbol;}
    public void setWantSymbol(boolean b) {wantsymbol = b;}
    public void setFlipStrand(boolean b) {
        flipstrand = b;
    }
    public void setUpstreamDownstream(int up, int down) {
        if (upstream == 0 && downstream == 0 && 
            (up != 0 || down != 0)) {
            close();
        }
        upstream = up;
        downstream = down;
    }
    public void setUpstream(int up) {
        if (upstream == 0 && downstream == 0 && 
            (up != 0)) {
            close();
        }
        upstream = up;
    }
    public void setDownstream(int down) {
        if (upstream == 0 && downstream == 0 && 
            (down != 0)) {
            close();
        }
        downstream = down;
    }
    public void setClosestN(int n) {
        if (n != closestN) {
            close();
        }
        closestN = n;
    }
    public void setToStart() {
        if (toBoundary != TOSTART) {
            close();
        }
        toBoundary = TOSTART;
    }
    public void setToEnd() {
        if (toBoundary != TOEND) {
            close();
        }
        toBoundary = TOEND;
    }
    public void setToEitherEnd() {
        if (toBoundary != TOWHOLE) {
            close();
        }
        toBoundary = TOWHOLE;
    }
    
    /* close releases the database connection.  This ought to be called
       when query parameters change that might change the structure of the SQL.
       Calling close() unnecessarily is safe; at worst it hurts performance
    */
    public void close() {
        try {
            if (ps != null) {
                ps.close();
                ps = null;
            }
            if (nameps != null) {
                nameps.close();
                nameps = null;
            }
            if (getalias != null) {
                getalias.close();
                getalias = null;
            }
            if (getgenesym != null) {
                getgenesym.close();
                getgenesym = null;
            }
            if (getallps != null) {
                getallps.close();
                getallps = null;
            }

            if (cxn != null) {
                DatabaseFactory.freeConnection(cxn);
                cxn = null;
            }            
        } catch (SQLException e) {
            throw new DatabaseException(e.toString(), e);
        }
    }
    public boolean isClosed() {
        return cxn == null;
    }
    /* generate SQL PreparedStatements based on the current 
       query parameters.  Set ps and nameps.

       Any changes made to ps here probably need to be reflected 
       in execute() so the bind variables set up in the sql query
       here are bound in the proper order in execute().
    */
    private void prepare() {
        ps = null;
        nameps = null;
        if (genome == null) {
            //            System.err.println("No genome.  returning from prepare()");
            return;
        }
        try {
            cxn = genome.getUcscConnection();
            StringBuffer query = new StringBuffer(getFields());
            query.append(" from ");
            query.append(tablename);
            query.append(" where chrom = ? ");
            if (upstream != 0 || downstream != 0) {
                query.append(" and ");
                query.append(getUpstreamOverlap());
            } else if (closestN == 0) {
                query.append(" and ");
                query.append(getOrfOverlap());
            } /* if closestN != 0, don't limit at all.  */

            if (closestN != 0) {
                query.append(getClosestOrder());
                query.append(" limit ? ");
            } else {
                query.append(" order by " + getStartField() +  " ");
            }

            //            System.err.println("query is " + query);
            ps = cxn.prepareStatement(query.toString());
            
            getallps = cxn.prepareStatement(getFields() + " from " + tablename);

            String namequery = getFields();
            if (symboltable == null) {
                namequery += " from " + tablename + " g where g.name = ?";
            } else {
                // this noop extra select is to fool the mysql query optimizer so it
                // doesn't screw this query up
                namequery += String.format(" from %s g where g.name = ? or g.name in (select id from (select %s as id from %s a where a.%s = ? ) as x)",
                                           tablename, 
                                           namecolumn,
                                           symboltable,
                                           symbolcolumn);
            }
            nameps = cxn.prepareStatement(namequery);
            if (symboltable != null && symbolcolumn != null && namecolumn != null) {
                getgenesym = cxn.prepareStatement(String.format("select %s from %s where %s=?",
                                                                symbolcolumn, symboltable, namecolumn));
            } else {
                getgenesym = null;
            }
            if (wantalias && symboltable != null && symboltable.equals("kgXref")) {
                try {
                    cxn = genome.getUcscConnection();
                    String aliassql = "select distinct(kgAlias.alias) from kgAlias, kgXref where kgXref.kgID = kgAlias.kgID and kgXref." +
                        namecolumn + " = ?";
                    getalias = cxn.prepareStatement(aliassql);
                } catch (SQLException ex) {
                ex.printStackTrace();
                wantalias = false;
                }                
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.toString(), e);
        }
    }

    public synchronized Iterator<Gene> execute(X region) {
        if (!region.getGenome().equals(genome) || ps == null) {
            close();
            setGenome(region.getGenome(), tablename);
        }
        int offset = 1;
        try {
            String chr = region.getChrom();
            if (!chr.matches("^(chr|scaffold).*")) {
                chr = "chr" + chr;
            }
            ps.setString(offset++, chr);
            if (upstream != 0 || downstream != 0) {
                offset = bindUpstreamOverlap(ps, offset, region);
            } else if (closestN == 0) {
                offset = bindOrfOverlap(ps, offset, region);
            }
            if (closestN != 0) {
                offset = bindClosestOrder(ps, offset, region);
                ps.setInt(offset++, closestN);
            } 
            Iterator<Gene> results = parseResults(ps);            
            return results;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new DatabaseException("Couldn't get UCSC RefGenes",ex);
        }
    }
    public synchronized Iterator<Gene> getAll() throws SQLException {
            return parseResults(getallps);  
        }
    public synchronized Iterator<Gene> byName(String name) {
        try {
            nameps.setString(1,name);
            if (symboltable != null) {
                nameps.setString(2,name);
            }
            Iterator<Gene> results = parseResults(nameps);
            return results;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new DatabaseException("Couldn't get UCSC RefGenes",ex);
        }
    }

    /* common method for execute() and byName().  The prepared statement should have all
       variables be bound and be ready to execute.  IT should return fields from
       the genes table */
    public synchronized Iterator<Gene> parseResults(PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();
        ArrayList<Gene> results = new ArrayList<Gene>();
        while (rs.next()) {
            String chr = rs.getString(2);
            chr = chr.replaceFirst("^chr","");
            Gene g = null;
            if(wantsExons) { 
                char strand = rs.getString(3).charAt(0);
                if (flipstrand) {
                    strand = strand == '+' ? '-' : '+';
                }                
                ExonicGene exonicGene = new ExonicGene(genome,
                                                       chr,
                                                       rs.getInt(4)+1,  //Correction for 0-based starts
                                                       rs.getInt(5),
                                                       rs.getString(1),
                                                       rs.getString(1),
                                                       strand,
                                                       "RefGene");
                g = exonicGene;
                int exonCount = rs.getInt(6);
                if(exonCount >= 1) {
                    try { 
                        Blob startBlob = rs.getBlob(7);
                        BufferedReader br = new BufferedReader(new InputStreamReader(startBlob.getBinaryStream()));
                        String startline = br.readLine(); 
                        String[] startArray = startline.split("\\D+");
                        br.close();
                        
                        Blob endBlob = rs.getBlob(8);
                        br = new BufferedReader(new InputStreamReader(endBlob.getBinaryStream()));
                        String endline = br.readLine();
                        String[] endArray = endline.split("\\D+");
                        br.close();
                        
                        for(int i = 0; i < startArray.length && i < endArray.length; i++) { 
                            if(startArray[i].length() > 0 && endArray[i].length() > 0) { 
                                int start = Integer.parseInt(startArray[i])+1; //Correction for 0-based starts in UCSC tables
                                int end = Integer.parseInt(endArray[i]);
                                
                                try {
                                    exonicGene.addExon(start, end);
                                } catch(IllegalArgumentException iae) {
                                    System.err.println("Gene is " + exonicGene.toString());
                                    System.err.println("Start line: \"" + startline + "\", " + startArray[i]);
                                    System.err.println("End line: \"" + endline + "\", " + endArray[i]);
                                    iae.printStackTrace(System.err);
                                }
                            }
                        }
                        
                    } catch(IOException ie) { 
                        ie.printStackTrace(System.err);
                    }
                }                
            } else { 
                char strand = rs.getString(3).charAt(0);
                if (flipstrand) {
                    strand = strand == '+' ? '-' : '+';
                }
                g = new Gene(genome,
                             chr,
                             rs.getInt(4)+1, //Correction for 0-based starts
                             rs.getInt(5),
                             rs.getString(1),
                             rs.getString(1),
                             strand,
                             "RefGene");
            }
            if(getgenesym != null && wantsymbol) { 
                getgenesym.setString(1, g.getID());
                ResultSet gsrs = getgenesym.executeQuery();
                if(gsrs.next()) { 
                    g.setName(gsrs.getString(1)); 
                }
                while(gsrs.next()) { 
                    g.addAlias(gsrs.getString(1));
                }
                gsrs.close();
            }
            if (wantalias && getalias != null) {
                try {
                    getalias.setString(1,rs.getString(1));                    
                    ResultSet aliasresults = getalias.executeQuery();
                    while (aliasresults.next()) {
                        g.addAlias(aliasresults.getString(1));
                    }
                    aliasresults.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    wantalias = false;
                } 
            }
            results.add(g);
            
        }
        rs.close();
        return results.iterator();
    }

    /* 
     * below here are private methods use to generate the SQL
     */


    private String getFields() {
        String query = String.format("select name, chrom, strand, %s, %s",
                                     getStartField(), getEndField());
        if(wantsExons) { query += ", exonCount, exonStarts, exonEnds"; }        
        return query;
    }

    /* returns an order clause to get the closest
       gene to the input region
    */
    private String getClosestOrder() {
        if (toBoundary == TOSTART) {
            return String.format(" order by least(abs(? - if(strand = '+', %s, %s)),abs(? - if(strand = '+', %s, %s))) ",
                                 getStartField(), getEndField(), getStartField(), getEndField());
        } else if (toBoundary == TOEND) {
            return String.format(" order by least(abs(? - if(strand = '-', %s, %s)),abs(? - if(strand = '-', %s, %s))) ",
                                 getStartField(), getEndField(), getStartField(), getEndField());
        } else {
            return String.format(" order by least(abs(%s - ?), abs(? - %s),abs(%s - ?), abs(? - %s)) ",
                                 getStartField(), getEndField(), getStartField(), getEndField());
        }
    }
    private int bindClosestOrder(PreparedStatement ps, int offset, X region) throws SQLException {
        /* use floats here because otherwise MySQL does stupid things when subtracting from the
           unsigned ints in the database */
        if (toBoundary == TOSTART || toBoundary == TOEND) {
            ps.setFloat(offset++, region.getEnd());
            ps.setFloat(offset++, region.getStart());
        } else {
            ps.setFloat(offset++, region.getEnd());
            ps.setFloat(offset++, region.getEnd());
            ps.setFloat(offset++, region.getStart());
            ps.setFloat(offset++, region.getStart());
        }
        return offset;
    }

    private String getUpstreamOverlap() {
        String query = String.format("((strand = '+' and ((%s <= ? and %s >= ?) or (%s >= ? and %s <= ?))) or " +
                                     " (strand = '-' and ((%s <= ? and %s >= ?) or (%s >= ? and %s <= ?))))",
                                     getStartField(), getStartField(), getStartField(), getStartField(),
                                     getEndField(), getEndField(), getEndField(), getEndField());
        return query;
    }
    private int bindUpstreamOverlap(PreparedStatement ps, int offset, X region) throws SQLException {
        ps.setInt(offset++,region.getStart() + upstream);
        ps.setInt(offset++,region.getStart() - downstream);
        ps.setInt(offset++,region.getStart() + upstream);
        ps.setInt(offset++,region.getEnd() + upstream);               
        ps.setInt(offset++,region.getStart() + downstream);
        ps.setInt(offset++,region.getStart() - upstream);
        ps.setInt(offset++,region.getStart() + downstream);
        ps.setInt(offset++,region.getEnd() + downstream);
        return offset;
    }

    private String getOrfOverlap() {
        return String.format(" ((%s <= ? and %s >= ?) or (%s >= ? and %s <= ?))",
                             getStartField(), getEndField(),getStartField(), getStartField());
    }
    private int bindOrfOverlap(PreparedStatement ps, int offset, X region) throws SQLException {
        ps.setInt(offset++,region.getStart());
        ps.setInt(offset++,region.getStart());
        ps.setInt(offset++,region.getStart());
        ps.setInt(offset++,region.getEnd());
        return offset;
    }

    
    /*
     * Methods for satisfying the psrg.echo.SelfDescribingVerb interface.
     */

    public EchoType getInputClass() {
        return new ClassType(Region.class);
    }

    public EchoType getOutputClass() {
        return new ClassType(Gene.class);
    }
    
    private static final EchoType[] paramClasses = { 
    	new ClassType(Genome.class), new ClassType(String.class) };
    private static final String[] paramNames = { "Genome", "GeneSource" };

    private static final EchoType[] inputClasses = { new ClassType(Region.class) };
    private static final String[] inputNames = { "Regions" };
    
    private static final String[] defConstNames = { "GeneSource" };
    private static final SelfDescribingConstant[] defConsts = { new ValueWrapper("refGene") };

    public EchoType[] getParameterClasses() { return paramClasses; }
    public String[] getParameterNames() { return paramNames; }
    
    public EchoType[] getInputClasses() { return inputClasses; }
    public String[] getInputNames() { return inputNames; }

    public void init(Map<String, Object> params) {
        Genome g = (Genome)params.get("Genome");
        String t = (String)params.get("GeneSource");
        setGenome(g, t);
    }

    public String[] defaultConstantNames() {
        return defConstNames;
    }

    public SelfDescribingConstant[] defaultConstants() {
        return defConsts;
    }
    /* command line driver for retrieving genes */
    public static void main(String args[]) throws Exception {
        String specname = null, genomename = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--species")) {
                String pieces[] = args[++i].split(";");
                specname = pieces[0];
                genomename = pieces[1];
            }
        }        
        if (specname == null || genomename == null) {
            throw new RuntimeException("Must supply --species 'species;genome'");
        }

        Organism org;
        Genome genome;
        org = new Organism(specname);
        genome = org.getGenome(genomename);
        RefGeneGenerator gen = new RefGeneGenerator(genome);
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--up")) {
                gen.setUpstream(Integer.parseInt(args[++i]));
            }
            if (args[i].equals("--down")) {
                gen.setDownstream(Integer.parseInt(args[++i]));
            }
            if (args[i].equals("--closestn")) {
                gen.setClosestN(Integer.parseInt(args[++i]));
            }
            if (args[i].equals("--tostart")) {
                gen.setToStart();
            }
            if (args[i].equals("--toend")) {
                gen.setToEnd();
            }
            if (args[i].equals("--toeitherend")) {
                gen.setToEitherEnd();
            }
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--gene")) {
                Iterator<Gene> iter = gen.byName(args[++i]);
                while (iter.hasNext()) {
                    System.out.println(iter.next().toString());
                }
            }
            if (args[i].equals("--region")) {
                Region r = Region.fromString(genome,args[++i]);
                Iterator<Gene> iter = gen.execute(r);
                while (iter.hasNext()) {
                    System.out.println(iter.next().toString());
                }

            }
        }

    }
}
