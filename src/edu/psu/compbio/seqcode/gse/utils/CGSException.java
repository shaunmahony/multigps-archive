package edu.psu.compbio.seqcode.gse.utils;

/**
 * This class can be used to signal error cases in code in the various modules 
 * of our codebase 
 * @author Bob
 *
 */
public class CGSException extends Exception {

	/**
	 * Constructs a <code>CGSException</code> with no detail message.
	 */
	public CGSException() {
		super();
	}

	/**
	 * Constructs a <code>CGSDriveException</code> with specified message.
	 * @param   msg   the detail message.
	 */
	public CGSException(String msg) {
		super(msg);
	}

	/**
	 * Constructs a <code>CGSException</code> with an Exception Object.
	 * @param   e   the Exception Object.
	 */
	public CGSException(Exception e) {
		super(e);
	}
	
	
	/**
	 * Constructs a <code>CGSException</code> with specified message and an Exception Object.
	 * @param msg	the detail message.
	 * @param e		the Exception Object.
	 */
	public CGSException(String msg, Exception e) {
		super(msg, e);
	}
}
