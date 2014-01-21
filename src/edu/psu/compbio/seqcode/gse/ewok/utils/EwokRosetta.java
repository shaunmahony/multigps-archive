/*
 * Created on Mar 3, 2006
 */
package edu.psu.compbio.seqcode.gse.ewok.utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.io.*;


import edu.psu.compbio.seqcode.gse.datasets.binding.BindingEvent;
import edu.psu.compbio.seqcode.gse.datasets.binding.BindingExtent;
import edu.psu.compbio.seqcode.gse.datasets.binding.ProbedBindingEvent;
import edu.psu.compbio.seqcode.gse.datasets.chipchip.MSPProbe;
import edu.psu.compbio.seqcode.gse.datasets.chipchip.Probe;
import edu.psu.compbio.seqcode.gse.datasets.general.Region;
import edu.psu.compbio.seqcode.gse.datasets.locators.*;
import edu.psu.compbio.seqcode.gse.ewok.*;
import edu.psu.compbio.seqcode.gse.ewok.nouns.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.binding.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.motifs.*;
import edu.psu.compbio.seqcode.gse.ewok.verbs.probers.MSPImmediateProbeGenerator;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseFactory;
import edu.psu.compbio.seqcode.gse.utils.database.UnknownRoleException;

/**
 * @author tdanford
 */
public class EwokRosetta extends EwokBase implements EwokBinding<MSPLocator,MSPProbe> {
    
    public EwokRosetta() {
        super();
    }
    
    public EwokRosetta(String sp, String gn) {
        super(sp, gn);
    }
    
    public RegionProber<MSPProbe> getProber(MSPLocator loc) { return getRosettaProber(loc); }
    
    public Expander<Region,BindingExtent> getPeakCaller(MSPLocator loc, double[] params) {
        return new CustomMSPBindingGenerator(loc.createObject());
    }
    
    public RegionProber<MSPProbe> getRosettaProber(MSPLocator loc) { 
        MSPImmediateProbeGenerator gen = new MSPImmediateProbeGenerator(genome, loc);
        RegionProber<MSPProbe> prober = new RegionProber.Wrapper<MSPProbe>(gen);
        return prober;
    }
    
    public void printRegionProbes(Iterator<Region> regions, RegionProber<MSPProbe> prober) { 
        Iterator<Pair<Region,Iterator<MSPProbe>>> itr = new ExpanderPairIterator<Region,MSPProbe>(prober, regions);
        while(itr.hasNext()) { 
            Pair<Region,Iterator<MSPProbe>> p = itr.next();
            Region r = p.getFirst();
            String regStr = p.getFirst().getChrom() + ":" + p.getFirst().getStart() + "-" + p.getFirst().getEnd();
            Iterator<MSPProbe> pitr = p.getLast();
            
            System.out.println("\n**** " + p.getFirst() + "\t" + regStr);
            while(pitr.hasNext()) { 
                System.out.println("\t" + pitr.next());
            }
        }
    }
    
    public void printRegionBindingEvents(Iterator<Region> regions, PeakCaller caller) { 
        Iterator<Pair<Region,Iterator<BindingEvent>>> itr = new ExpanderPairIterator<Region,BindingEvent>(caller, regions);
        while(itr.hasNext()) { 
            Pair<Region,Iterator<BindingEvent>> p = itr.next();
            Iterator<BindingEvent> beItr = p.getLast(); 
			String regStr = p.getFirst().getChrom() + ":" + p.getFirst().getStart() + "-" + p.getFirst().getEnd();
            if(beItr.hasNext()) { 
                System.out.println("\n+++ " + p.getFirst() + "\t" + regStr);
                while(beItr.hasNext()) {
                    BindingEvent be = beItr.next();
                    System.out.println("\t" + be);
                    
                    ProbedBindingEvent pbe = (ProbedBindingEvent)be;
                    Collection<Probe> probes = pbe.getProbes();
                    for(Probe pb : probes) { 
                        System.out.println("\t\t" + pb.toString());
                    }
                }
            } else { 
                System.out.println("\n--- " + p.getFirst() + "\t" + regStr);
			}
        }
    }
    
    public void printRegionBindingAnnotation(Iterator<Region> regions, PeakCaller caller) {
        
        BindingFilter bf = new BindingFilter(caller);
        Iterator<Pair<Region,Integer>> annRegions = 
            new MapperIterator<Region,Pair<Region,Integer>>(new FilterValueMapper<Region,Region>(bf), regions);
        
        while(annRegions.hasNext()) {
            Pair<Region,Integer> p = annRegions.next();
			String regStr = p.getFirst().getChrom() + ":" + p.getFirst().getStart() + "-" + p.getFirst().getEnd();
			System.out.println(p.getLast() + "\t" + p.getFirst() + "\t" + regStr);
        }
    }    
    

    /* (non-Javadoc)
     * @see edu.psu.compbio.seqcode.gse.analysis.ewok.EwokBinding#getAllLocators()
     */
    public Collection<MSPLocator> getAllLocators() throws SQLException, UnknownRoleException {
        int species = getGenome().getSpeciesDBID();
        
        LinkedList<MSPLocator> locs = new LinkedList<MSPLocator>();
        java.sql.Connection c = DatabaseFactory.getConnection(ExptLocator.dbRole);
        
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("select name, version from rosettaanalysis where species="+species);

        while(rs.next()) { 
            String name2 = rs.getString(1);
            String version2 = rs.getString(2);
            MSPLocator locb = new MSPLocator(getGenome(), name2, version2);
            locs.addLast(locb);
        }

        rs.close();
        s.close();
        DatabaseFactory.freeConnection(c);
        
        return locs;
    }

    /* (non-Javadoc)
     * @see edu.psu.compbio.seqcode.gse.analysis.ewok.EwokBinding#getBase()
     */
    public EwokBase getBase() {
        return this;
    }   
}
