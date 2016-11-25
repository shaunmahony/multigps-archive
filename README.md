multigps-archive
================

MultiGPS & SeqCode have moved!
------------------------------
The most up-to-date development of MultiGPS and SeqCode are hosted here:
* MultiGPS: https://github.com/seqcode/multigps
* SeqCode: https://github.com/seqcode/seqcode-core

The current repo (multigps-archive) is being left in place to serve as an archive of the original version of MultiGPS. The source code link in the initial publication (https://www.ncbi.nlm.nih.gov/pubmed/24675637) also points here. However, no further development or bug fixes will occur in this repo. Please use the more recent versions linked above if you are using or testing MultiGPS.

What's in this archive?
-----------------------
This repository includes code for the first release (version 0.5, Mar 2014) of MultiGPS and some other code from Shaun Mahony's lab at Penn State University. 

The MultiGPS code is located under edu.psu.compbio.seqcode.projects.multigps, and the main method is edu.psu.compbio.seqcode.projects.multigps.analysis.MultiGPS.

Much of the gse package (edu.psu.compbio.seqcode.gse) is based on GSE, the "Genomic Spatial Events" codebase from the Gifford lab at CSAIL, MIT (package: edu.mit.csail.cgs.gse). 
GSE is outlined in this publication: http://www.ncbi.nlm.nih.gov/pubmed/18229714
The core classes of GSE were built by Tim Danford and Alex Rolfe. Later additions were also contributed by Bob Altshuler, Shaun Mahony, Yuchun Guo, Chris Reeder, and Giorgos Papachristoudis.
This version of GSE was branched from the main MIT code base on 24th Oct 2012. Initial refactoring deleted a number of outdated subpackages, updated the naming spaces for compatibility with the Mahony lab servers at PSU, and aimed to remove any interactions with Oracle databases in favor of mysql.
