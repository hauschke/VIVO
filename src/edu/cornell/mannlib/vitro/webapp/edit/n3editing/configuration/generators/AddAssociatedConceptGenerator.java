/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.generators;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vivoweb.webapp.util.ModelUtils;

import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.DateTimeWithPrecisionVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.DateTimeIntervalValidationVTwo;

import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.dao.jena.QueryUtils;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.XSD;
import com.hp.hpl.jena.ontology.OntModel;
import edu.cornell.mannlib.vitro.webapp.beans.DataProperty;
import edu.cornell.mannlib.vitro.webapp.beans.DataPropertyStatement;
import edu.cornell.mannlib.vitro.webapp.beans.Individual;
import edu.cornell.mannlib.vitro.webapp.beans.ObjectProperty;
import edu.cornell.mannlib.vitro.webapp.beans.VClass;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.ResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.TemplateResponseValues;
import edu.cornell.mannlib.vitro.webapp.dao.DisplayVocabulary;
import edu.cornell.mannlib.vitro.webapp.dao.VitroVocabulary;
import edu.cornell.mannlib.vitro.webapp.dao.WebappDaoFactory;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.Field;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.preprocessors.RoleToActivityPredicatePreprocessor;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.processEdit.RdfLiteralHash;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditN3GeneratorVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.SelectListGeneratorVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.FieldVTwo;
import edu.cornell.mannlib.vitro.webapp.web.MiscWebUtils;
import edu.cornell.mannlib.vitro.webapp.search.beans.ProhibitedFromSearch;
import edu.cornell.mannlib.vitro.webapp.utils.FrontEndEditingUtils;
import edu.cornell.mannlib.vitro.webapp.utils.FrontEndEditingUtils.EditMode;
import edu.cornell.mannlib.vitro.webapp.utils.generators.EditModeUtils;
/**
 * Generates the edit configuration for importing concepts from external
 * search services, e.g. UMLS etc.      
 * 
 * The N3 for this is set with the default settinf of 
 *
 */
public class AddAssociatedConceptGenerator  extends VivoBaseGenerator implements EditConfigurationGenerator {
	
	private Log log = LogFactory.getLog(AddAssociatedConceptGenerator.class);
	private boolean isObjectPropForm = false;
	private String subjectUri = null;
	private String predicateUri = null;
	private String objectUri = null;
	private String datapropKeyStr= null;
	private int dataHash = 0;
	private DataPropertyStatement dps = null;
	private String dataLiteral = null;
	private String template = "addAssociatedConcept.ftl";
	private static HashMap<String,String> defaultsForXSDtypes ;
	
	
    @Override
    public EditConfigurationVTwo getEditConfiguration(VitroRequest vreq, HttpSession session) {
    	EditConfigurationVTwo editConfiguration = new EditConfigurationVTwo();
	  initBasics(editConfiguration, vreq);
      initPropertyParameters(vreq, session, editConfiguration);
      initObjectPropForm(editConfiguration, vreq);               
      
      editConfiguration.setTemplate(template);
      
      setVarNames(editConfiguration);
    	
    	//Assumes this is a simple case of subject predicate var
      editConfiguration.setN3Required(this.generateN3Required(vreq));
    	    	
      //n3 optional
      editConfiguration.setN3Optional(this.generateN3Optional());
    	
	//Todo: what do new resources depend on here?
	//In original form, these variables start off empty
	editConfiguration.setNewResources(generateNewResources(vreq));
	//In scope
	this.setUrisAndLiteralsInScope(editConfiguration, vreq);
	
	//on Form
	this.setUrisAndLiteralsOnForm(editConfiguration, vreq);
	
	editConfiguration.setFilesOnForm(new ArrayList<String>());
    	
    	//Sparql queries
    	this.setSparqlQueries(editConfiguration, vreq);
    	
    	//set fields
    	setFields(editConfiguration, vreq, EditConfigurationUtils.getPredicateUri(vreq));
    	
    
    	setTemplate(editConfiguration, vreq);
    	//No validators required here
        //Add preprocessors
        addPreprocessors(editConfiguration, vreq.getWebappDaoFactory());
        //Adding additional data, specifically edit mode
        addFormSpecificData(editConfiguration, vreq);
        //One override for basic functionality, changing url pattern
        //and entity 
        //Adding term should return to this same page, not the subject
        //Return takes the page back to the individual form
        editConfiguration.setUrlPatternToReturnTo(EditConfigurationUtils.getFormUrlWithoutContext(vreq));
    	return editConfiguration;
    }
    
 

	private void setVarNames(EditConfigurationVTwo editConfiguration) {
		  editConfiguration.setVarNameForSubject("subject");
	      editConfiguration.setVarNameForPredicate("predicate");
	      editConfiguration.setVarNameForObject("conceptNode");
	}

	protected void setTemplate(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq) {
    	editConfiguration.setTemplate(template);
		
	}
   
   
    
   /*
    * N3 Required and Optional Generators as well as supporting methods 
    */
	
	private String getPrefixesString() {
		//TODO: Include dynamic way of including this
		return "@prefix core: <http://vivoweb.org/ontology/core#> .";
	}
	
	//TODO: Check if single string or multiple strings - check rdfslabel form etc. for prefix
	//processing
    private List<String> generateN3Required(VitroRequest vreq) {
    	return list(    	            	        
    	        getPrefixesString() + "\n" +
    	        "?subject ?predicate ?conceptNode .\n" +
    	        "?conceptNode <" + RDFS.label.getURI() + "> ?conceptLabel .\n" + 
    	        "?conceptNode <" + RDFS.isDefinedBy.getURI() + "> ?vocabURI ."
    	);
    }
    
   //Don't think there's any n3 optional here
	private List<String> generateN3Optional() {
		return list(    	            	        
    	);
    }
	
	
  
	
	/*
	 * Get new resources
	 */
	 private Map<String, String> generateNewResources(VitroRequest vreq) {
			HashMap<String, String> newResources = new HashMap<String, String>();
			//There are no new resources here, the concept node uri doesn't
			//get created but already exists, and vocab uri should already exist as well
			return newResources;
		}
    

	
	
	/*
	 * Set URIS and Literals In Scope and on form and supporting methods
	 */
    
    private void setUrisAndLiteralsInScope(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	HashMap<String, List<String>> urisInScope = new HashMap<String, List<String>>();
    	//note that at this point the subject, predicate, and object var parameters have already been processed
    	//these two were always set when instantiating an edit configuration object from json,
    	//although the json itself did not specify subject/predicate as part of uris in scope
    	urisInScope.put(editConfiguration.getVarNameForSubject(), 
    			Arrays.asList(new String[]{editConfiguration.getSubjectUri()}));
    	urisInScope.put(editConfiguration.getVarNameForPredicate(), 
    			Arrays.asList(new String[]{editConfiguration.getPredicateUri()}));
    	//Setting inverse role predicate
    	urisInScope.put("inverseRolePredicate", getInversePredicate(vreq));
    	
    	
    	editConfiguration.setUrisInScope(urisInScope);
    	//Uris in scope include subject, predicate, and object var
    	//literals in scope empty initially, usually populated by code in prepare for update
    	//with existing values for variables
    	editConfiguration.setLiteralsInScope(new HashMap<String, List<Literal>>());
    }
    
    private List<String> getInversePredicate(VitroRequest vreq) {
		List<String> inversePredicateArray = new ArrayList<String>();
		ObjectProperty op = EditConfigurationUtils.getObjectProperty(vreq);
		if(op != null && op.getURIInverse() != null) {
			inversePredicateArray.add(op.getURIInverse());
		}
		return inversePredicateArray;
	}

	//n3 should look as follows
    //?subject ?predicate ?objectVar 
    
    private void setUrisAndLiteralsOnForm(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	List<String> urisOnForm = new ArrayList<String>();
    	List<String> literalsOnForm = new ArrayList<String>();
    	//The URI of the node that defines the concept
    	urisOnForm.add("conceptURI");
    	urisOnForm.add("vocabURI");
    	//Also need to add the label of the concept
    	literalsOnForm.add("conceptLabel");
    	editConfiguration.setLiteralsOnForm(literalsOnForm);
    }
    
    
    /**
     * Set SPARQL Queries and supporting methods
     */
    
    
    private void setSparqlQueries(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	//Sparql queries defining retrieval of literals etc.
    	editConfiguration.setSparqlForAdditionalLiteralsInScope(new HashMap<String, String>());
    	
    	Map<String, String> urisInScope = new HashMap<String, String>();
    	editConfiguration.setSparqlForAdditionalUrisInScope(urisInScope);
    	
    	editConfiguration.setSparqlForExistingLiterals(generateSparqlForExistingLiterals(vreq));
    	editConfiguration.setSparqlForExistingUris(generateSparqlForExistingUris(vreq));
    }
    
    
    //Get page uri for object
    private HashMap<String, String> generateSparqlForExistingUris(VitroRequest vreq) {
    	HashMap<String, String> map = new HashMap<String, String>();
    	//Existing uris here might include is defined by
    	//map.put("vocabURI", getExistingVocabURIQuery());
    	return map;
    }
    
    private String getExistingVocabURIQuery() {
    	String query = "SELECT ?existingVocabURI \n " + 
		"WHERE { \n" +
	      "?conceptNode <" + RDFS.isDefinedBy.getURI() + ">  ?existingVocabURI ."
	      + "}";
		return query;
	}




	private HashMap<String, String> generateSparqlForExistingLiterals(VitroRequest vreq) {
    	HashMap<String, String> map = new HashMap<String, String>();
    	//Queries for existing concept label 
    	//Vocab uri label is something that can be retrieved
    	map.put("conceptLabel", getConceptLabelQuery());
    	return map;
    }

    
   
	private String getConceptLabelQuery() {
		String query = "SELECT ?existingConceptLabel \n " + 
		"WHERE { \n" +
	      "?conceptNode <" + RDFS.label.getURI() + ">  ?existingConceptLabel ."
	      + "}";
		return query;
	}



	/**
	 * 
	 * Set Fields and supporting methods
	 */
	
	private void setFields(EditConfigurationVTwo editConfiguration, VitroRequest vreq, String predicateUri) {
    	setConceptNodeField(editConfiguration, vreq);
    	setConceptLabelField(editConfiguration, vreq);
    	setVocabURIField(editConfiguration, vreq);
    }
    
	//this field will be hidden and include the concept node URI
	private void setConceptNodeField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq) {
		editConfiguration.addField(new FieldVTwo().
				setName("conceptNode").
				setValidators(new ArrayList<String>()).
				setOptionsType("UNDEFINED"));
		
	}



	private void setVocabURIField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq) {
		editConfiguration.addField(new FieldVTwo().
				setName("vocabURI").
				setValidators(new ArrayList<String>()).
				setOptionsType("UNDEFINED"));
	}



	private void setConceptLabelField(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq) {
		editConfiguration.addField(new FieldVTwo().
				setName("conceptLabel").
				setValidators(new ArrayList<String>()).
				setOptionsType("UNDEFINED").
				setRangeDatatypeUri(XSD.xstring.toString())
				);
	}



	

	
    
   
    //Add preprocessor
	
   private void addPreprocessors(EditConfigurationVTwo editConfiguration, WebappDaoFactory wadf) {
	   //Will be a completely different type of preprocessor
	  /*
	   editConfiguration.addEditSubmissionPreprocessor(
			   new RoleToActivityPredicatePreprocessor(editConfiguration, wadf));
	   */
	}
     
   
	//Form specific data
	public void addFormSpecificData(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
		HashMap<String, Object> formSpecificData = new HashMap<String, Object>();
		//Existing concepts should probably be a hash map or a hash map of classes
		//with URI of concept node to label and information about existing URI
		//This would be a sparql query and would need to be run here?
		//For test purposes
		List<AssociatedConceptInfo> testInfo = new ArrayList<AssociatedConceptInfo>();
		testInfo.add(new AssociatedConceptInfo("testLabel", "testURI", "testVocabURI", "testVocabLabel"));
		formSpecificData.put("existingConcepts", testInfo);
		editConfiguration.setFormSpecificData(formSpecificData);
	}
	
	public class AssociatedConceptInfo {
		private String conceptLabel;
		private String conceptURI;
		private String vocabURI;
		private String vocabLabel;
		public AssociatedConceptInfo(String inputLabel, String inputURI, String inputVocabURI, String inputVocabLabel) {
			this.conceptLabel = inputLabel;
			this.conceptURI = inputURI;
			this.vocabURI = inputVocabURI;
			this.vocabLabel = inputVocabLabel;
		}
		
		//Getters
		public String getConceptLabel() {
			return conceptLabel;
		}
		
		public  String getConceptURI() {
			return conceptURI;
		}
		
		public  String getVocabURI() {
			return vocabURI;
		}
		
		public  String getVocabLabel(){
			return vocabLabel;
		}
		
	}
	
	
	
	
}