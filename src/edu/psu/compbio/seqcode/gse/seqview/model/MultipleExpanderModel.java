package edu.psu.compbio.seqcode.gse.seqview.model;

import java.util.*;

import edu.psu.compbio.seqcode.gse.ewok.verbs.Expander;
import edu.psu.compbio.seqcode.gse.utils.*;

public class MultipleExpanderModel<IN,OUT> extends SeqViewModel implements Runnable {

    private boolean newinput;
    private LinkedList<Expander<IN,OUT>> expanders;
    private IN input;
    private ArrayList<OUT> result;

    public MultipleExpanderModel() {
        super();
        expanders = new LinkedList<Expander<IN,OUT>>();
        newinput = false;
    }

    public synchronized void run() {
        while(keepRunning()) {
            try {
                if (!newinput) {
                    wait();
                }
            } catch (InterruptedException ex) {

            }
            if (newinput) {
                try {
                    result = new ArrayList<OUT>();
                    for(Expander<IN,OUT> expander : expanders) { 
                        Iterator<OUT>iter = expander.execute(input);
                        while (iter.hasNext()) {
                            result.add(iter.next());
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                newinput = false;
                notifyListeners();
            }
        }
    }
    
    public synchronized void addExpander(Expander<IN,OUT> exp) { 
        expanders.addLast(exp);
    }
    
    public synchronized void setInput(IN i) {
        if (newinput == false) {
            if (!i.equals(input)) {
                input = i;
                newinput = true;
            } else {
                notifyListeners();
            }
        }
    }

    public Iterator<OUT> getResults() {
        if (newinput == false) {
            return result.iterator();
        } else {
            // or should we throw some exception?
            return null;
        }
    }

    public boolean isReady() {return !newinput;}
}
