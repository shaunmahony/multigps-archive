package edu.psu.compbio.seqcode.gse.seqview.components;

import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;

import edu.psu.compbio.seqcode.gse.datasets.binding.BindingEvent;
import edu.psu.compbio.seqcode.gse.datasets.binding.BindingScan;
import edu.psu.compbio.seqcode.gse.datasets.binding.BindingScanLoader;
import edu.psu.compbio.seqcode.gse.datasets.expression.Experiment;
import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.ewok.verbs.ChromRegionIterator;
import edu.psu.compbio.seqcode.gse.ewok.verbs.binding.BindingExpander;
import edu.psu.compbio.seqcode.gse.utils.EventSource;
import edu.psu.compbio.seqcode.gse.utils.Listener;
import edu.psu.compbio.seqcode.gse.utils.database.UnknownRoleException;
import edu.psu.compbio.seqcode.gse.viz.components.SelectionEvent;


public class ExprExperimentSelectFrame 
	extends JFrame 
	implements EventSource<SelectionEvent<Experiment>>, ActionListener {

    private Genome genome;
    private ExprExperimentSelectPanel bssp;
    private JButton ok, cancel;    
    private EventSource.Default<SelectionEvent<Experiment>> src;

    public ExprExperimentSelectFrame(Genome g, RegionList list) {
        super();

        src = new EventSource.Default<SelectionEvent<Experiment>>();
        
        bssp = new ExprExperimentSelectPanel();
        if(g != null ) { 
        	try {
        		bssp.setGenome(g);
        	} catch (Exception ex) {
        		ex.printStackTrace();
        		this.dispose();
        	}
        }
        
        this.genome = g;
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        Dimension buttonSize = new Dimension(30,20);
        ok = new JButton("OK");
        cancel = new JButton("Cancel");

        ok.setMaximumSize(buttonSize);
        cancel.setMaximumSize(buttonSize);
        ok.addActionListener(this);
        cancel.addActionListener(this);
        
        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        Container content = getContentPane();
        content.setLayout(new BorderLayout());
        content.add(buttonPanel,BorderLayout.SOUTH);
        content.add(bssp,BorderLayout.CENTER);
        setSize(500,400);
        setLocation(50,50);
        setVisible(true);
    }

    public void actionPerformed (ActionEvent e) {
    	SelectionEvent evt = null;
        if (e.getSource() == ok) {
        	evt = new SelectionEvent<Experiment>(this, bssp.getSelected());
        } else if (e.getSource() == cancel) {
        	evt = new SelectionEvent<Experiment>(this, new LinkedList<Experiment>());
        }
        src.fireEvent(evt);
        this.dispose();
    }

	public void addEventListener(Listener<SelectionEvent<Experiment>> el) {
		src.addEventListener(el);
	}

	public boolean hasListeners() {
		return src.hasListeners();
	}

	public void removeEventListener(Listener<SelectionEvent<Experiment>> el) {
		src.removeEventListener(el);
	}
}

