package org.hl7.fhir.instance.utils;

/*
Copyright (c) 2011+, HL7, Inc
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, 
are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this 
   list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, 
   this list of conditions and the following disclaimer in the documentation 
   and/or other materials provided with the distribution.
 * Neither the name of HL7 nor the names of its contributors may be used to 
   endorse or promote products derived from this software without specific 
   prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

*/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.CodeType;
import org.hl7.fhir.instance.model.DateAndTime;
import org.hl7.fhir.instance.model.UriType;
import org.hl7.fhir.instance.model.ValueSet;
import org.hl7.fhir.instance.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.instance.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.instance.model.ValueSet.FilterOperator;
import org.hl7.fhir.instance.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.instance.model.ValueSet.ValueSetDefineConceptComponent;
import org.hl7.fhir.instance.model.ValueSet.ValueSetExpansionContainsComponent;

public class ValueSetExpanderSimple implements ValueSetExpander {

	private static final int UPPER_LIMIT = 10000;// this value is arbitrary
	
  private Map<String, ValueSet> valuesets = new HashMap<String, ValueSet>();
  private Map<String, ValueSet> codesystems = new HashMap<String, ValueSet>();
  private List<ValueSetExpansionContainsComponent> codes = new ArrayList<ValueSet.ValueSetExpansionContainsComponent>();
  private Map<String, ValueSetExpansionContainsComponent> map = new HashMap<String, ValueSet.ValueSetExpansionContainsComponent>();
  private ValueSet focus;
  private ConceptLocator locator;

	private ValueSetExpanderFactory factory;
  
  public ValueSetExpanderSimple(Map<String, ValueSet> valuesets, Map<String, ValueSet> codesystems, ValueSetExpanderFactory factory, ConceptLocator locator) {
    super();
    this.valuesets = valuesets;
    this.codesystems = codesystems;
    this.factory = factory;
    this.locator = locator;
  }
  
  public ValueSet expand(ValueSet source) throws Exception {
    focus = source.copy();
    focus.setExpansion(new ValueSet.ValueSetExpansionComponent());
    focus.getExpansion().setTimestampSimple(DateAndTime.now());
    
  	
    handleDefine(source);
    if (source.getCompose() != null) 
    	handleCompose(source.getCompose());

    // sanity check on value set size;
    if (map.size() > UPPER_LIMIT)
    	throw new Exception("Value set size of "+Integer.toString(map.size())+" exceeds upper limit of "+Integer.toString(UPPER_LIMIT));
    
    for (ValueSetExpansionContainsComponent c : codes) {
    	if (map.containsKey(key(c))) {
    		focus.getExpansion().getContains().add(c);
    	}
    }
    return focus;
  }

	private void handleCompose(ValueSetComposeComponent compose) throws Exception {
  	for (UriType imp : compose.getImport()) 
  		importValueSet(imp.getValue());
  	for (ConceptSetComponent inc : compose.getInclude()) 
  		includeCodes(inc);
  	for (ConceptSetComponent inc : compose.getExclude()) 
  		excludeCodes(inc);

  }

	private void importValueSet(String value) throws Exception {
	  if (value == null)
	  	throw new Exception("unable to find value set with no identity");
	  ValueSet vs = valuesets.get(value);
	  if (vs == null)
		  	throw new Exception("Unable to find imported value set "+value);
	  vs = factory.getExpander().expand(vs);
	  for (ValueSetExpansionContainsComponent c : vs.getExpansion().getContains()) {
	  	addCode(c.getSystemSimple(), c.getCodeSimple(), c.getDisplaySimple());
	  }	  
  }

	private void includeCodes(ConceptSetComponent inc) throws Exception {
    if (inc.getSystemSimple().equals("http://snomed.info/sct"))
      if (locator != null) {
        addCodes(locator.expand(inc));
        return;
      } else
      throw new Exception("Snomed Expansion is not yet supported (which release?)");
    if (inc.getSystemSimple().equals("http://loinc.org"))
      if (locator != null) {
        addCodes(locator.expand(inc));
        return;
      } else
      throw new Exception("LOINC Expansion is not yet supported (todo)");
	    
	  ValueSet cs = codesystems.get(inc.getSystemSimple());
	  if (cs == null)
	  	throw new Exception("unable to find code system "+inc.getSystemSimple().toString());
	  if (inc.getCode().size() == 0 && inc.getFilter().size() == 0) {
	    // special case - add all the code system
	    for (ValueSetDefineConceptComponent def : cs.getDefine().getConcept()) {
        addCodeAndDescendents(inc.getSystemSimple(), def);
	    }
	  }
	    
	  for (CodeType c : inc.getCode()) {
	  	addCode(inc.getSystemSimple(), c.getValue(), getCodeDisplay(cs, c.getValue()));
	  }
	  if (inc.getFilter().size() > 1)
	    throw new Exception("Multiple filters not handled yet"); // need to and them, and this isn't done yet. But this shouldn't arise in non loinc and snomed value sets
    if (inc.getFilter().size() == 1) {
	    ConceptSetFilterComponent fc = inc.getFilter().get(0);
	  	if ("concept".equals(fc.getPropertySimple()) && fc.getOpSimple() == FilterOperator.isa) {
	  		// special: all non-abstract codes in the target code system under the value
	  		ValueSetDefineConceptComponent def = getConceptForCode(cs.getDefine().getConcept(), fc.getValueSimple());
	  		if (def == null)
	  			throw new Exception("Code '"+fc.getValueSimple()+"' not found in system '"+inc.getSystemSimple()+"'");
	  		addCodeAndDescendents(inc.getSystemSimple(), def);
	  	} else
	  		throw new Exception("not done yet");
	  }
  }

	private void addCodes(List<ValueSetExpansionContainsComponent> expand) throws Exception {
	  if (expand.size() > 500) 
	    throw new Exception("Too many codes to display (>"+Integer.toString(expand.size())+")");
    for (ValueSetExpansionContainsComponent c : expand) {
      addCode(c.getSystemSimple(), c.getCodeSimple(), c.getDisplaySimple());
    }   
  }

	private void addCodeAndDescendents(String system, ValueSetDefineConceptComponent def) {
		if (!ToolingExtensions.hasDeprecated(def)) {  
			if (def.getAbstract() == null || !def.getAbstractSimple())
				addCode(system, def.getCodeSimple(), def.getDisplaySimple());
			for (ValueSetDefineConceptComponent c : def.getConcept()) 
				addCodeAndDescendents(system, c);
		}
  }

	private void excludeCodes(ConceptSetComponent inc) throws Exception {
	  ValueSet cs = codesystems.get(inc.getSystemSimple().toString());
	  if (cs == null)
	  	throw new Exception("unable to find value set "+inc.getSystemSimple().toString());
    if (inc.getCode().size() == 0 && inc.getFilter().size() == 0) {
      // special case - add all the code system
//      for (ValueSetDefineConceptComponent def : cs.getDefine().getConcept()) {
//!!!!        addCodeAndDescendents(inc.getSystemSimple(), def);
//      }
    }
      

	  for (CodeType c : inc.getCode()) {
	  	// we don't need to check whether the codes are valid here- they can't have gotten into this list if they aren't valid
	  	map.remove(key(inc.getSystemSimple(), c.getValue()));
	  }
	  if (inc.getFilter().size() > 0)
	  	throw new Exception("not done yet");
  }

	
	private String getCodeDisplay(ValueSet cs, String code) throws Exception {
		ValueSetDefineConceptComponent def = getConceptForCode(cs.getDefine().getConcept(), code);
		if (def == null)
			throw new Exception("Unable to find code '"+code+"' in code system "+cs.getDefine().getSystemSimple());
		return def.getDisplaySimple();
  }

	private ValueSetDefineConceptComponent getConceptForCode(List<ValueSetDefineConceptComponent> clist, String code) {
		for (ValueSetDefineConceptComponent c : clist) {
			if (code.equals(c.getCodeSimple()))
			  return c;
			ValueSetDefineConceptComponent v = getConceptForCode(c.getConcept(), code);   
			if (v != null)
			  return v;
		}
		return null;
  }
	
	private void handleDefine(ValueSet vs) {
	  if (vs.getDefine() != null) {
      // simple case: just generate the return
    	for (ValueSetDefineConceptComponent c : vs.getDefine().getConcept()) 
    		addDefinedCode(vs, vs.getDefine().getSystemSimple(), c);
   	}
  }

	private String key(ValueSetExpansionContainsComponent c) {
		return key(c.getSystemSimple(), c.getCodeSimple());
	}

	private String key(String uri, String code) {
		return "{"+uri+"}"+code;
	}

	private void addDefinedCode(ValueSet vs, String system, ValueSetDefineConceptComponent c) {
		if (!ToolingExtensions.hasDeprecated(c)) { 

			if (c.getAbstract() == null || !c.getAbstractSimple()) {
				addCode(system, c.getCodeSimple(), c.getDisplaySimple());
			}
			for (ValueSetDefineConceptComponent g : c.getConcept()) 
				addDefinedCode(vs, vs.getDefine().getSystemSimple(), g);
		}
  }

	private void addCode(String system, String code, String display) {
		ValueSetExpansionContainsComponent n = new ValueSet.ValueSetExpansionContainsComponent();
		n.setSystemSimple(system);
	  n.setCodeSimple(code);
	  n.setDisplaySimple(display);
	  String s = key(n);
	  if (!map.containsKey(s)) { 
	  	codes.add(n);
	  	map.put(s, n);
	  }
  }

  
}
