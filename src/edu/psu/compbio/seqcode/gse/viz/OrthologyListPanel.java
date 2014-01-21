/*
 * Created on Oct 20, 2005
 */
package edu.psu.compbio.seqcode.gse.viz;

import java.util.*;
import java.sql.SQLException;
import java.io.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

import edu.psu.compbio.seqcode.gse.datasets.orthology.OrthologyLoader;
import edu.psu.compbio.seqcode.gse.datasets.orthology.OrthologyMapping;
import edu.psu.compbio.seqcode.gse.datasets.orthology.OrthologyPanel;
import edu.psu.compbio.seqcode.gse.datasets.orthology.TotalOrthologyMapping;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.utils.database.UnknownRoleException;
import edu.psu.compbio.seqcode.gse.viz.utils.PanelFrame;

/**
 * @author tdanford
 */
public class OrthologyListPanel extends JPanel implements edu.psu.compbio.seqcode.gse.utils.Closeable {
    
    public static void main(String[] args) { 
        try {
            OrthologyListPanel olp = new OrthologyListPanel();
            PanelFrame pf = new PanelFrame("Orthology List", olp);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (UnknownRoleException e) {
            e.printStackTrace();
        }
    }
    
    private OrthologyLoader loader;
    
    private JList list;
    private DefaultListModel model;

    public OrthologyListPanel() throws SQLException, UnknownRoleException {
        super();
        loader = new OrthologyLoader();
        setLayout(new BorderLayout());
        
        model = new DefaultListModel();
        list = new JList(model);
        
        add(new JScrollPane(list), BorderLayout.CENTER);
        updateList();
        
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) { 
                    int row = list.locationToIndex(new Point(e.getX(), e.getY()));
                    OrthologyMapping mapping = (OrthologyMapping)model.get(row);
                    TotalOrthologyMapping total = loader.loadTotalOrthologyMapping(mapping);
                    OrthologyPanel op = new OrthologyPanel(total);
                    PanelFrame pf = new PanelFrame("Orthology Mapping", op);
                }
            }
        });
    }
    
    public void updateList() { 
        model.clear();
        Collection<OrthologyMapping> mappingList = loader.loadAllMappings();
        for(OrthologyMapping om : mappingList) { 
            model.addElement(om);
        }
    }
    
    public Collection<OrthologyMapping> getSelectedMappings() { 
        LinkedList<OrthologyMapping> lst = new LinkedList<OrthologyMapping>();
        int[] inds = list.getSelectedIndices();
        for(int i = 0; i < inds.length; i++) { 
            lst.addLast((OrthologyMapping)model.elementAt(inds[i]));
        }
        return lst;
    }

    /* (non-Javadoc)
     * @see edu.psu.compbio.seqcode.gse.utils.Closeable#close()
     */
    public void close() {
        loader.close();
    }

    /* (non-Javadoc)
     * @see edu.psu.compbio.seqcode.gse.utils.Closeable#isClosed()
     */
    public boolean isClosed() {
        return loader.isClosed();
    }
    
}
