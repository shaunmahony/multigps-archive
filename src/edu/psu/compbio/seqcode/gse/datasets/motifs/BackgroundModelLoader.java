/**
 * 
 */
package edu.psu.compbio.seqcode.gse.datasets.motifs;

import java.io.*;
import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import edu.psu.compbio.seqcode.gse.datasets.species.Genome;
import edu.psu.compbio.seqcode.gse.datasets.species.Organism;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.*;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseException;
import edu.psu.compbio.seqcode.gse.utils.database.DatabaseFactory;
import edu.psu.compbio.seqcode.gse.utils.io.motifs.BackgroundModelIO;

/**
 * @author rca
 * Code for database interaction involving background models
 */

/*
~/psrg/software/meme-4.3.0/bin/fasta-get-markov -m 0 < hg19.fa | awk '{print $2 FS $1}' | grep -v order > hg19.zero

java edu.psu.compbio.seqcode.gse.datasets.motifs.BackgroundModelLoader --species "$HS;hg19" --bgname 'whole genome zero order' --bgtype FREQUENCY --bgfile hg19.zero
where hg19.zero is 
.2 A
.3 C
.2 T
.3 G 
*/
public class BackgroundModelLoader {

  private static Logger logger = Logger.getLogger(BackgroundModelLoader.class);
  
  public static final String FREQUENCY_TYPE_STRING = "FREQUENCY";
  public static final String MARKOV_TYPE_STRING = "MARKOV";
  
  /**
   * All the select SQLs used by this class
   */
  private static final String SQL_GET_MODEL_ID = "select id from background_model where name = ? and max_kmer_len = ? and model_type = ?";
  
  private static final String SQL_GET_MAP_ID = "select id from background_genome_map where bg_model_id = ? and genome_id = ?";
  
  private static final String SQL_GET_ALL_MODELS = "select id, name, max_kmer_len, model_type from background_model";
  
  private static final String SQL_GET_GENOMES = "select genome_id from background_genome_map where bg_model_id = ?";
    
  private static final String SQL_HAS_COUNTS = "select map.has_counts from background_model bm, background_genome_map map where map.bg_model_id = bm.id and map.id = ?";
  
  private static final String SQL_GET_METADATA_BY_MODEL_ID = "select name, max_kmer_len, model_type from background_model where id = ?";
  
  private static final String SQL_GET_METADATA_CORE = 
    "select map.id, map.genome_id, map.bg_model_id, bm.name, bm.max_kmer_len, bm.model_type, map.has_counts"
    + " from background_model bm, background_genome_map map"
    + " where bm.id = map.bg_model_id";
  private static final String SQL_GET_METADATA_ORDER_BY = " order by bm.name, bm.max_kmer_len, map.genome_id";
  
  
  private static final String SQL_GET_METADATA_MAP_ID = " and map.id = ?";
  private static final String SQL_GET_METADATA_GENOME_ID = " and map.genome_id = ?";
  private static final String SQL_GET_METADATA_MODEL_ID = " and map.bg_model_id = ?";
  private static final String SQL_GET_METADATA_NAME = " and bm.name = ?";
  private static final String SQL_GET_METADATA_KMER_LEN = " and bm.max_kmer_len = ?";
  private static final String SQL_GET_METADATA_MODEL_TYPE = " and bm.model_type = ?";

  private static final int SQL_GET_METADATA_CORE_MAP_ID_INDEX = 1;
  private static final int SQL_GET_METADATA_CORE_GENOME_ID_INDEX = 2;
  private static final int SQL_GET_METADATA_CORE_MODEL_ID_INDEX = 3;
  private static final int SQL_GET_METADATA_CORE_NAME_INDEX = 4;
  private static final int SQL_GET_METADATA_CORE_KMER_LEN_INDEX = 5;
  private static final int SQL_GET_METADATA_CORE_MODEL_TYPE_INDEX = 6;
  private static final int SQL_GET_METADATA_CORE_HAS_COUNTS_INDEX = 7;
  
  
  
  
  private static final String SQL_GET_MODEL_BY_ID = "select bggm_id, kmer, probability, count from background_model_cols where bggm_id = ? order by bggm_id, length(kmer), kmer";
  private static final int SQL_GET_MODEL_BY_ID_MAP_ID_INDEX = 1;
  private static final int SQL_GET_MODEL_BY_ID_KMER_INDEX = 2;
  private static final int SQL_GET_MODEL_BY_ID_PROB_INDEX = 3;
  private static final int SQL_GET_MODEL_BY_ID_COUNT_INDEX = 4;
  
  private static final String SQL_GET_MODEL_CORE = 
    "select map.id, map.genome_id, bm.name, bm.max_kmer_len, bm.id, bgmc.kmer, bgmc.probability, bgmc.count"
    + " from background_model bm, background_genome_map map, background_model_cols bgmc"
    + " where bm.id = map.bg_model_id and bgmc.bggm_id = map.id";
  private static final String SQL_GET_MODEL_ORDER_BY = " order by bm.name, bm.max_kmer_len, map.genome_id, length(bgmc.kmer), bgmc.kmer";
  private static final int SQL_GET_MODEL_CORE_MAP_ID_INDEX = 1;
  private static final int SQL_GET_MODEL_CORE_GENOME_ID_INDEX = 2;
  private static final int SQL_GET_MODEL_CORE_NAME_INDEX = 3;
  private static final int SQL_GET_MODEL_CORE_KMERLEN_INDEX = 4;
  private static final int SQL_GET_MODEL_CORE_MODEL_ID_INDEX = 5;
  private static final int SQL_GET_MODEL_CORE_KMER_INDEX = 6;
  private static final int SQL_GET_MODEL_CORE_PROB_INDEX = 7;
  private static final int SQL_GET_MODEL_CORE_COUNT_INDEX = 8;
  
  private static final String SQL_GET_MODEL_MAP_ID = " and map.id = ?";

  private static final String SQL_GET_MODEL_GENOME_ID = " and map.genome_id = ?";

  private static final String SQL_GET_MODEL_MODEL_ID = " and bm.id = ?";

  private static final String SQL_GET_MODEL_NAME = " and bm.name = ?";
  
  private static final String SQL_GET_MODEL_KMER_LEN = " and bm.max_kmer_len = ?";  
  
  private static final String SQL_GET_MODEL_TYPE = " and bm.model_type = ?";  

  private static final String SQL_GET_MODEL_HAS_COUNTS = " and map.has_counts = ?";  

  /**
   * @see getBackgroundModelID(String, int, String, Connection)
   * Creates a database connection for the query
   */
  public static Integer getBackgroundModelID(String name, int kmerLen, String modelType) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundModelID(name, kmerLen, modelType, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Look for a model with this name, maxKmerLen, and type and return its ID. 
   * 
   * @param name the name of the model
   * @param kmerLen the length of the longest kmer in the model
   * @param dbModelType the type of the model (FREQUENCY or MARKOV)
   * @param cxn an open database connection
   * @return the ID of the model, or null if there's no match
   * @throws SQLException
   */
  public static Integer getBackgroundModelID(String name, int kmerLen, String modelType, Connection cxn) throws SQLException {
    PreparedStatement getModelID = null;
    ResultSet rs = null;    
    try {
      getModelID = cxn.prepareStatement(SQL_GET_MODEL_ID);
      getModelID.setString(1, name);
      getModelID.setInt(2, kmerLen);
      getModelID.setString(3, modelType);
      rs = getModelID.executeQuery();

      if (rs.next()) {
        Integer modelID = rs.getInt(1);
        return modelID;
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModelID != null) {
        getModelID.close();
      }
    }
  }
  
  
  /**
   * @see getBackgroundGenomeMapID(int, int, Connection)
   * Creates a database connection for the query
   */
  public static Integer getBackgroundGenomeMapID(int bgModelID, int genomeID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundGenomeMapID(bgModelID, genomeID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Look for an entry in the background model genome map for the specified
   * background model and genome, and return its ID
   * @param bgModelID the id of the model to look up
   * @param genomeID a genome for which there may be an instance of the model
   * @param cxn an open database connection
   * @return the background genome map ID of the model, or null if there's no match
   * @throws SQLException
   */
  public static Integer getBackgroundGenomeMapID(int bgModelID, int genomeID, Connection cxn) throws SQLException {
    PreparedStatement getBGGenomeMapID = null;
    ResultSet rs = null;
    try {
      getBGGenomeMapID = cxn.prepareStatement(SQL_GET_MAP_ID);
      getBGGenomeMapID.setInt(1, bgModelID);
      getBGGenomeMapID.setInt(2, genomeID);
      rs = getBGGenomeMapID.executeQuery();
      if (rs.next()) {
        return rs.getInt(1);
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getBGGenomeMapID != null) {
        getBGGenomeMapID.close();
      }      
    }
  }
  
  
  /**************************************************************************
   * Methods for looking up which background models are in the database
   **************************************************************************/
  
  
  /**
   * @see getBackgroundModelByModelID(int modelID, Connection cxn)
   */
  public static BackgroundModelMetadata getBackgroundModelByModelID(int modelID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundModelByModelID(modelID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a partial metadata object containing id, name, type, kmerlen for the
   * specified modelID 
   * @param modelID the model ID to look up
   * @param cxn an open db connection to the annotations schema
   * @return A metadata object, or null if there's no match
   * @throws SQLException
   */
  public static BackgroundModelMetadata getBackgroundModelByModelID(int modelID, Connection cxn) throws SQLException {
    PreparedStatement getModel = null;
    ResultSet rs = null;
    try {
      getModel = cxn.prepareStatement(SQL_GET_METADATA_BY_MODEL_ID);
      getModel.setInt(1, modelID);
      rs = getModel.executeQuery();
      if (rs.next()) {
        return new BackgroundModelMetadata(modelID, rs.getString(1), rs.getInt(2), rs.getString(3));
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModel != null) {
        getModel.close();
      }      
    }
  }
  
  
  /**
   * @see getBackgroundModel(int modelID, int genomeID, Connection cxn)
   */
  public static BackgroundModelMetadata getBackgroundModel(int modelID, int genomeID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundModel(modelID, genomeID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a metadata object for a model with the specified modelID and genomeID
   * @param modelID the model ID to look up
   * @param genomeID the genome ID to look up
   * @param cxn an open db connection to the annotations schema
   * @return A metadata object, or null if there's no match
   * @throws SQLException
   */
  public static BackgroundModelMetadata getBackgroundModel(int modelID, int genomeID, Connection cxn) throws SQLException {
    PreparedStatement getModel = null;
    ResultSet rs = null;
    try {
      StringBuffer sql = new StringBuffer(SQL_GET_METADATA_CORE);
      sql.append(SQL_GET_METADATA_MODEL_ID);
      sql.append(SQL_GET_METADATA_GENOME_ID);
      sql.append(SQL_GET_METADATA_ORDER_BY);
      
      getModel = cxn.prepareStatement(sql.toString());
      getModel.setInt(1, modelID);
      getModel.setInt(2, genomeID);
      rs = getModel.executeQuery();
      if (rs.next()) {
        return new BackgroundModelMetadata(rs.getInt(SQL_GET_METADATA_CORE_MAP_ID_INDEX), genomeID, modelID, 
            rs.getString(SQL_GET_METADATA_CORE_NAME_INDEX), rs.getInt(SQL_GET_METADATA_CORE_KMER_LEN_INDEX), 
            rs.getString(SQL_GET_METADATA_CORE_MODEL_TYPE_INDEX), rs.getBoolean(SQL_GET_METADATA_CORE_HAS_COUNTS_INDEX));
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModel != null) {
        getModel.close();
      }      
    }
  }
  
  
  /**
   * @see getBackgroundModel(String name, int maxKmerLen, String modelType, int genomeID, Connection cxn)
   */
  public static BackgroundModelMetadata getBackgroundModel(String name, int maxKmerLen, String modelType, int genomeID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundModel(name, maxKmerLen, modelType, genomeID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a metadata object for a model with the specified name, kmerlen, type
   * and genome ID
   * @param name the name to look up
   * @param maxKmerLen the max kmer length to look up
   * @param modelType the model type to look up
   * @param genomeID the genome ID to look up
   * @param cxn an open db connection to the annotations schema
   * @return A metadata object, or null if there's no match
   * @throws SQLException
   */
  public static BackgroundModelMetadata getBackgroundModel(String name, int maxKmerLen, String modelType, int genomeID, Connection cxn) throws SQLException {
    PreparedStatement getModel = null;
    ResultSet rs = null;
    try {
      StringBuffer sql = new StringBuffer(SQL_GET_METADATA_CORE);
      sql.append(SQL_GET_METADATA_NAME);
      sql.append(SQL_GET_METADATA_KMER_LEN);
      sql.append(SQL_GET_METADATA_MODEL_TYPE);
      sql.append(SQL_GET_METADATA_GENOME_ID);
      sql.append(SQL_GET_METADATA_ORDER_BY);

      getModel = cxn.prepareStatement(sql.toString());
      getModel.setString(1, name);
      getModel.setInt(2, maxKmerLen);
      getModel.setString(3, modelType);
      getModel.setInt(4, genomeID);
      rs = getModel.executeQuery();
      if (rs.next()) {
        return new BackgroundModelMetadata(rs.getInt(SQL_GET_METADATA_CORE_MAP_ID_INDEX), genomeID, rs.getInt(SQL_GET_METADATA_CORE_MODEL_ID_INDEX), 
            name, maxKmerLen, modelType, rs.getBoolean(SQL_GET_METADATA_CORE_HAS_COUNTS_INDEX));
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModel != null) {
        getModel.close();
      }      
    }
  }
  
  
  /**
   * @see getBackgroundModelByMapID(int mapID, Connection cxn)
   */
  public static BackgroundModelMetadata getBackgroundModelByMapID(int mapID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundModelByMapID(mapID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a metadata object for a model with the specified map ID
   * @param mapID the background genome map ID to look up
   * @param cxn an open db connection to the annotations schema
   * @return A metadata object, or null if there's no match
   * @throws SQLException
   */
  public static BackgroundModelMetadata getBackgroundModelByMapID(int mapID, Connection cxn) throws SQLException {
    PreparedStatement getModel = null;
    ResultSet rs = null;
    try {
      StringBuffer sql = new StringBuffer(SQL_GET_METADATA_CORE);
      sql.append(SQL_GET_METADATA_MAP_ID);
      sql.append(SQL_GET_METADATA_ORDER_BY);

      getModel = cxn.prepareStatement(sql.toString());
      getModel.setInt(1, mapID);
      rs = getModel.executeQuery();
      if (rs.next()) {
        return new BackgroundModelMetadata(mapID, rs.getInt(SQL_GET_METADATA_CORE_GENOME_ID_INDEX), rs.getInt(SQL_GET_METADATA_CORE_MODEL_ID_INDEX),
            rs.getString(SQL_GET_METADATA_CORE_NAME_INDEX), rs.getInt(SQL_GET_METADATA_CORE_KMER_LEN_INDEX), 
            rs.getString(SQL_GET_METADATA_CORE_MODEL_TYPE_INDEX), rs.getBoolean(SQL_GET_METADATA_CORE_HAS_COUNTS_INDEX));
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModel != null) {
        getModel.close();
      }      
    }
  }
  
  
  /**
   * @see getAllBackgroundModels(boolean ignoreGenome, Connection cxn)
   */
  public static List<BackgroundModelMetadata> getAllBackgroundModels() throws SQLException {
    return BackgroundModelLoader.getAllBackgroundModels(false);
  }
  
  
  /**
   * @see getAllBackgroundModels(boolean ignoreGenome, Connection cxn)
   */
  public static List<BackgroundModelMetadata> getAllBackgroundModels(boolean ignoreGenome) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getAllBackgroundModels(ignoreGenome, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  

  /**
   * @see getAllBackgroundModels(boolean ignoreGenome, Connection cxn)
   */
  public static List<BackgroundModelMetadata> getAllBackgroundModels(Connection cxn) throws SQLException {
    return BackgroundModelLoader.getAllBackgroundModels(false, cxn);
  }

  
  /**
   * Get metadata objects for all models. If the ignore genome option is true
   * then partial metadata objects (without genomes and map IDs) are returned.
   * @param ignoreGenome if false return metadata for each entry in the 
   * background_genome_map table, if true return metadata for each entry in the 
   * background_model table
   * @param cxn an open db connection to the annotations schema
   * @return A list of metadata objects
   * @throws SQLException
   */
  public static List<BackgroundModelMetadata> getAllBackgroundModels(boolean ignoreGenome, Connection cxn) throws SQLException {
    PreparedStatement getAllModels = null;
    ResultSet rs = null;
    try {
      if (ignoreGenome) {
        getAllModels = cxn.prepareStatement(SQL_GET_ALL_MODELS);
        rs = getAllModels.executeQuery();
        List<BackgroundModelMetadata> results = new ArrayList<BackgroundModelMetadata>();
        while (rs.next()) {        
          results.add(new BackgroundModelMetadata(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
        }
        return results;
      }
      else {
        StringBuffer sql = new StringBuffer(SQL_GET_METADATA_CORE);
        sql.append(SQL_GET_METADATA_ORDER_BY);

        getAllModels = cxn.prepareStatement(sql.toString());
        rs = getAllModels.executeQuery();
        List<BackgroundModelMetadata> results = new ArrayList<BackgroundModelMetadata>();
        while (rs.next()) {    
          results.add(new BackgroundModelMetadata(rs.getInt(SQL_GET_METADATA_CORE_MAP_ID_INDEX), rs.getInt(SQL_GET_METADATA_CORE_GENOME_ID_INDEX), 
              rs.getInt(SQL_GET_METADATA_CORE_MODEL_ID_INDEX), rs.getString(SQL_GET_METADATA_CORE_NAME_INDEX), 
              rs.getInt(SQL_GET_METADATA_CORE_KMER_LEN_INDEX), rs.getString(SQL_GET_METADATA_CORE_MODEL_TYPE_INDEX), 
              rs.getBoolean(SQL_GET_METADATA_CORE_HAS_COUNTS_INDEX)));
        }
        return results;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getAllModels != null) {
        getAllModels.close();
      }      
    }
  }
  
  
  /**
   * @see getBackgroundModelsForGenome(int genomeID, Connection cxn)
   */
  public static List<BackgroundModelMetadata> getBackgroundModelsForGenome(int genomeID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getBackgroundModelsForGenome(genomeID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a list of metadata objects for all background models for the specified
   * genome
   * @param genomeID the genome ID to look up
   * @param cxn an open db connection to the annotations schema
   * @return a list of metadata objects
   * @throws SQLException
   */
  public static List<BackgroundModelMetadata> getBackgroundModelsForGenome(int genomeID, Connection cxn) throws SQLException {
    PreparedStatement getGenomeModels = null;
    ResultSet rs = null;    
    try {
      StringBuffer sql = new StringBuffer(SQL_GET_METADATA_CORE);
      sql.append(SQL_GET_METADATA_GENOME_ID);
      sql.append(SQL_GET_METADATA_ORDER_BY);

      getGenomeModels = cxn.prepareStatement(sql.toString());
      getGenomeModels.setInt(1, genomeID);
      rs = getGenomeModels.executeQuery();
      List<BackgroundModelMetadata> results = new ArrayList<BackgroundModelMetadata>();
      while (rs.next()) {
        results.add(new BackgroundModelMetadata(rs.getInt(SQL_GET_METADATA_CORE_MAP_ID_INDEX), genomeID, 
            rs.getInt(SQL_GET_METADATA_CORE_MODEL_ID_INDEX), rs.getString(SQL_GET_METADATA_CORE_NAME_INDEX), 
            rs.getInt(SQL_GET_METADATA_CORE_KMER_LEN_INDEX), rs.getString(SQL_GET_METADATA_CORE_MODEL_TYPE_INDEX), 
            rs.getBoolean(SQL_GET_METADATA_CORE_HAS_COUNTS_INDEX)));
      }
      return results;
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getGenomeModels != null) {
        getGenomeModels.close();
      }      
    }
  }
  
  
  /**
   * @see getGenomesForBackgroundModel(int modelID, Connection cxn)
   */
  public static List<Integer> getGenomesForBackgroundModel(int modelID) throws SQLException{
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getGenomesForBackgroundModel(modelID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a list of genome IDs for which there are models with the specified
   * model ID
   * @param modelID the modelID to look up
   * @param cxn an open db connection to the annotations schema
   * @return a list of genome IDs
   * @throws SQLException
   */
  public static List<Integer> getGenomesForBackgroundModel(int modelID, Connection cxn) throws SQLException {
    PreparedStatement getModels = null;
    ResultSet rs = null;
    try {
      getModels = cxn.prepareStatement(SQL_GET_GENOMES);
      getModels.setInt(1, modelID);
      List<Integer> results = new ArrayList<Integer>();
      rs = getModels.executeQuery();
      while (rs.next()) {
        results.add(rs.getInt(1));
      }
      return results;
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModels != null) {
        getModels.close();
      }
    }
  }
  
  
  /**************************************************************************
   * Method used for constructing queries for all model types
   **************************************************************************/  

  /**
   * Given the parameters that describe one or more background models construct
   * an appropriate SQL query and return the Background Models that are found 
   * @param mapID an ID from the background genome map (this will limit to a 
   * single model)
   * @param genomeID a genome ID for the models
   * @param modelID a model ID from the background_model table
   * @param name a model name
   * @param maxKmerLen a maximum kmer length
   * @param cxn an open DB connection to the annotations schema
   * @return A list of matching Counts Background Models
   * @throws SQLException
   */
  private static PreparedStatement assembleGetModelQuery(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, String modelType, Boolean hasCounts, Connection cxn) throws SQLException {
    //build the appropriate sql query
    StringBuffer sql = new StringBuffer(SQL_GET_MODEL_CORE);
    if (mapID != null) {
      sql.append(SQL_GET_MODEL_MAP_ID);
    }
    if (genomeID != null) {
      sql.append(SQL_GET_MODEL_GENOME_ID);
    }
    if (modelID != null) {
      sql.append(SQL_GET_MODEL_MODEL_ID);
    }
    if (name != null) {
      sql.append(SQL_GET_MODEL_NAME);
    }
    if (maxKmerLen != null) {
      sql.append(SQL_GET_MODEL_KMER_LEN);
    }
    if (modelType != null) {
      sql.append(SQL_GET_MODEL_TYPE);
    }
    if (hasCounts != null) {
      sql.append(SQL_GET_MODEL_HAS_COUNTS);
    }

    
    sql.append(SQL_GET_MODEL_ORDER_BY);
    //done building the query      

    PreparedStatement getModels = cxn.prepareStatement(sql.toString());

    //set the sql params        
    int argCount = 1;
    if (mapID != null) {
      getModels.setInt(argCount, mapID);
      argCount++;
    }
    if (genomeID != null) {
      getModels.setInt(argCount, genomeID);
      argCount++;
    }
    if (modelID != null) {
      getModels.setInt(argCount, modelID);
      argCount++;
    }
    if (name != null) {
      getModels.setString(argCount, name);
      argCount++;
    }
    if (maxKmerLen != null) {
      getModels.setInt(argCount, maxKmerLen);
      argCount++;
    }
    if (modelType != null) {
      getModels.setString(argCount, modelType);
      argCount++;
    }
    if (hasCounts != null) {
      if (hasCounts.booleanValue()) {
        getModels.setInt(argCount, 1);
      }
      else {
        getModels.setInt(argCount, 0);
      }
      argCount++;
    }
    
    return getModels;
  }

  
  
  /**************************************************************************
   * Methods for loading frequency background models
   **************************************************************************/  
  
  /**
   * Given a FrequencyBackgroundModel object and a result set whose rows are 
   * kmer probabilities (and perhaps other data), parse those rows and 
   * set the model probabilities accordingly
   * @param fbm A FrequencyBackgroundModel object to initialize
   * @param rs a ResultSet of kmer probability data, already on the first row
   * for the specified background model
   * @param queryCore the query core used to obtain the result set. This is used
   * to determine which indices to check for the kmer probabilities
   * @return true if there are more rows in the result set for another background
   * model, false otherwise
   * @throws SQLException
   */
  private static boolean initFrequencyModelProbs(FrequencyBackgroundModel fbm, ResultSet rs, String queryCore) throws SQLException {
    int idIndex;
    int kmerIndex;
    int probIndex;
    
    if (queryCore.equals(SQL_GET_MODEL_BY_ID)) {
      idIndex = SQL_GET_MODEL_BY_ID_MAP_ID_INDEX;
      kmerIndex = SQL_GET_MODEL_BY_ID_KMER_INDEX;
      probIndex = SQL_GET_MODEL_BY_ID_PROB_INDEX;
    }
    else if (queryCore.equals(SQL_GET_MODEL_CORE)) {
      idIndex = SQL_GET_MODEL_CORE_MAP_ID_INDEX;
      kmerIndex = SQL_GET_MODEL_CORE_KMER_INDEX;
      probIndex = SQL_GET_MODEL_CORE_PROB_INDEX;
    }
    else {
      throw new IllegalArgumentException("Unrecognized query core: " + queryCore);
    }

    int currKmerLen = rs.getString(kmerIndex).length();
    HashMap<String, Double> probs = new HashMap<String, Double>();
    boolean hasNext = true;
    do {
      String kmer = rs.getString(kmerIndex);
      //if this row is a new kmer length then set the probabilities for the
      //previous length
      if (kmer.length() != currKmerLen) {
        if (!probs.isEmpty()) {
          fbm.setKmerFrequencies(probs);
          probs.clear();
        }
        currKmerLen = kmer.length();
      }
      probs.put(kmer, rs.getDouble(probIndex));
      hasNext = rs.next();
    } while (hasNext && (rs.getInt(idIndex) == fbm.getMapID()));

    //set the last batch of kmer probabilities
    fbm.setKmerFrequencies(probs);

    return hasNext;
  }


  /**
   * @see getFrequencyModel(BackgroundModelMetadata md, Connection cxn)
   */
  public static FrequencyBackgroundModel getFrequencyModel(BackgroundModelMetadata md) throws SQLException, NotFoundException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getFrequencyModel(md, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
    
  /**
   * Get a Frequency Background Model described by the specified metadata
   * @param md A complete metadata object specifying a background model. 
   * @param cxn an open db connection to the annotations schema
   * @return A FrequencyBackgroundModel, or null if none exists (and can't be generated)
   * @throws SQLException
   * @throws NotFoundException if the genomeID is invalid, most likely because
   * the metadata object wasn't loaded from the database or was modified afterwards
   */
  public static FrequencyBackgroundModel getFrequencyModel(BackgroundModelMetadata md, Connection cxn) throws SQLException, NotFoundException {
    PreparedStatement getProbs = null;
    ResultSet rs = null;
    try {
      //check that the metadata is complete
      if (!md.hasMapID() || !md.hasGenomeID() || !md.hasModelID() || (md.getName() == null) || !md.hasMaxKmerLen() || (md.getDBModelType() == null)) {
        throw new NullPointerException("Metadata object has one or more null fields");
      }
      
      int mapID = md.getMapID();
      //if the model type is frequency then load it directly
      if (md.getDBModelType().equals(FREQUENCY_TYPE_STRING)) {        
        FrequencyBackgroundModel fbm = new FrequencyBackgroundModel(md);
        
        getProbs = cxn.prepareStatement(SQL_GET_MODEL_BY_ID);
        getProbs.setInt(1, mapID);
        rs = getProbs.executeQuery();
        if (rs.next()) {
          BackgroundModelLoader.initFrequencyModelProbs(fbm, rs, SQL_GET_MODEL_BY_ID);
        }
        return fbm;
      }
      //otherwise load the counts if available and convert from the counts model
      else if (md.getDBModelType().equals(MARKOV_TYPE_STRING)) {
        if (BackgroundModelLoader.hasCounts(mapID, cxn)) {
          return new FrequencyBackgroundModel(BackgroundModelLoader.getCountsModel(md, cxn));
        }
        else {
          return null;
        }
      }
      else {
        //otherwise the model type is invalid
        throw new IllegalArgumentException("Invalid model type: " + md.getDBModelType());
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getProbs != null) {
        getProbs.close();
      }
    }
  }
  
  
  /**
   * @see getFrequencyModel(int mapID, Connection cxn)
   */
  public static FrequencyBackgroundModel getFrequencyModel(int mapID) throws SQLException, NotFoundException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getFrequencyModel(mapID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Gets a Frequency background model by its mapID.
   * @param mapID the mapID of the model to look up
   * @param cxn an open db connection to the annotations schema
   * @return
   * @throws SQLException
   * @throws NotFoundException
   */
  public static FrequencyBackgroundModel getFrequencyModel(int mapID, Connection cxn) throws SQLException, NotFoundException {
    BackgroundModelMetadata md = BackgroundModelLoader.getBackgroundModelByMapID(mapID, cxn);
    if (md != null) {
      return BackgroundModelLoader.getFrequencyModel(md, cxn);
    }
    else {
      return null;
    }
  }
  
  
  
    
  /**
   * Returns a list of Frequency Background Models parsed out of a single result
   * set
   * @param rs the result set containing the data for the background model(s)
   * @param queryCore the SQL query core that was used to generate the specified
   * result set
   * @return A list of FrequencyBackgroundModels
   * @throws SQLException
   */  
  private static List<FrequencyBackgroundModel> createFrequencyModels(ResultSet rs, String queryCore) throws SQLException {
    int mapIDIndex;
    int nameIndex;
    int kmerLenIndex;
    int modelIDIndex;
    int genomeIDIndex;
    
    if (queryCore.equals(SQL_GET_MODEL_CORE)) {
      mapIDIndex = SQL_GET_MODEL_CORE_MAP_ID_INDEX;
      nameIndex = SQL_GET_MODEL_CORE_NAME_INDEX;
      kmerLenIndex = SQL_GET_MODEL_CORE_KMERLEN_INDEX;
      modelIDIndex = SQL_GET_MODEL_CORE_MODEL_ID_INDEX;
      genomeIDIndex = SQL_GET_MODEL_CORE_GENOME_ID_INDEX;
    }
    else {
      throw new IllegalArgumentException("Unrecognized query core: " + queryCore);
    }     
    
    try {
      List<FrequencyBackgroundModel> models = new ArrayList<FrequencyBackgroundModel>();
      if (rs.next()) {
        boolean hasNext = true;
        while (hasNext) {
          int mapID = rs.getInt(mapIDIndex);
          FrequencyBackgroundModel mbm = new FrequencyBackgroundModel(rs.getString(nameIndex), Organism.findGenome(rs.getInt(genomeIDIndex)), rs.getInt(kmerLenIndex));
          mbm.setMapID(mapID);
          mbm.setModelID(rs.getInt(modelIDIndex));
          models.add(mbm);
          //call the subroutine to parse all the probabilities from the result set
          hasNext = BackgroundModelLoader.initFrequencyModelProbs(mbm, rs, queryCore);
        }
      }
      return models;
    }
    catch (NotFoundException nfex) {
      throw new DatabaseException("Error loading genome for model", nfex);
    }      
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  private static List<FrequencyBackgroundModel> getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getFrequencyModels(mapID, genomeID, modelID, name, maxKmerLen, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }

  
  /**
   * Given the parameters that describe one or more background models construct
   * an appropriate SQL query and return the Background Models that are found 
   * @param mapID an ID from the background genome map (this will limit to a 
   * single model)
   * @param genomeID a genome ID for the models
   * @param modelID a model ID from the background_model table
   * @param name a model name
   * @param maxKmerLen a maximum kmer length
   * @param cxn an open DB connection to the annotations schema
   * @return A list of matching Frequency Background Models
   * @throws SQLException
   */
  private static List<FrequencyBackgroundModel> getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn) throws SQLException {
    PreparedStatement getModels = null;
    ResultSet rs = null;
    try {
      getModels = BackgroundModelLoader.assembleGetModelQuery(mapID, genomeID, modelID, name, maxKmerLen, FREQUENCY_TYPE_STRING, null, cxn);      
      rs = getModels.executeQuery();
      return BackgroundModelLoader.createFrequencyModels(rs, SQL_GET_MODEL_CORE);      
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModels != null) {
        getModels.close();
      }
    }
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(String name) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, name, null);
  }

  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(String name, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, name, null,cxn);
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(String name, int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, name, maxKmerLen);
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(String name, int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, name, maxKmerLen, cxn);
  }

  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(int modelID) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, modelID, null, null);
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(int modelID, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, modelID, null, null, cxn);
  }
  

  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(BackgroundModelMetadata md) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getFrequencyModels(md, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }

  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModels(BackgroundModelMetadata md, Connection cxn) throws SQLException {
    Integer mapID = null;
    Integer genomeID = null;
    Integer modelID = null;
    String name = null;
    Integer maxKmerLen = null;
    
    //initialize any fields that are available 
    if (md.hasMapID()) {
      mapID = md.getMapID();
    }
    if (md.hasGenomeID()) {
     genomeID = md.getGenomeID();
    }
    if (md.hasModelID()) {
      modelID = md.getModelID();
    }
    name = md.getName();
    if (md.hasMaxKmerLen()) {
      maxKmerLen = md.getMaxKmerLen();
    }

    return BackgroundModelLoader.getFrequencyModels(mapID, genomeID, modelID, name, maxKmerLen, cxn);
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByLength(int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, null, maxKmerLen);
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByLength(int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, null, maxKmerLen, cxn);
  }
  
  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByGenome(int genomeID) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, genomeID, null, null, null);
  }


  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByGenome(int genomeID, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, genomeID, null, null, null, cxn);
  }


  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */  
  public static List<FrequencyBackgroundModel> getFrequencyModelsByGenome(int genomeID, String name) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, genomeID, null, name, null);
  }


  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByGenome(int genomeID, String name, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, genomeID, null, name, null, cxn);
  }

  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByGenome(int genomeID, int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, genomeID, null, null, maxKmerLen);
  }
  

  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getFrequencyModelsByGenome(int genomeID, int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, genomeID, null, null, maxKmerLen, cxn);
  }

  
  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getAllFrequencyModels() throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, null, null);
  }
  

  /**
   * @see getFrequencyModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<FrequencyBackgroundModel> getAllFrequencyModels(Connection cxn) throws SQLException {
    return BackgroundModelLoader.getFrequencyModels(null, null, null, null, null, cxn);
  }

  
  /**************************************************************************
   * Methods for loading Markov background models
   **************************************************************************/

  /**
   * Given a MarkovBackgroundModel object and a result set whose rows are 
   * kmer probabilities (and perhaps other data), parse those rows and 
   * set the model probabilities accordingly
   * @param mbm A MarkovBackgroundModel object to initialize
   * @param rs a ResultSet of kmer probability data, already on the first row
   * for the specified background model
   * @param queryCore the query core used to obtain the result set. This is used
   * to determine which indices to check for the kmer probabilities
   * @return true if there are more rows in the result set for another background
   * model, false otherwise
   * @throws SQLException
   */
  private static boolean initMarkovModelProbs(MarkovBackgroundModel mbm, ResultSet rs, String queryCore) throws SQLException {
    int idIndex;
    int kmerIndex;
    int probIndex;
    
    if (queryCore.equals(SQL_GET_MODEL_BY_ID)) {
      idIndex = SQL_GET_MODEL_BY_ID_MAP_ID_INDEX;
      kmerIndex = SQL_GET_MODEL_BY_ID_KMER_INDEX;
      probIndex = SQL_GET_MODEL_BY_ID_PROB_INDEX;
    }
    else if (queryCore.equals(SQL_GET_MODEL_CORE)) {
      idIndex = SQL_GET_MODEL_CORE_MAP_ID_INDEX;
      kmerIndex = SQL_GET_MODEL_CORE_KMER_INDEX;
      probIndex = SQL_GET_MODEL_CORE_PROB_INDEX;
    }
    else {
      throw new IllegalArgumentException("Unrecognized query core: " + queryCore);
    }

    String firstKmer = rs.getString(kmerIndex);
    String currPrevBases = firstKmer.substring(0, firstKmer.length()- 1);
    double[] probs = new double[4];
    boolean hasNext = true;
    do {
      String kmer = rs.getString(kmerIndex);
      String prevBases = kmer.substring(0, kmer.length()-1);
      //if this row is a new value of prev bases then set the probabilities for
      //the old value
      if (!prevBases.equals(currPrevBases)) {
        mbm.setMarkovProb(currPrevBases, probs[0], probs[1], probs[2], probs[3]);
        Arrays.fill(probs, 0.0);
        currPrevBases = prevBases;
      }
      char currBase = kmer.charAt(kmer.length()-1);
      double prob = rs.getDouble(probIndex);
      switch (currBase) {
        case 'A': probs[0] = prob; break;
        case 'C': probs[1] = prob; break;
        case 'G': probs[2] = prob; break;
        case 'T': probs[3] = prob; break;
      }          
      hasNext = rs.next();
    } while (hasNext && (rs.getInt(idIndex) == mbm.getMapID()));
    //set the last batch of kmer probabilities
    mbm.setMarkovProb(currPrevBases, probs[0], probs[1], probs[2], probs[3]);
    return hasNext; 
  }
  
  
  /**
   * @see getMarkovModel(BackgroundModelMetadata md, Connection cxn)
   */
  public static MarkovBackgroundModel getMarkovModel(BackgroundModelMetadata md) throws SQLException, NotFoundException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getMarkovModel(md, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
    
  /**
   * Get a Markov Background Model described by the specified metadata
   * @param md A complete metadata object specifying a background model. 
   * @param cxn an open db connection to the annotations schema
   * @return A MarkovBackgroundModel, or null if none exists (and can't be generated)
   * @throws SQLException
   * @throws NotFoundException if the genomeID is invalid, most likely because
   * the metadata object wasn't loaded from the database or was modified afterwards
   */
  public static MarkovBackgroundModel getMarkovModel(BackgroundModelMetadata md, Connection cxn) throws SQLException, NotFoundException {
    PreparedStatement getProbs = null;
    ResultSet rs = null;
    try {
      //check that the metadata is complete
      if (!md.hasMapID() || !md.hasGenomeID() || !md.hasModelID() || (md.getName() == null) || !md.hasMaxKmerLen() || (md.getDBModelType() == null)) {
        throw new NullPointerException("Metadata object has one or more null fields");
      }
      
      int mapID = md.getMapID();
      //if the model's db model type is Markov then load it directly
      if (md.getDBModelType().equals(MARKOV_TYPE_STRING)) {        
        MarkovBackgroundModel mbm = new MarkovBackgroundModel(md);
        
        getProbs = cxn.prepareStatement(SQL_GET_MODEL_BY_ID);
        getProbs.setInt(1, mapID);
        rs = getProbs.executeQuery();
        if (rs.next()) {
          BackgroundModelLoader.initMarkovModelProbs(mbm, rs, SQL_GET_MODEL_BY_ID);
        }
        return mbm;
      }
      //otherwise, if it's frequency, then load it as a frequency model and 
      //convert it to a markov model
      else if (md.getDBModelType().equals(FREQUENCY_TYPE_STRING)) {
        return new MarkovBackgroundModel(BackgroundModelLoader.getFrequencyModel(md, cxn));
      }
      //otherwise there's some error with the model type
      else {
        throw new IllegalArgumentException("Invalid model type: " + md.getDBModelType());
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getProbs != null) {
        getProbs.close();
      }
    }
  }
  
  
  /**
   * @see getMarkovModel(int mapID, Connection cxn)
   */
  public static MarkovBackgroundModel getMarkovModel(int mapID) throws SQLException, NotFoundException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getMarkovModel(mapID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Gets a Markov background model by its mapID.
   * @param mapID the mapID of the model to look up
   * @param cxn an open db connection to the annotations schema
   * @return
   * @throws SQLException
   * @throws NotFoundException
   */
  public static MarkovBackgroundModel getMarkovModel(int mapID, Connection cxn) throws SQLException, NotFoundException {
    BackgroundModelMetadata md = BackgroundModelLoader.getBackgroundModelByMapID(mapID, cxn);
    if (md != null) {
      return BackgroundModelLoader.getMarkovModel(md, cxn);
    }
    else {
      return null;
    }
  }
  
  
  /**
   * Returns a list of Markov Background Models parsed out of a single result
   * set
   * @param rs the result set containing the data for the background model(s)
   * @param queryCore the SQL query core that was used to generate the specified
   * result set
   * @return A list of MarkovBackgroundModels
   * @throws SQLException
   */
  private static List<MarkovBackgroundModel> createMarkovModels(ResultSet rs, String queryCore) throws SQLException {
    int mapIDIndex;
    int nameIndex;
    int kmerLenIndex;
    int modelIDIndex;
    int genomeIDIndex;

    if (queryCore.equals(SQL_GET_MODEL_CORE)) {
      mapIDIndex = SQL_GET_MODEL_CORE_MAP_ID_INDEX;
      nameIndex = SQL_GET_MODEL_CORE_NAME_INDEX;
      kmerLenIndex = SQL_GET_MODEL_CORE_KMERLEN_INDEX;
      modelIDIndex = SQL_GET_MODEL_CORE_MODEL_ID_INDEX;
      genomeIDIndex = SQL_GET_MODEL_CORE_GENOME_ID_INDEX;
    }
    else {
      throw new IllegalArgumentException("Unrecognized query core: " + queryCore);
    }     

    try {
      List<MarkovBackgroundModel> models = new ArrayList<MarkovBackgroundModel>();
      if (rs.next()) {
        boolean hasNext = true;
        while (hasNext) {
          int mapID = rs.getInt(mapIDIndex);
          MarkovBackgroundModel mbm = new MarkovBackgroundModel(rs.getString(nameIndex), Organism.findGenome(rs.getInt(genomeIDIndex)), rs.getInt(kmerLenIndex));
          mbm.setMapID(mapID);
          mbm.setModelID(rs.getInt(modelIDIndex));
          models.add(mbm);
          //call the subroutine to parse all the probabilities from the result set
          hasNext = BackgroundModelLoader.initMarkovModelProbs(mbm, rs, queryCore);
        }
      }
      return models;
    }
    catch (NotFoundException nfex) {
      throw new DatabaseException("Error loading genome for model", nfex);
    }      
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  private static List<MarkovBackgroundModel> getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getMarkovModels(mapID, genomeID, modelID, name, maxKmerLen, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }

  
  /**
   * Given the parameters that describe one or more background models construct
   * an appropriate SQL query and return the Background Models that are found 
   * @param mapID an ID from the background genome map (this will limit to a 
   * single model)
   * @param genomeID a genome ID for the models
   * @param modelID a model ID from the background_model table
   * @param name a model name
   * @param maxKmerLen a maximum kmer length
   * @param cxn an open DB connection to the annotations schema
   * @return A list of matching Markov Background Models
   * @throws SQLException
   */
  private static List<MarkovBackgroundModel> getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn) throws SQLException {
    PreparedStatement getModels = null;
    ResultSet rs = null;
    try {
      getModels = BackgroundModelLoader.assembleGetModelQuery(mapID, genomeID, modelID, name, maxKmerLen, MARKOV_TYPE_STRING, null, cxn);      
      rs = getModels.executeQuery();
      return BackgroundModelLoader.createMarkovModels(rs, SQL_GET_MODEL_CORE);      
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModels != null) {
        getModels.close();
      }
    }
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(String name) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, name, null);
  }

  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(String name, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, name, null,cxn);
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(String name, int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, name, maxKmerLen);
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(String name, int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, name, maxKmerLen, cxn);
  }

  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(int modelID) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, modelID, null, null);
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(int modelID, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, modelID, null, null, cxn);
  }
  

  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(BackgroundModelMetadata md) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getMarkovModels(md, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }

  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModels(BackgroundModelMetadata md, Connection cxn) throws SQLException {
    Integer mapID = null;
    Integer genomeID = null;
    Integer modelID = null;
    String name = null;
    Integer maxKmerLen = null;
    
    //initialize any fields that are available 
    if (md.hasMapID()) {
      mapID = md.getMapID();
    }
    if (md.hasGenomeID()) {
     genomeID = md.getGenomeID();
    }
    if (md.hasModelID()) {
      modelID = md.getModelID();
    }
    name = md.getName();
    if (md.hasMaxKmerLen()) {
      maxKmerLen = md.getMaxKmerLen();
    }

    return BackgroundModelLoader.getMarkovModels(mapID, genomeID, modelID, name, maxKmerLen, cxn);
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByLength(int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, null, maxKmerLen);
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByLength(int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, null, maxKmerLen, cxn);
  }
  
  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByGenome(int genomeID) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, genomeID, null, null, null);
  }


  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByGenome(int genomeID, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, genomeID, null, null, null, cxn);
  }


  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */  
  public static List<MarkovBackgroundModel> getMarkovModelsByGenome(int genomeID, String name) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, genomeID, null, name, null);
  }


  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByGenome(int genomeID, String name, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, genomeID, null, name, null, cxn);
  }

  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByGenome(int genomeID, int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, genomeID, null, null, maxKmerLen);
  }
  

  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getMarkovModelsByGenome(int genomeID, int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, genomeID, null, null, maxKmerLen, cxn);
  }

  
  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getAllMarkovModels() throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, null, null);
  }
  

  /**
   * @see getMarkovModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<MarkovBackgroundModel> getAllMarkovModels(Connection cxn) throws SQLException {
    return BackgroundModelLoader.getMarkovModels(null, null, null, null, null, cxn);
  }

  
  /**************************************************************************
   * Methods for loading Counts background models
   **************************************************************************/

  /**
   * @see hasCounts(int mapID, Connection cxn)
   */
  public static boolean hasCounts(int mapID) throws SQLException{
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.hasCounts(mapID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Check whether the model with the specified mapID has counts
   * @param mapID the backrgound genome map id of the model to check
   * @param cxn an open db connection to the annotations schema
   * @return true if the model has counts, false otherwise
   * @throws SQLException
   */
  public static boolean hasCounts(int mapID, Connection cxn) throws SQLException {
    PreparedStatement checkCounts = null;
    ResultSet rs = null;
    try {
      //check if the model has counts... 
      checkCounts = cxn.prepareStatement(SQL_HAS_COUNTS);
      checkCounts.setInt(1, mapID);
      rs = checkCounts.executeQuery();      
      if (rs.next()) {
        if (rs.getInt(1) == 1) {
          return true;
        }
        else {
          return false;
        }
      }
      else {
        return false;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (checkCounts != null) {
        checkCounts.close();
      }
    }
  }
  
  
  /**
   * Given a CountsBackgroundModel object and a result set whose rows are 
   * kmer counts (and perhaps other data), parse those rows and 
   * set the model counts accordingly
   * @param fbm A CountsBackgroundModel object to initialize
   * @param rs a ResultSet of kmer probability data, already on the first row
   * for the specified background model
   * @param queryCore the query core used to obtain the result set. This is used
   * to determine which indices to check for the kmer probabilities
   * @return true if there are more rows in the result set for another background
   * model, false otherwise
   * @throws SQLException
   */
  private static boolean initCountsModel(CountsBackgroundModel cbm, ResultSet rs, String queryCore) throws SQLException {
    int idIndex;
    int kmerIndex;
    int countIndex;
    
    if (queryCore.equals(SQL_GET_MODEL_BY_ID)) {
      idIndex = SQL_GET_MODEL_BY_ID_MAP_ID_INDEX;
      kmerIndex = SQL_GET_MODEL_BY_ID_KMER_INDEX;
      countIndex = SQL_GET_MODEL_BY_ID_COUNT_INDEX;
    }
    else if (queryCore.equals(SQL_GET_MODEL_CORE)) {
      idIndex = SQL_GET_MODEL_CORE_MAP_ID_INDEX;
      kmerIndex = SQL_GET_MODEL_CORE_KMER_INDEX;
      countIndex = SQL_GET_MODEL_CORE_COUNT_INDEX;
    }
    else {
      throw new IllegalArgumentException("Unrecognized query core: " + queryCore);
    }

    boolean hasNext = true;
    do {
      String kmer = rs.getString(kmerIndex);
      cbm.setKmerCount(kmer, rs.getLong(countIndex));
      hasNext = rs.next();
    } while (hasNext && (rs.getInt(idIndex) == cbm.getMapID()));

    return hasNext;
  }
  
  
  /**
   * @see getCountsModel(BackgroundModelMetadata md, Connection cxn)
   */
  public static CountsBackgroundModel getCountsModel(BackgroundModelMetadata md) throws SQLException, NotFoundException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getCountsModel(md, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Get a Counts Background Model described by the specified metadata
   * @param md A complete metadata object specifying a background model.  
   * @param cxn an open db connection to the annotations schema
   * @return A CountsBackgroundModel, or null if none exists (and can't be generated)
   * @throws SQLException
   * @throws NotFoundException if the genomeID is invalid, most likely because
   * the metadata object wasn't loaded from the database or was modified afterwards
   */
  public static CountsBackgroundModel getCountsModel(BackgroundModelMetadata md, Connection cxn) throws SQLException, NotFoundException {
    PreparedStatement getCounts = null;
    ResultSet rs = null;
    try {
      //check that the metadata is complete (except for the model type)
      if (!md.hasMapID() || !md.hasGenomeID() || !md.hasModelID() || (md.getName() == null) || !md.hasMaxKmerLen() || (!md.hasDBModelType())) {
        throw new NullPointerException("Metadata object has one or more null fields");
      }
      
      int mapID = md.getMapID();
      if (BackgroundModelLoader.hasCounts(mapID)) {        
        CountsBackgroundModel cbm = new CountsBackgroundModel(md);
        
        getCounts = cxn.prepareStatement(SQL_GET_MODEL_BY_ID);
        getCounts.setInt(1, mapID);
        rs = getCounts.executeQuery();
        if (rs.next()) {
          BackgroundModelLoader.initCountsModel(cbm, rs, SQL_GET_MODEL_BY_ID);
        }
        return cbm;
      }      
      //otherwise there aren't counts for the specified model...
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getCounts != null) {
        getCounts.close();
      }
    }
  }
  
  
  /**
   * @see getCountsModel(int mapID, Connection cxn)
   */
  public static CountsBackgroundModel getCountsModel(int mapID) throws SQLException, NotFoundException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getCountsModel(mapID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Gets a Counts background model by its mapID.
   * @param mapID the mapID of the model to look up
   * @param cxn an open db connection to the annotations schema
   * @return
   * @throws SQLException
   * @throws NotFoundException
   */
  public static CountsBackgroundModel getCountsModel(int mapID, Connection cxn) throws SQLException, NotFoundException {
    BackgroundModelMetadata md = BackgroundModelLoader.getBackgroundModelByMapID(mapID, cxn);
    if (md != null) {
      return BackgroundModelLoader.getCountsModel(md, cxn);
    }
    else {
      return null;
    }
  }
  
  
    
  /**
   * Returns a list of Counts Background Models parsed out of a single result
   * set
   * @param rs the result set containing the data for the background model(s)
   * @param queryCore the SQL query core that was used to generate the specified
   * result set
   * @return A list of CountsBackgroundModels
   * @throws SQLException
   */
  private static List<CountsBackgroundModel> createCountsModels(ResultSet rs, String queryCore) throws SQLException {
    int mapIDIndex;
    int nameIndex;
    int kmerLenIndex;
    int modelIDIndex;
    int genomeIDIndex;

    if (queryCore.equals(SQL_GET_MODEL_CORE)) {
      mapIDIndex = SQL_GET_MODEL_CORE_MAP_ID_INDEX;
      nameIndex = SQL_GET_MODEL_CORE_NAME_INDEX;
      kmerLenIndex = SQL_GET_MODEL_CORE_KMERLEN_INDEX;
      modelIDIndex = SQL_GET_MODEL_CORE_MODEL_ID_INDEX;
      genomeIDIndex = SQL_GET_MODEL_CORE_GENOME_ID_INDEX;
    }
    else {
      throw new IllegalArgumentException("Unrecognized query core: " + queryCore);
    }     

    try {
      List<CountsBackgroundModel> models = new ArrayList<CountsBackgroundModel>();
      if (rs.next()) {
        boolean hasNext = true;
        while (hasNext) {
          int mapID = rs.getInt(mapIDIndex);
          CountsBackgroundModel mbm = new CountsBackgroundModel(rs.getString(nameIndex), Organism.findGenome(rs.getInt(genomeIDIndex)), rs.getInt(kmerLenIndex));
          mbm.setMapID(mapID);
          mbm.setModelID(rs.getInt(modelIDIndex));
          models.add(mbm);
          //call the subroutine to parse all the counts from the result set
          hasNext = BackgroundModelLoader.initCountsModel(mbm, rs, queryCore);
        }
      }
      return models;
    }
    catch (NotFoundException nfex) {
      throw new DatabaseException("Error loading genome for model", nfex);
    }      
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  private static List<CountsBackgroundModel> getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getCountsModels(mapID, genomeID, modelID, name, maxKmerLen, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }

  
  /**
   * Given the parameters that describe one or more background models construct
   * an appropriate SQL query and return the Background Models that are found 
   * @param mapID an ID from the background genome map (this will limit to a 
   * single model)
   * @param genomeID a genome ID for the models
   * @param modelID a model ID from the background_model table
   * @param name a model name
   * @param maxKmerLen a maximum kmer length
   * @param cxn an open DB connection to the annotations schema
   * @return A list of matching Counts Background Models
   * @throws SQLException
   */
  private static List<CountsBackgroundModel> getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn) throws SQLException {
    PreparedStatement getModels = null;
    ResultSet rs = null;
    try {
      getModels = BackgroundModelLoader.assembleGetModelQuery(mapID, genomeID, modelID, name, maxKmerLen, null, true, cxn);            
      rs = getModels.executeQuery();
      return BackgroundModelLoader.createCountsModels(rs, SQL_GET_MODEL_CORE);      
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModels != null) {
        getModels.close();
      }
    }
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(String name) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, name, null);
  }

  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(String name, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, name, null,cxn);
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(String name, int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, name, maxKmerLen);
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(String name, int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, name, maxKmerLen, cxn);
  }

  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(int modelID) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, modelID, null, null);
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(int modelID, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, modelID, null, null, cxn);
  }
  

  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(BackgroundModelMetadata md) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelLoader.getCountsModels(md, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }

  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModels(BackgroundModelMetadata md, Connection cxn) throws SQLException {
    Integer mapID = null;
    Integer genomeID = null;
    Integer modelID = null;
    String name = null;
    Integer maxKmerLen = null;
    
    //initialize any fields that are available 
    if (md.hasMapID()) {
      mapID = md.getMapID();
    }
    if (md.hasGenomeID()) {
     genomeID = md.getGenomeID();
    }
    if (md.hasModelID()) {
      modelID = md.getModelID();
    }
    name = md.getName();
    if (md.hasMaxKmerLen()) {
      maxKmerLen = md.getMaxKmerLen();
    }

    return BackgroundModelLoader.getCountsModels(mapID, genomeID, modelID, name, maxKmerLen, cxn);
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByLength(int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, null, maxKmerLen);
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByLength(int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, null, maxKmerLen, cxn);
  }
  
  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByGenome(int genomeID) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, genomeID, null, null, null);
  }


  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByGenome(int genomeID, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, genomeID, null, null, null, cxn);
  }


  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */  
  public static List<CountsBackgroundModel> getCountsModelsByGenome(int genomeID, String name) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, genomeID, null, name, null);
  }


  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByGenome(int genomeID, String name, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, genomeID, null, name, null, cxn);
  }

  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByGenome(int genomeID, int maxKmerLen) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, genomeID, null, null, maxKmerLen);
  }
  

  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getCountsModelsByGenome(int genomeID, int maxKmerLen, Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, genomeID, null, null, maxKmerLen, cxn);
  }

  
  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getAllCountsModels() throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, null, null);
  }
  

  /**
   * @see getCountsModels(Integer mapID, Integer genomeID, Integer modelID, String name, Integer maxKmerLen, Connection cxn)
   */
  public static List<CountsBackgroundModel> getAllCountsModels(Connection cxn) throws SQLException {
    return BackgroundModelLoader.getCountsModels(null, null, null, null, null, cxn);
  }


  
  /**************************************************************************
   * Inserting and Updating Background Models
   **************************************************************************/
  
  /**
   * Insert a markov background model into the database
   * @param model the model to insert
   * @return the DBID of the model
   * @throws SQLException
   */
  public static Pair<Integer, Integer> insertMarkovModel(MarkovBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if ((model.getName() == null) || (model.getName().isEmpty()) || (model.getGenome() == null)) {
      throw new IllegalArgumentException("Model must have a name and genome specified to be imported to database.");
    }
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      //insert into the background model and background
      Pair<Integer, Integer> ids = BackgroundModelLoader.insertBackgroundModelAndMap(model.getName(), model.getMaxKmerLen(), 
          MARKOV_TYPE_STRING, model.getGenome().getDBID(), false, cxn);
      int mapID = ids.car();
      
      //Finally, insert all the "columns" of the background model
      BackgroundModelLoader.insertMarkovModelColumns(model, mapID, cxn);
      
      //If everything has worked then commit
      cxn.commit();

      //update the model with its new ID
      model.setMapID(mapID);

      return ids;
    }
    catch (SQLException sqlex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw sqlex;      
    }
    catch (CGSException cgsex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw cgsex;
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }


  /**
   * insert a frequency background model into the database
   * @param model the model to insert
   * @return the DBID of the model
   * @throws SQLException
   */
  public static Pair<Integer, Integer> insertFrequencyModel(FrequencyBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if ((model.getName() == null) || (model.getName().isEmpty()) || (model.getGenome() == null)) {
      throw new IllegalArgumentException("Model must have a name and genome specified to be imported to database.");
    }
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      //insert into the background model and background
      Pair<Integer, Integer> ids = BackgroundModelLoader.insertBackgroundModelAndMap(model.getName(), model.getMaxKmerLen(), 
          FREQUENCY_TYPE_STRING, model.getGenome().getDBID(), false, cxn);
      int mapID = ids.car();
      //insert all the "columns" of the background model
      BackgroundModelLoader.insertFrequencyModelColumns(model, mapID, cxn);

      //If everything has worked then commit
      cxn.commit();

      //update the model with its new ID
      model.setMapID(mapID);

      return ids;
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }
  

  /**
   * Insert a counts background model into the database
   * @param model the model to insert
   * @param insertAsMarkov indicates whether to insert the markov probabilities
   * or the frequencies corresponding to the counts 
   * @return the DBID of the model
   * @throws SQLException
   */
  public static Pair<Integer, Integer>  insertCountsModel(CountsBackgroundModel model, boolean insertAsMarkov) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if ((model.getName() == null) || (model.getName().isEmpty()) || (model.getGenome() == null)) {
      throw new IllegalArgumentException("Model must have a name and genome specified to be imported to database.");
    }
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      String modelType;
      if (insertAsMarkov) {
        modelType = MARKOV_TYPE_STRING;
      }
      else {
        modelType = FREQUENCY_TYPE_STRING;
      }
      
      //insert into the background model and background
      Pair<Integer, Integer>  ids = BackgroundModelLoader.insertBackgroundModelAndMap(model.getName(), model.getMaxKmerLen(), 
          modelType, model.getGenome().getDBID(), true, cxn);
      int mapID = ids.car();
      
      //insert all the "columns" of the background model
      BackgroundModelLoader.insertCountsModelColumns(model, mapID, insertAsMarkov, cxn);

      //If everything has worked then commit
      cxn.commit();

      //update the model with its new ID
      model.setMapID(mapID);

      return ids;
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }
  
  
  /**
   * Update a markov background model already in the database
   * @param model the model to insert
   * @throws SQLException
   */
  public static void updateMarkovModel(MarkovBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if (!model.hasMapID()) {
      throw new IllegalArgumentException("Model must already have a database ID to be updated in the database.");
    }
    
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      int mapID = model.getMapID();
      //remove from the database all the existing entries for the model columns
      BackgroundModelLoader.removeModelColumns(mapID, cxn);
      
      //Insert all the "columns" of the background model
      BackgroundModelLoader.insertMarkovModelColumns(model, mapID, cxn);
      
      //If everything has worked then commit
      cxn.commit();
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }

  
  /**
   * Update a frequency background model already in the database
   * @param model the model to insert
   * @throws SQLException
   */
  public static void updateFrequencyModel(FrequencyBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if (!model.hasMapID()) {
      throw new IllegalArgumentException("Model must already have a database ID to be updated in the database.");
    }
    
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      int mapID = model.getMapID();
      //remove from the database all the existing entries for the model columns
      BackgroundModelLoader.removeModelColumns(mapID, cxn);
      
      //Insert all the "columns" of the background model
      BackgroundModelLoader.insertFrequencyModelColumns(model, mapID, cxn);
      
      //If everything has worked then commit
      cxn.commit();
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }

  
  /**
   * Update a counts background model already in the database
   * @param model the model to insert
   * @throws SQLException
   */
  public static void updateCountsModel(CountsBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if (!model.hasMapID()) {
      throw new IllegalArgumentException("Model must already have a database ID to be updated in the database.");
    }
    
    java.sql.Connection cxn = null;
    PreparedStatement getModelType = null;
    ResultSet rs = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);      
      
      int mapID = model.getMapID();
      
      //determine whether this model exists in the database as a markov model or
      //a frequency model, so that it can be updated in the same format
      boolean isMarkov;
      getModelType = 
      	cxn.prepareStatement("select model_type from background_model bm, background_genome_map map where map.id = ? and map.bg_model_id = bm.id");
      getModelType.setInt(1, mapID);
      rs = getModelType.executeQuery();
      if (rs.next()) {
      	isMarkov = rs.getString(1).equals(MARKOV_TYPE_STRING);
      }
      else {
      	throw new DatabaseException("Unable to find Background Model in database.");
      }
      
      //remove from the database all the existing entries for the model columns
      BackgroundModelLoader.removeModelColumns(mapID, cxn);
      
      //Insert all the "columns" of the background model
      BackgroundModelLoader.insertCountsModelColumns(model, mapID, isMarkov, cxn);
      
      //If everything has worked then commit
      cxn.commit();
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
    	if (rs != null) {
    		rs.close();
    	}
    	if (getModelType != null) {
    		getModelType.close();
    	}
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }

  
  /**
   * insert entries for the model in the background model table and the
   * background model genome map table
   * @param name the name of the model
   * @param kmerLen the length of the longest kmer in the model
   * @param dbModelType the type of the model ("MARKOV" or "FREQUENCY")
   * @param genomeID the DBID of the genome the model is for
   * @param cxn an open database connection
   * @return the background genome map ID of the model
   * @throws SQLException
   */
  private static Pair<Integer, Integer> insertBackgroundModelAndMap(String name, int kmerLen, String modelType, int genomeID, boolean hasCounts, Connection cxn) throws SQLException, CGSException {
    /**
     * Check whether there is already an entry for a model with this name, maxKmerLen, and type. If so, reuse the model ID, 
     * otherwise create one.
     */
    Integer modelID = BackgroundModelLoader.getBackgroundModelID(name, kmerLen, modelType, cxn);
    if (modelID == null) {
      modelID = BackgroundModelLoader.insertBackgroundModel(name, kmerLen, modelType, cxn);
    }

    /**
     * Check whether there is already an entry for this model's genome in the
     * background model genome map. If not then create one, otherwise throw
     * an exception indicating that the update method should be called to
     * update an existing model.
     */
    Integer mapID = BackgroundModelLoader.getBackgroundGenomeMapID(modelID, genomeID, cxn);
    if (mapID != null) {
      throw new CGSException("Model already exists. Select a different name or use updateMarkovModel() to update the existing model.");
    }
    else {
      mapID = BackgroundModelLoader.insertBackgroundGenomeMap(modelID, genomeID, hasCounts, cxn);
    }

    return new Pair<Integer, Integer>(mapID, modelID);
  }


  /**
   * insert the model in the the background model table
   * @param name the name of the model
   * @param kmerLen the length of the longest kmer in the model
   * @param dbModelType the type of the model ("MARKOV" or "FREQUENCY")
   * @param cxn an open database connection
   * @return the background model ID 
   * @throws SQLException
   */
  private static Integer insertBackgroundModel(String name, int kmerLen, String modelType, Connection cxn) throws SQLException {
    PreparedStatement insertBG = cxn.prepareStatement("insert into background_model(id,name,max_kmer_len,model_type) values (weightmatrix_id.nextval,?,?,?)");
    insertBG.setString(1, name);
    insertBG.setInt(2, kmerLen);
    insertBG.setString(3, modelType);
    insertBG.execute();
    insertBG.close();
    PreparedStatement getModelID = cxn.prepareStatement("select weightmatrix_id.currval from dual");
    ResultSet rs = getModelID.executeQuery();
    try {
      if (rs.next()) {
        return rs.getInt(1);
      }
      else {
        throw new SQLException("Failed to get Model ID following insert into Background_Model table.");
      }    
    }
    finally {
      rs.close();
      getModelID.close();
    }
  }
  
  
  /**
   * insert the model in the background model genome map table
   * @param modelID the background model table ID of the model
   * @param genomeID the DBID of the genome the model is for
   * @param cxn an open database connection
   * @return the
   * @throws SQLException
   */
  private static Integer insertBackgroundGenomeMap(int modelID, int genomeID, boolean hasCounts, Connection cxn) throws SQLException {
    PreparedStatement insertMap = 
      cxn.prepareStatement("insert into background_genome_map(id, genome_id, bg_model_id, has_counts) values (weightmatrix_id.nextval, ?, ?, ?)");
    insertMap.setInt(1, genomeID);
    insertMap.setInt(2, modelID);
    if (hasCounts) {
      insertMap.setInt(3, 1);      
    }
    else {
      insertMap.setInt(3, 0);
    }
    
    insertMap.execute();
    insertMap.close();
    PreparedStatement getMapID = cxn.prepareStatement("select weightmatrix_id.currval from dual");
    ResultSet rs = getMapID.executeQuery();
    try {
      if (rs.next()) {
        return rs.getInt(1);
      }
      else {
        throw new SQLException("Failed to get Background Model Genome Map ID following insert into Background_Genome_Map table.");
      }
    }
    finally {
      rs.close();
      getMapID.close();
    }
  }
  
  
  /**
   * remove the model's kmer probabilities 
   * @param mapID the model's ID in the background genome map table
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void removeModelColumns(int mapID, Connection cxn) throws SQLException {
  	PreparedStatement deleteOld = cxn.prepareStatement("delete from background_model_cols where bggm_id = ?");
    try {
      deleteOld.setInt(1,mapID);
      deleteOld.execute();
    }
    finally {
      deleteOld.close();
    }
  }
  
  
  /**
   * insert the model's kmer probabilities 
   * @param model the background model
   * @param mapID the model's ID in the background genome map table
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void insertMarkovModelColumns(MarkovBackgroundModel model, int mapID, Connection cxn) throws SQLException {
    PreparedStatement insertCol = cxn.prepareStatement("insert into background_model_cols(bggm_id,kmer,probability) values(?,?,?)");
    try {
      for (int i = 1; i <= model.getMaxKmerLen(); i++) {
        for (String kmer : model.getKmers(i)) {
          double prob = model.getMarkovProb(kmer);

          insertCol.setInt(1, mapID);
          insertCol.setString(2, kmer);
          insertCol.setDouble(3, prob);
          insertCol.execute();
        }
      }
    }
    finally {
      insertCol.close();
    }
  }
  
  
  /**
   * insert the model's kmer probabilities 
   * @param model the background model
   * @param mapID the model's ID in the background genome map table
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void insertFrequencyModelColumns(FrequencyBackgroundModel model, int mapID, Connection cxn) throws SQLException {
    PreparedStatement insertCol = cxn.prepareStatement("insert into background_model_cols(bggm_id,kmer,probability) values(?,?,?)");
    try {
      for (int i = 1; i <= model.getMaxKmerLen(); i++) {
        for (String kmer : model.getKmers(i)) {
          double prob = model.getFrequency(kmer);

          insertCol.setInt(1, mapID);
          insertCol.setString(2, kmer);
          insertCol.setDouble(3, prob);
          insertCol.execute();
        }
      }
    }
    finally {
      insertCol.close();
    }
  }
  

  /**
   * insert the model's kmer probabilities 
   * @param model the background model
   * @param mapID the model's ID in the background genome map table
   * @param insertAsMarkov if true then insert the markov probabilities, if
   * false then insert the kmer frequencies
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void insertCountsModelColumns(CountsBackgroundModel model, int mapID, boolean insertAsMarkov, Connection cxn) throws SQLException {
    PreparedStatement insertCol = cxn.prepareStatement("insert into background_model_cols(bggm_id,kmer,probability,count) values(?,?,?,?)");
    try {
      for (int i = 1; i <= model.getMaxKmerLen(); i++) {
        for (String kmer : model.getKmers(i)) {
          double prob;
          if (insertAsMarkov) {
            prob = model.getMarkovProb(kmer);
          }
          else {
            prob = model.getFrequency(kmer);
          }          

          insertCol.setInt(1, mapID);
          insertCol.setString(2, kmer);
          insertCol.setDouble(3, prob);
          insertCol.setLong(4, model.getKmerCount(kmer));
          insertCol.execute();
        }
      }
    }
    finally {
      insertCol.close();
    }
  }
  
  
  /**************************************************************************
   * Mainline, test code, and parse/insert method
   **************************************************************************/
 
  
  public static void main(String[] args) {
    ClassLoader loader = BackgroundModelLoader.class.getClassLoader();
    PropertyConfigurator.configure(loader.getResource("edu/mit/csail/cgs/utils/config/log4j.properties"));      
    
    //BackgroundModelLoader.testDBLoading();
    BackgroundModelLoader.importModel(args);
  }
  
  
  /** 
   * Imports a background model from a file into the DB.
   * File format is
   * 1 A .2
   * 2 C .3
   * 3 G .3
   * 4 T .2
   * 5 AA ....
   *
   * Usage:
   * java edu.psu.compbio.seqcode.gse.datasets.motifs.BackgroundModelLoader --genome "Mus musculus;mm8" --bgname "whole genome" --bgtype MARKOV --bgfile foo.back
   */
  public static void importModel(String args[]) {
    try {
      Genome gen = null;
      String bgName = null;
      String bgType = null;
      String bgFilename = null;
      
      //args = new String[] {"--species", "Mus musculus;mm5", "--bgname", "test1", "--bgtype", MARKOV_TYPE_STRING, "--bgfile", "mm8_2.back"};
      //      args = new String[] {"--species", "Saccharomyces cerevisiae;sacCer1", "--bgname", "test2", "--bgtype", MARKOV_TYPE_STRING, "--bgfile", "yeast1.back"};
      //args = new String[] {"--species", "Homo sapiens;hg17", "--bgname", "test1", "--bgtype", MARKOV_TYPE_STRING, "--bgfile", "human_1.back"};
      //args = new String[] {"--species", "Mus musculus;mm5", "--bgname", "test1", "--bgtype", FREQUENCY_TYPE_STRING, "--bgfile", "testfreq3.back"};
      //args = new String[] {"--species", "Mus musculus;mm5", "--bgname", "test4", "--bgtype", "COUNTS;FREQUENCY", "--bgfile", "testcount2.back"};
      
      
      gen = Args.parseGenome(args).cdr();
      bgName = Args.parseString(args, "bgname", null);
      bgType = Args.parseString(args, "bgtype", null);
      bgFilename = Args.parseString(args, "bgfile", null);

      if (gen == null) {
        logger.fatal("Must specify a genome in --species"); System.exit(1);
      }
      if (bgFilename == null) {
        logger.fatal("Must supply a --bgfile"); System.exit(1);
      } 
      if (bgName == null) {
        logger.fatal("Must supply a --bgname"); System.exit(1);
      }
      if (bgType == null) {
        logger.fatal("Must supply a --bgtype"); System.exit(1);
      }

      Pair<Integer, Integer> ids = null;
      if (bgType.toUpperCase().equals(MARKOV_TYPE_STRING)) {
        MarkovBackgroundModel mbg = BackgroundModelIO.parseMarkovBackgroundModel(bgName, bgFilename, gen);
        ids = BackgroundModelLoader.insertMarkovModel(mbg);        
      }
      else if (bgType.toUpperCase().equals(FREQUENCY_TYPE_STRING)) {
        FrequencyBackgroundModel fbg = BackgroundModelIO.parseFrequencyBackgroundModel(bgName, bgFilename, gen);
        ids = BackgroundModelLoader.insertFrequencyModel(fbg);
      }
      else if (bgType.toUpperCase().equals("COUNTS;MARKOV")) {
        CountsBackgroundModel cbg = BackgroundModelIO.parseCountsBackgroundModel(bgName, bgFilename, gen);
        ids = BackgroundModelLoader.insertCountsModel(cbg, true);
      }
      else if (bgType.toUpperCase().equals("COUNTS;FREQUENCY")) {
        CountsBackgroundModel cbg = BackgroundModelIO.parseCountsBackgroundModel(bgName, bgFilename, gen);
        ids = BackgroundModelLoader.insertCountsModel(cbg, false);
      }
      else {
        logger.fatal("Background type must be one of: Markov, Frequency, Counts;Markov, Counts;Frequency.");
        System.exit(1);
      }
      logger.debug(ids.car() + " " + ids.cdr());
    }
    catch (NotFoundException nfex) {
      logger.fatal(nfex);
      System.exit(1);
    }
    catch (IOException ioex) {
      logger.fatal(ioex);
      System.exit(1);
    }
    catch (ParseException pex) {
      logger.fatal(pex);      
      System.exit(1);
    }
    catch (SQLException sqlex) {
      logger.fatal(sqlex);
      sqlex.printStackTrace();
      System.exit(1);
    }
    catch (CGSException cgsex) {
      logger.fatal(cgsex);
      System.exit(1);
    }
    System.exit(0);
  }

  
  private static void testDBLoading() {    
    MarkovBackgroundModel testValuesMM8_3 = null;
    MarkovBackgroundModel testValuesMM8_2 = null;
    MarkovBackgroundModel testValuesYeast_3 = null;
    MarkovBackgroundModel testValuesYeast_2 = null;
    MarkovBackgroundModel testValuesHuman_2 = null;
    FrequencyBackgroundModel testFreqValuesMM8_3 = null;
    FrequencyBackgroundModel testFreqValuesMM8_2 = null;
    CountsBackgroundModel testCountValuesMM8_3 = null;
    CountsBackgroundModel testCountValuesMM8_2 = null;

    try {
      testValuesMM8_3 = BackgroundModelIO.parseMarkovBackgroundModel("mm8.back", Organism.findGenome("mm8"));
      testValuesMM8_2 = BackgroundModelIO.parseMarkovBackgroundModel("mm8_1.back", Organism.findGenome("mm8"));
      testValuesYeast_3 = BackgroundModelIO.parseMarkovBackgroundModel("yeast2.back", Organism.findGenome("sacCer1"));
      testValuesYeast_2 = BackgroundModelIO.parseMarkovBackgroundModel("yeast1.back", Organism.findGenome("sacCer1"));
      testValuesHuman_2 = BackgroundModelIO.parseMarkovBackgroundModel("human_1.back", Organism.findGenome("hg17"));
    
      testFreqValuesMM8_3 = BackgroundModelIO.parseFrequencyBackgroundModel("testfreq3.back", Organism.findGenome("mm8"));
      testFreqValuesMM8_2 = BackgroundModelIO.parseFrequencyBackgroundModel("testfreq2.back", Organism.findGenome("mm8"));

      testCountValuesMM8_3 = BackgroundModelIO.parseCountsBackgroundModel("testcount3.back", Organism.findGenome("mm8"));
      testCountValuesMM8_2 = BackgroundModelIO.parseCountsBackgroundModel("testcount2.back", Organism.findGenome("mm8"));
    }
    catch (IOException ex) {
      logger.fatal(ex);
    }
    catch (ParseException ex) {
      logger.fatal(ex);
    }
    catch (NotFoundException nfex) {
      logger.fatal(nfex);
    }

    
    try {
      //test1: 5370, 22, 5369, test1, 3, MARKOV
      //test1: 5372, 22, 5371, test1, 2, MARKOV
      //test2: 5374, 23, 5373, test2, 3, MARKOV
      //test2: 5376, 23, 5375, test2, 2, MARKOV
      
      int expectedMapID_test1_2 = 5372;
      int expectedGenomeID_test1_2 = 22;
      int expectedModelID_test1_2 = 5371;
      String expectedName_test1_2 = "test1";
      int expectedKmerLen_test1_2 = 2;
      String expectedType_test1_2 = MARKOV_TYPE_STRING;
      
      int expectedFreqMapID_test1_2 = 5379;
      int expectedFreqModelID_test1_2 = 5378;
      
      int expectedCountMapID_test3_2 = 5385;
      int expectedCountModelID_test3_2 = 5384;
      String expectedCountName_test3_2 = "test3";
      String expectedCountName_test4_2 = "test4";

      logger.debug("Testing getAllBackgroundModels()");
      List<BackgroundModelMetadata> foo = BackgroundModelLoader.getAllBackgroundModels();
      for (BackgroundModelMetadata md : foo) {
        System.out.println(md.toString());        
      }
      
      logger.debug("Testing getAllBackgroundModels(ignoreGenome)");
      foo = BackgroundModelLoader.getAllBackgroundModels(true);
      for (BackgroundModelMetadata md : foo) {
        System.out.println(md.toString());        
      }
      
      logger.debug("Testing getBackgroundModelsForGenome(genomeID)");
      foo = BackgroundModelLoader.getBackgroundModelsForGenome(expectedGenomeID_test1_2); 
      for (BackgroundModelMetadata md : foo) {
        System.out.println(md.toString());        
      }
      
      logger.debug("Testing getGenomesForBackgroundModel(modelID)");
      List<Integer> bar = BackgroundModelLoader.getGenomesForBackgroundModel(expectedModelID_test1_2); //mouse
      for (int genomeID : bar) {
        System.out.println(genomeID);        
      }
      
      
      BackgroundModelMetadata expectedMDModelOnly_test1_2 = new BackgroundModelMetadata(expectedModelID_test1_2, expectedName_test1_2, expectedKmerLen_test1_2, expectedType_test1_2);
      BackgroundModelMetadata expectedMD_test1_2 = new BackgroundModelMetadata(expectedMapID_test1_2, expectedGenomeID_test1_2, 
          expectedModelID_test1_2, expectedName_test1_2, 
          expectedKmerLen_test1_2, expectedType_test1_2, false);
      
      BackgroundModelMetadata expectedFreqMDModelOnly_test1_2 = new BackgroundModelMetadata(expectedFreqModelID_test1_2, expectedName_test1_2, expectedKmerLen_test1_2, FREQUENCY_TYPE_STRING);
      BackgroundModelMetadata expectedFreqMD_test1_2 = new BackgroundModelMetadata(expectedFreqMapID_test1_2, expectedGenomeID_test1_2, 
          expectedFreqModelID_test1_2, expectedName_test1_2, 
          expectedKmerLen_test1_2, FREQUENCY_TYPE_STRING, false);

      BackgroundModelMetadata expectedCountMDModelOnly_test3_2 = new BackgroundModelMetadata(expectedCountModelID_test3_2, expectedCountName_test3_2, expectedKmerLen_test1_2, FREQUENCY_TYPE_STRING);
      BackgroundModelMetadata expectedCountMD_test3_2 = new BackgroundModelMetadata(expectedCountMapID_test3_2, expectedGenomeID_test1_2, 
          expectedCountModelID_test3_2, expectedCountName_test3_2, 
          expectedKmerLen_test1_2, FREQUENCY_TYPE_STRING, false);

      BackgroundModelMetadata expectedCountMD_test4_2 = new BackgroundModelMetadata(expectedCountMapID_test3_2, expectedGenomeID_test1_2, 
          expectedCountModelID_test3_2, expectedCountName_test4_2, 
          expectedKmerLen_test1_2, FREQUENCY_TYPE_STRING, false);

      
      int expectedMapID_test1_3 = 5370;
      int expectedGenomeID_test1_3 = 22;
      int expectedModelID_test1_3 = 5369;
      String expectedName_test1_3 = "test1";
      int expectedKmerLen_test1_3 = 3;
      String expectedType_test1_3 = MARKOV_TYPE_STRING;
      
      int expectedFreqMapID_test1_3 = 5381;
      int expectedFreqModelID_test1_3 = 5380;
      
      int expectedCountMapID_test3_3 = 5383;
      int expectedCountModelID_test3_3 = 5382;
      String expectedCountName_test3_3 = "test3";

      
      BackgroundModelMetadata expectedMDModelOnly_test1_3 = new BackgroundModelMetadata(expectedModelID_test1_3, expectedName_test1_3, expectedKmerLen_test1_3, expectedType_test1_3);
      BackgroundModelMetadata expectedMD_test1_3 = new BackgroundModelMetadata(expectedMapID_test1_3, expectedGenomeID_test1_3, 
          expectedModelID_test1_3, expectedName_test1_3, 
          expectedKmerLen_test1_3, expectedType_test1_3, false);
      BackgroundModelMetadata expectedFreqMD_test1_3 = new BackgroundModelMetadata(expectedFreqMapID_test1_3, expectedGenomeID_test1_3, 
          expectedFreqModelID_test1_3, expectedName_test1_3, 
          expectedKmerLen_test1_3, FREQUENCY_TYPE_STRING, false);
      BackgroundModelMetadata expectedCountMD_test3_3 = new BackgroundModelMetadata(expectedCountMapID_test3_3, expectedGenomeID_test1_3, 
          expectedCountModelID_test3_3, expectedCountName_test3_3, 
          expectedKmerLen_test1_3, FREQUENCY_TYPE_STRING, false);

      
      int expectedMapID_test2_3 = 5374;
      int expectedGenomeID_test2_3 = 23;
      int expectedModelID_test2_3 = 5373;
      String expectedName_test2_3 = "test2";
      int expectedKmerLen_test2_3 = 3;
      String expectedType_test2_3 = MARKOV_TYPE_STRING;
      
      BackgroundModelMetadata expectedMDModelOnly_test2_3 = new BackgroundModelMetadata(expectedModelID_test2_3, expectedName_test2_3, expectedKmerLen_test2_3, expectedType_test2_3);
      BackgroundModelMetadata expectedMD_test2_3 = new BackgroundModelMetadata(expectedMapID_test2_3, expectedGenomeID_test2_3, 
          expectedModelID_test2_3, expectedName_test2_3, 
          expectedKmerLen_test2_3, expectedType_test2_3, false);
      
      
      
      //test getBackgroundModelID(name, kmerlen, type)
      BackgroundModelLoader.getBackgroundModelID(expectedName_test1_3, expectedKmerLen_test1_3, expectedType_test1_3);
      if (BackgroundModelLoader.getBackgroundModelID(expectedName_test1_3, expectedKmerLen_test1_3, expectedType_test1_3) == expectedModelID_test1_3) {
        logger.debug("OK: getBackgroundModelID(name, kmerlen, type)");        
      }
      else {
        logger.debug("FAIL: getBackgroundModelID(name, kmerlen, type)");
      }

      //test getBackgroundModelID(name, kmerlen, type)
      if (BackgroundModelLoader.getBackgroundGenomeMapID(expectedModelID_test1_3, expectedGenomeID_test1_3) == expectedMapID_test1_3) {
        logger.debug("OK: getBackgroundGenomeMapID(modelID, genomeID)");        
      }
      else {
        logger.debug("FAIL: getBackgroundGenomeMapID(modelID, genomeID)");
      }

      //test getBackgroundModelByModelID(modelID)
      if (expectedMDModelOnly_test1_3.equals(BackgroundModelLoader.getBackgroundModelByModelID(expectedModelID_test1_3))) {
        logger.debug("OK: getBackgroundModelByModelID(modelID)");        
      }
      else {
        logger.debug("FAIL: getBackgroundModelByModelID(modelID)");
      }

      //test getBackgroundModel(modelID, genomeID)
      if (expectedMD_test1_3.equals(BackgroundModelLoader.getBackgroundModel(expectedModelID_test1_3, expectedGenomeID_test1_3))) {
        logger.debug("OK: getBackgroundModel(modelID, genomeID)");        
      }
      else {
        logger.debug("FAIL: getBackgroundModel(modelID, genomeID)");
      }

      //test getBackgroundModel(name, kmerlen, type, genomeID)
      if (expectedMD_test1_3.equals(BackgroundModelLoader.getBackgroundModel(expectedName_test1_3, expectedKmerLen_test1_3, expectedType_test1_3, expectedGenomeID_test1_3))) {
        logger.debug("OK: getBackgroundModel(name, kmerlen, type, genomeID)");        
      }
      else {
        logger.debug("FAIL: getBackgroundModel(name, kmerlen, type, genomeID)");
      }

      //test getBackgroundModelByMapID(mapID)
      if (expectedMD_test1_3.equals(BackgroundModelLoader.getBackgroundModelByMapID(expectedMapID_test1_3))) {
        logger.debug("OK: getBackgroundModelByMapID(mapID)");        
      }
      else {
        logger.debug("FAIL: getBackgroundModelByMapID(mapID)");
      }

      /**********************************************************************
       * Done with basic tests for getting ids and metadata
       **********************************************************************/

      /**********************************************************************
       * Test methods for getting Markov Models
       **********************************************************************/
      
      
      //test getMarkovModel(metadata)
      MarkovBackgroundModel mbm = BackgroundModelLoader.getMarkovModel(expectedMD_test1_3);
      if (mbm.equalValues(testValuesMM8_3)) {
        logger.debug("OK: getMarkovModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getMarkovModel(metadata)");
      }
      MarkovBackgroundModel mbm2 = BackgroundModelLoader.getMarkovModel(expectedMD_test2_3);
      if (mbm2.equalValues(testValuesYeast_3)) {
        logger.debug("OK: getMarkovModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getMarkovModel(metadata)");
      }
      
      //test getMarkovModels methods
      List<MarkovBackgroundModel> mbmList;      
      
      mbmList = BackgroundModelLoader.getMarkovModels(expectedMDModelOnly_test1_2);
      if ((mbmList.size() == 2) && mbmList.get(0).equalValues(testValuesHuman_2) && mbmList.get(1).equalValues(testValuesMM8_2)) {          
        logger.debug("OK: getMarkovModels(metadata)");        
      }
      else {
        logger.debug("FAIL: getMarkovModels(metadata)");
      }

      mbmList = BackgroundModelLoader.getMarkovModels(expectedName_test1_3);
      if ((mbmList.size() == 3) && mbmList.get(1).equalValues(testValuesMM8_2) && mbmList.get(2).equalValues(testValuesMM8_3)
          && mbmList.get(0).equalValues(testValuesHuman_2)) { 
        logger.debug("OK: getMarkovModels(name)");        
      }
      else {
        logger.debug("FAIL: getMarkovModels(name)");
      }
      
      mbmList = BackgroundModelLoader.getMarkovModels(expectedName_test1_3, 2);
      if ((mbmList.size() == 2) && mbmList.get(1).equalValues(testValuesMM8_2) && mbmList.get(0).equalValues(testValuesHuman_2)) { 
        logger.debug("OK: getMarkovModels(name, kmerlen)");        
      }
      else {
        logger.debug("FAIL: getMarkovModels(name, kmerlen)");
      }
      
      mbmList = BackgroundModelLoader.getMarkovModels(expectedModelID_test1_2);
      if ((mbmList.size() == 2) && mbmList.get(0).equalValues(testValuesHuman_2) && mbmList.get(1).equalValues(testValuesMM8_2)) { 
        logger.debug("OK: getMarkovModels(modelID)");        
      }
      else {
        logger.debug("FAIL: getMarkovModels(modelID)");
      }

      mbmList = BackgroundModelLoader.getMarkovModelsByLength(2);
      if ((mbmList.size() == 3) && mbmList.get(1).equalValues(testValuesMM8_2) && mbmList.get(2).equalValues(testValuesYeast_2)
          && mbmList.get(0).equalValues(testValuesHuman_2)) { 
        logger.debug("OK: getMarkovModelsByLength(kmerlen)");        
      }
      else {
        logger.debug("FAIL: getMarkovModelsByLength(kmerlen)");
      }
      
      mbmList = BackgroundModelLoader.getMarkovModelsByGenome(expectedGenomeID_test1_2);
      if ((mbmList.size() == 2) && mbmList.get(0).equalValues(testValuesMM8_2) && mbmList.get(1).equalValues(testValuesMM8_3)) {          
        logger.debug("OK: getMarkovModelsByGenome(genomeID)");        
      }
      else {
        logger.debug("FAIL: getMarkovModelsByGenome(genomeID)");
      }
      
      mbmList = BackgroundModelLoader.getMarkovModelsByGenome(expectedGenomeID_test1_2, expectedName_test1_2);
      if ((mbmList.size() == 2) && mbmList.get(0).equalValues(testValuesMM8_2) && mbmList.get(1).equalValues(testValuesMM8_3)) {          
        logger.debug("OK: getMarkovModelsByGenome(genomeID, name)");        
      }
      else {
        logger.debug("FAIL: getMarkovModelsByGenome(genomeID, name)");
      }

      mbmList = BackgroundModelLoader.getMarkovModelsByGenome(expectedGenomeID_test1_2, expectedKmerLen_test1_2);
      if ((mbmList.size() == 1) && mbmList.get(0).equalValues(testValuesMM8_2)) {          
        logger.debug("OK: getMarkovModelsByGenome(genomeID, kmerlen)");        
      }
      else {
        logger.debug("FAIL: getMarkovModelsByGenome(genomeID, kmerlen)");
      }
      
      
      
      //test getFrequencyModel(metadata)
      FrequencyBackgroundModel fbm = BackgroundModelLoader.getFrequencyModel(expectedFreqMD_test1_2);
      if (fbm.equalValues(testFreqValuesMM8_2)) {
        logger.debug("OK: getFrequencyModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModel(metadata)");
      }
      FrequencyBackgroundModel fbm2 = BackgroundModelLoader.getFrequencyModel(expectedFreqMD_test1_3);
      if (fbm2.equalValues(testFreqValuesMM8_3)) {
        logger.debug("OK: getFrequencyModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModel(metadata)");
      }
      
      
      //test getFrequencyModels methods
      List<FrequencyBackgroundModel> fbmList;      
      
      fbmList = BackgroundModelLoader.getFrequencyModels(expectedFreqMDModelOnly_test1_2);
      if ((fbmList.size() == 1) && fbmList.get(0).equalValues(testFreqValuesMM8_2)) {          
        logger.debug("OK: getFrequencyModels(metadata)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModels(metadata)");
      }

      fbmList = BackgroundModelLoader.getFrequencyModels(expectedName_test1_3);
      if ((fbmList.size() == 2) && fbmList.get(0).equalValues(testFreqValuesMM8_2) && fbmList.get(1).equalValues(testFreqValuesMM8_3)) {
        logger.debug("OK: getFrequencyModels(name)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModels(name)");
      }
      
      fbmList = BackgroundModelLoader.getFrequencyModels(expectedName_test1_3, 2);
      if ((fbmList.size() == 1) && fbmList.get(0).equalValues(testFreqValuesMM8_2)) { 
        logger.debug("OK: getFrequencyModels(name, kmerlen)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModels(name, kmerlen)");
      }
      
      fbmList = BackgroundModelLoader.getFrequencyModels(expectedFreqModelID_test1_2);
      if ((fbmList.size() == 1) && fbmList.get(0).equalValues(testFreqValuesMM8_2)) { 
        logger.debug("OK: getFrequencyModels(modelID)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModels(modelID)");
      }

//      fbmList = BackgroundModelLoader.getFrequencyModelsByLength(2);
//      if ((fbmList.size() == 3) && fbmList.get(0).equalValues(testFreqValuesMM8_2) && fbmList.get(1).equalValues(testFreqValuesMM8_2)) {
//        logger.debug("OK: getFrequencyModelsByLength(kmerlen)");        
//      }
//      else {
//        logger.debug("FAIL: getFrequencyModelsByLength(kmerlen)");
//      }
      
      fbmList = BackgroundModelLoader.getFrequencyModelsByGenome(expectedGenomeID_test1_2);
      if ((fbmList.size() == 5) && fbmList.get(0).equalValues(testFreqValuesMM8_2) && fbmList.get(1).equalValues(testFreqValuesMM8_3)
          && fbmList.get(2).equalValues(testFreqValuesMM8_2) && fbmList.get(3).equalValues(testFreqValuesMM8_3)
          && fbmList.get(4).equalValues(testFreqValuesMM8_2)) {          
        logger.debug("OK: getFrequencyModelsByGenome(genomeID)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModelsByGenome(genomeID)");
      }
      
      fbmList = BackgroundModelLoader.getFrequencyModelsByGenome(expectedGenomeID_test1_2, expectedName_test1_2);
      if ((fbmList.size() == 2) && fbmList.get(0).equalValues(testFreqValuesMM8_2) && fbmList.get(1).equalValues(testFreqValuesMM8_3)) {          
        logger.debug("OK: getFrequencyModelsByGenome(genomeID, name)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModelsByGenome(genomeID, name)");
      }

      fbmList = BackgroundModelLoader.getFrequencyModelsByGenome(expectedGenomeID_test1_2, expectedKmerLen_test1_2);
      if ((fbmList.size() == 3) && fbmList.get(0).equalValues(testFreqValuesMM8_2) && fbmList.get(1).equalValues(testFreqValuesMM8_2)
          && fbmList.get(2).equalValues(testFreqValuesMM8_2)) {          
        logger.debug("OK: getFrequencyModelsByGenome(genomeID, kmerlen)");        
      }
      else {
        logger.debug("FAIL: getFrequencyModelsByGenome(genomeID, kmerlen)");
      }

      
      
      
      //test getCountsModel(metadata)
      CountsBackgroundModel cbm = BackgroundModelLoader.getCountsModel(expectedCountMD_test3_2);
      if (cbm.equalValues(testCountValuesMM8_2)) {
        logger.debug("OK: getCountsModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getCountsModel(metadata)");
      }
      CountsBackgroundModel cbm2 = BackgroundModelLoader.getCountsModel(expectedCountMD_test3_3);
      if (cbm2.equalValues(testCountValuesMM8_3)) {
        logger.debug("OK: getCountsModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getCountsModel(metadata)");
      }
      CountsBackgroundModel cbm3 = BackgroundModelLoader.getCountsModel(expectedCountMD_test4_2);
      if (cbm3.equalValues(testCountValuesMM8_2)) {
        logger.debug("OK: getCountsModel(metadata)");        
      }
      else {
        logger.debug("FAIL: getCountsModel(metadata)");
      }
      
      
      //test getCountsModels methods
      List<CountsBackgroundModel> cbmList;      
      
      cbmList = BackgroundModelLoader.getCountsModels(expectedCountMDModelOnly_test3_2);
      if ((cbmList.size() == 1) && cbmList.get(0).equalValues(testCountValuesMM8_2)) {          
        logger.debug("OK: getCountsModels(metadata)");        
      }
      else {
        logger.debug("FAIL: getCountsModels(metadata)");
      }

      cbmList = BackgroundModelLoader.getCountsModels(expectedCountName_test3_3);
      if ((cbmList.size() == 2) && cbmList.get(0).equalValues(testCountValuesMM8_2) && cbmList.get(1).equalValues(testCountValuesMM8_3)) {
        logger.debug("OK: getCountsModels(name)");        
      }
      else {
        logger.debug("FAIL: getCountsModels(name)");
      }
      
      cbmList = BackgroundModelLoader.getCountsModels(expectedCountName_test3_3, 2);
      if ((cbmList.size() == 1) && cbmList.get(0).equalValues(testCountValuesMM8_2)) { 
        logger.debug("OK: getCountsModels(name, kmerlen)");        
      }
      else {
        logger.debug("FAIL: getCountsModels(name, kmerlen)");
      }
      
      cbmList = BackgroundModelLoader.getCountsModels(expectedCountModelID_test3_2);
      if ((cbmList.size() == 1) && cbmList.get(0).equalValues(testCountValuesMM8_2)) { 
        logger.debug("OK: getCountsModels(modelID)");        
      }
      else {
        logger.debug("FAIL: getCountsModels(modelID)");
      }

//      cbmList = BackgroundModelLoader.getCountsModelsByLength(2);
//      if ((cbmList.size() == 1) && cbmList.get(0).equalValues(testCountValuesMM8_2)) {
//        logger.debug("OK: getCountsModelsByLength(kmerlen)");        
//      }
//      else {
//        logger.debug("FAIL: getCountsModelsByLength(kmerlen)");
//      }
      
      cbmList = BackgroundModelLoader.getCountsModelsByGenome(expectedGenomeID_test1_2);
      if ((cbmList.size() == 3) && cbmList.get(0).equalValues(testCountValuesMM8_2) && cbmList.get(1).equalValues(testCountValuesMM8_3)
          && cbmList.get(2).equalValues(testCountValuesMM8_2)) {          
        logger.debug("OK: getCountsModelsByGenome(genomeID)");        
      }
      else {
        logger.debug("FAIL: getCountsModelsByGenome(genomeID)");
      }
      
      cbmList = BackgroundModelLoader.getCountsModelsByGenome(expectedGenomeID_test1_2, expectedCountName_test3_2);
      if ((cbmList.size() == 2) && cbmList.get(0).equalValues(testCountValuesMM8_2) && cbmList.get(1).equalValues(testCountValuesMM8_3)) {          
        logger.debug("OK: getCountsModelsByGenome(genomeID, name)");        
      }
      else {
        logger.debug("FAIL: getCountsModelsByGenome(genomeID, name)");
      }

      cbmList = BackgroundModelLoader.getCountsModelsByGenome(expectedGenomeID_test1_2, expectedKmerLen_test1_2);
      if ((cbmList.size() == 2) && cbmList.get(0).equalValues(testCountValuesMM8_2) && cbmList.get(1).equalValues(testCountValuesMM8_2)) {          
        logger.debug("OK: getCountsModelsByGenome(genomeID, kmerlen)");        
      }
      else {
        logger.debug("FAIL: getCountsModelsByGenome(genomeID, kmerlen)");
      }

    }
    catch (NotFoundException nfex) {
      logger.fatal(nfex);
    }
    catch (SQLException ex) {
      logger.fatal(ex);
    }
  }
}
