/*
 * Created on Apr 3, 2007
 */
package edu.psu.compbio.seqcode.gse.tools.binding;

import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import edu.psu.compbio.seqcode.gse.datasets.binding.*;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.utils.Listener;
import edu.psu.compbio.seqcode.gse.utils.NotFoundException;
import edu.psu.compbio.seqcode.gse.utils.database.UnknownRoleException;
import edu.psu.compbio.seqcode.gse.viz.components.BindingScanParamPanel;
import edu.psu.compbio.seqcode.gse.viz.components.BindingScanSelectPanel;
import edu.psu.compbio.seqcode.gse.viz.utils.GenomeSelectPanel;

public class GUIBindingScanManager extends JFrame implements Listener<ActionEvent> {
 
	private static final long serialVersionUID = 1L;

	public static void main(String[] args) { 
        new GUIBindingScanManager();
    }

    private BindingScanSelectPanel bindingPanel;
    private GenomeSelectPanel genomePanel;
    private Genome currentGenome;
    
    private BindingScanLoader loader;
    
    private JPanel buttonPanel;
    private JButton paramsButton, deleteButton;
    
    public GUIBindingScanManager() { 
        super("GUI Binding Scan Manager");
        genomePanel = new GenomeSelectPanel("Mus musculus", "mm8");
        currentGenome = getGenome();
        bindingPanel = null;
        try {
            bindingPanel = new BindingScanSelectPanel();
            bindingPanel.setGenome(currentGenome);
        } catch (UnknownRoleException e) {
            e.printStackTrace();
            throw new IllegalStateException("Unknown genome", e);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Unknown genome", e);
        }
        
        loader = bindingPanel.getBindingLoader();
        
        genomePanel.addEventListener(this);
        
        Container c = (Container)getContentPane();
        c.setLayout(new BorderLayout());
        
        buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        
        buttonPanel.add(paramsButton = new JButton("Params"));
        buttonPanel.add(deleteButton = new JButton("Delete"));
        
        paramsButton.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                showParams();
            }
        });
        
        deleteButton.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                deleteSelected();
            }
        });
        
        c.add(genomePanel, BorderLayout.NORTH);
        c.add(bindingPanel, BorderLayout.CENTER);
        c.add(buttonPanel, BorderLayout.SOUTH);
        
        setVisible(true);
        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
    
    private void deleteSelected() { 
        Collection<BindingScan> scans = bindingPanel.getSelected();
        for(BindingScan scan : scans) { 
            try {
                loader.deleteScan(scan.getDBID());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        bindingPanel.clearSelected();
        bindingPanel.filter();
    }
    
    private void showParams() { 
        Collection<BindingScan> scans = bindingPanel.getSelected();
        for(BindingScan scan : scans) { 
            try {
                Map<String,String> params = loader.loadParams(scan);
                new BindingScanParamPanel.Frame(params);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private Genome getGenome() { 
        try {
            Organism og = Organism.getOrganism(genomePanel.getSpecies());
            Genome g = og.getGenome(genomePanel.getGenome());
            return g;
        } catch (NotFoundException e1) {
            e1.printStackTrace();
            return null;
        }   
    }

    public void eventRegistered(ActionEvent e) {
        currentGenome = getGenome();
        bindingPanel.setGenome(currentGenome);
    }
}
