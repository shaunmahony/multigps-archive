package edu.psu.compbio.seqcode.gse.utils;

import java.lang.*;
import java.io.*;
import java.util.*;

public interface RealValuedFunction { 
	public double eval(double input);
	public String getName();
}

//