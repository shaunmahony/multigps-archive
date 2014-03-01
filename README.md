seqcode
=======

SeqCode: Java code for the analysis of high-throughput sequencing data

This repository includes projects that are released by Shaun Mahony's lab at Penn State University. 

The MultiGPS code is located under edu.psu.compbio.seqcode.projects.multigps, and the main method is edu.psu.compbio.seqcode.projects.multigps.analysis.MultiGPS.
MultiGPS is currently on version 0.5

Much of the gse package (edu.psu.compbio.seqcode.gse) is based on GSE, the "Genomic Spatial Events" codebase from the Gifford lab at CSAIL, MIT (package: edu.mit.csail.cgs.gse). 
GSE is outlined in this publication: http://www.ncbi.nlm.nih.gov/pubmed/18229714
The core classes of GSE were built by Tim Danford and Alex Rolfe. Later additions were also contributed by Bob Altshuler, Shaun Mahony, Yuchun Guo, Chris Reeder, and Giorgos Papachristoudis.
This version of GSE was branched from the main MIT code base on 24th Oct 2012. Initial refactoring deleted a number of outdated subpackages, updated the naming spaces for compatibility with the Mahony lab servers at PSU, and aimed to remove any interactions with Oracle databases in favor of mysql.
