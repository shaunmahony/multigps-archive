/*
 * Created on Feb 19, 2007
 *
 * TODO 
 * 
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.psu.compbio.seqcode.gse.ewok.verbs;

import java.util.*;

public interface Generator<X> {
    public Iterator<X> execute();
}
