/*
 * Author: tdanford
 * Date: Sep 29, 2008
 */
package edu.psu.compbio.seqcode.gse.utils.models;


public interface Modeleable {
	public Class getModelClass();
	public Model asModel();
}
