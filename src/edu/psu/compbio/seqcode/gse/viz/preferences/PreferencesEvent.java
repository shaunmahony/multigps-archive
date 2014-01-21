package edu.psu.compbio.seqcode.gse.viz.preferences;

import java.util.EventObject;

public class PreferencesEvent extends EventObject {
	
	private PreferencesModel model;

	public PreferencesEvent(Object src, PreferencesModel m) {
		super(src);
		model = m;
	}
	
	public PreferencesModel getModel() { return model; }

}
