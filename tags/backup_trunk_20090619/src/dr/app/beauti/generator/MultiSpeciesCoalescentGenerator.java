package dr.app.beauti.generator;

import dr.app.beauti.util.XMLWriter;
import dr.app.beauti.components.ComponentFactory;
import dr.app.beauti.options.BeautiOptions;
import dr.app.beauti.options.Parameter;
import dr.app.beauti.options.PartitionModel;
import dr.app.beauti.options.TraitGuesser;
import dr.app.beauti.options.TreePrior;
import dr.app.beauti.priorsPanel.PriorType;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciesBindings;
import dr.evomodel.speciation.SpeciesTreeBMPrior;
import dr.evomodel.speciation.SpeciesTreeModel;
import dr.evomodel.speciation.TreePartitionCoalescent;
import dr.evomodel.speciation.YuleModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodelxml.BirthDeathModelParser;
import dr.evomodelxml.YuleModelParser;
import dr.evoxml.TaxonParser;
import dr.inference.distribution.ExponentialDistributionModel;
import dr.inference.distribution.GammaDistributionModel;
import dr.inference.distribution.MixedDistributionLikelihood;
import dr.inference.model.ParameterParser;
import dr.util.Attribute;
import dr.xml.AttributeParser;
import dr.xml.XMLParser;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Drummond
 * @author Walter Xie
 */
public class MultiSpeciesCoalescentGenerator extends Generator {
	
	private int numOfSpecies; // used in private String getIndicatorsParaValue()
	BeautiOptions options;
		
    public MultiSpeciesCoalescentGenerator(BeautiOptions options, ComponentFactory[] components) {
        super(options, components);
        this.options = options;
    }
    
    /**
     * write tag <sp>
     * @param taxonList
     * @param writer
     */
    public void writeMultiSpecies(TaxonList taxonList, XMLWriter writer) {
    	List<String> species = options.getSpeciesList();
    	String sp;
    	
    	numOfSpecies = species.size(); // used in private String getIndicatorsParaValue()
    	
    	for (String eachSp : species) {
    		writer.writeOpenTag(SpeciesBindings.SP, new Attribute[]{new Attribute.Default<String>(XMLParser.ID, eachSp)});

    		for (int i = 0; i < taxonList.getTaxonCount(); i++) {
    			Taxon taxon = taxonList.getTaxon(i);
    			sp = taxon.getAttribute(TraitGuesser.Traits.TRAIT_SPECIES.toString()).toString();

    			if (sp.equals(eachSp)) {
    				writer.writeIDref(TaxonParser.TAXON, taxon.getId());
    			}

    		}
    		writer.writeCloseTag(SpeciesBindings.SP);
    	}

    	writeGeneTrees (writer);
    }
    
    /**
     * write the species tree, species tree model, likelihood, etc.
     * @param writer
     */
    public void writeMultiSpeciesCoalescent(XMLWriter writer) {
    	writeSpeciesTree(writer);
    	writeSpeciesTreeModel(writer);
    	writeSpeciesTreeLikelihood(writer);
    	writeGeneUnderSpecies(writer);
    }
    
    
    private void writeGeneTrees(XMLWriter writer) {
    	writer.writeComment("Collection of Gene Trees");

        writer.writeOpenTag(SpeciesBindings.GENE_TREES, new Attribute[]{new Attribute.Default<String>(XMLParser.ID, SpeciesBindings.GENE_TREES)});

    	// generate gene trees regarding each data partition
    	for (PartitionModel partitionModel : options.getActivePartitionModels()) {
    		writer.writeIDref(TreeModel.TREE_MODEL, partitionModel.getName() + "." + TreeModel.TREE_MODEL);
        }

        writer.writeCloseTag(SpeciesBindings.GENE_TREES);
    }


    private void writeSpeciesTree(XMLWriter writer) {
    	writer.writeComment("Species Tree: Provides Per branch demographic function");

        writer.writeOpenTag(SpeciesTreeModel.SPECIES_TREE, new Attribute[]{new Attribute.Default<String>(XMLParser.ID, SP_TREE),
				new Attribute.Default<String>(SpeciesTreeModel.BMPRIOR, "true")});        
        writer.writeIDref(TraitGuesser.Traits.TRAIT_SPECIES.toString(), TraitGuesser.Traits.TRAIT_SPECIES.toString());
        
        //TODO: take sppSplitPopulations value from partionModel(?).constant.popSize
        // hard code get(0)
        double popSizeValue = options.getParameter("constant.popSize", options.getActivePartitionModels().get(0)).initial; // "initial" is "value"
        writer.writeOpenTag(SpeciesTreeModel.SPP_SPLIT_POPULATIONS, new Attribute[]{
        		new Attribute.Default<String>(AttributeParser.VALUE, Double.toString(popSizeValue))});
        
        writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
        		new Attribute.Default<String>(XMLParser.ID, SpeciesTreeModel.SPECIES_TREE + "." + SPLIT_POPS)}, true);
        
        writer.writeCloseTag(SpeciesTreeModel.SPP_SPLIT_POPULATIONS);
        
        writer.writeCloseTag(SpeciesTreeModel.SPECIES_TREE);

    }
    
    private void writeSpeciesTreeModel(XMLWriter writer) {    	    	
    	Parameter para;
    	
    	writer.writeComment("Species Tree: tree prior");
    	
    	if (options.nodeHeightPrior == TreePrior.SPECIES_BIRTH_DEATH) {
    		writer.writeComment("Species Tree: Birth Death Model");
    		
	    	writer.writeOpenTag(BirthDeathModelParser.BIRTH_DEATH_MODEL, new Attribute[]{
	    			new Attribute.Default<String>(XMLParser.ID, BirthDeathModelParser.BIRTH_DEATH),
					new Attribute.Default<String>(XMLParser.Utils.UNITS, XMLParser.Utils.SUBSTITUTIONS)});      
	    	
	    	writer.writeOpenTag(BirthDeathModelParser.BIRTHDIFF_RATE);
	    	
	    	para = options.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.BIRTHDIFF_RATE_PARAM_NAME);	    	
	    	writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
	        		new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.BIRTHDIFF_RATE_PARAM_NAME),
	        		new Attribute.Default<String>(AttributeParser.VALUE, Double.toString(para.initial)),
	        		new Attribute.Default<String>(ParameterParser.LOWER, Double.toString(para.lower)),
	        		new Attribute.Default<String>(ParameterParser.UPPER, Double.toString(para.upper))}, true);
	    	
	    	writer.writeCloseTag(BirthDeathModelParser.BIRTHDIFF_RATE);
	    	    	
	    	writer.writeOpenTag(BirthDeathModelParser.RELATIVE_DEATH_RATE);
	    	
	    	para = options.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.RELATIVE_DEATH_RATE_PARAM_NAME);
	    	writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
	        		new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.RELATIVE_DEATH_RATE_PARAM_NAME),
	        		new Attribute.Default<String>(AttributeParser.VALUE, Double.toString(para.initial)),
	        		new Attribute.Default<String>(ParameterParser.LOWER, Double.toString(para.lower)),
	        		new Attribute.Default<String>(ParameterParser.UPPER, Double.toString(para.upper))}, true);
	    	
	    	writer.writeCloseTag(BirthDeathModelParser.RELATIVE_DEATH_RATE);
	    	    	
	    	writer.writeCloseTag(BirthDeathModelParser.BIRTH_DEATH_MODEL); 
    	} else if (options.nodeHeightPrior == TreePrior.SPECIES_YULE) {
    		writer.writeComment("Species Tree: Yule Model");
    		
    		writer.writeOpenTag(YuleModel.YULE_MODEL, new Attribute[]{
	    			new Attribute.Default<String>(XMLParser.ID, YuleModelParser.YULE),
					new Attribute.Default<String>(XMLParser.Utils.UNITS, XMLParser.Utils.SUBSTITUTIONS)}); 
    		
    		writer.writeOpenTag(YuleModelParser.BIRTH_RATE);
    		
    		para = options.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + YuleModelParser.YULE + "." + YuleModelParser.BIRTH_RATE);
    		writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
	        		new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + YuleModelParser.YULE + "." + YuleModelParser.BIRTH_RATE),
	        		new Attribute.Default<String>(AttributeParser.VALUE, Double.toString(para.initial)),
	        		new Attribute.Default<String>(ParameterParser.LOWER, Double.toString(para.lower)),
	        		new Attribute.Default<String>(ParameterParser.UPPER, Double.toString(para.upper))}, true);
    		
    		writer.writeCloseTag(YuleModelParser.BIRTH_RATE);
    		
    		writer.writeCloseTag(YuleModel.YULE_MODEL); 
    	}
    	
    }
    
    
    private void writeSpeciesTreeLikelihood(XMLWriter writer) {
    	writer.writeComment("Species Tree: Likelihood of species tree");
	    	
    	if (options.nodeHeightPrior == TreePrior.SPECIES_BIRTH_DEATH) {
    		writer.writeComment("Species Tree: Birth Death Model");
    		
	    	writer.writeOpenTag(SpeciationLikelihood.SPECIATION_LIKELIHOOD, new Attribute[]{
	    			new Attribute.Default<String>(XMLParser.ID, SPECIATION_LIKE)});      
	    	
	    	writer.writeOpenTag(SpeciationLikelihood.MODEL); 
	    	writer.writeIDref(BirthDeathModelParser.BIRTH_DEATH_MODEL, BirthDeathModelParser.BIRTH_DEATH);    	
	    	writer.writeCloseTag(SpeciationLikelihood.MODEL);    	
	    	 
    	} else if (options.nodeHeightPrior == TreePrior.SPECIES_YULE) {
    		writer.writeComment("Species Tree: Yule Model");
    		
	    	writer.writeOpenTag(SpeciationLikelihood.SPECIATION_LIKELIHOOD, new Attribute[]{
	    			new Attribute.Default<String>(XMLParser.ID, SPECIATION_LIKE)});      
	    	
	    	writer.writeOpenTag(SpeciationLikelihood.MODEL); 
	    	writer.writeIDref(YuleModel.YULE_MODEL, YuleModelParser.YULE);    	
	    	writer.writeCloseTag(SpeciationLikelihood.MODEL); 	    		    	
    	}
    	
    	// <sp> tree
    	writer.writeOpenTag(SpeciesTreeModel.SPECIES_TREE); 
    	writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE);    	
    	writer.writeCloseTag(SpeciesTreeModel.SPECIES_TREE); 
    	
    	writer.writeCloseTag(SpeciationLikelihood.SPECIATION_LIKELIHOOD);
    }
    
    private void writeGeneUnderSpecies(XMLWriter writer) {
    	
    	writer.writeComment("Species Tree: Coalescent likelihood for gene trees under species tree");
    	
    	// speciesCoalescent id="coalescent"
    	writer.writeOpenTag(TreePartitionCoalescent.SPECIES_COALESCENT, new Attribute[]{
    			new Attribute.Default<String>(XMLParser.ID, COALESCENT)});   
    	
    	writer.writeIDref(TraitGuesser.Traits.TRAIT_SPECIES.toString(), TraitGuesser.Traits.TRAIT_SPECIES.toString()); 
    	writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE); 
    	
    	writer.writeCloseTag(TreePartitionCoalescent.SPECIES_COALESCENT); 
    	
    	// exponentialDistributionModel id="pdist"
    	writer.writeOpenTag(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL, new Attribute[]{
    			new Attribute.Default<String>(XMLParser.ID, PDIST)});  
    	
    	writer.writeOpenTag(ExponentialDistributionModel.MEAN); 
    	
    	Parameter para = options.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + options.POP_MEAN); 
        
    	writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
        		new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + options.POP_MEAN),
        		new Attribute.Default<String>(AttributeParser.VALUE, Double.toString(para.initial))}, true);
    	
    	writer.writeCloseTag(ExponentialDistributionModel.MEAN); 
    	
    	writer.writeCloseTag(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL); 
    	
    	if (options.nodeHeightPrior == TreePrior.SPECIES_YULE) {
    		// new part
	    	writer.writeOpenTag(MixedDistributionLikelihood.DISTRIBUTION_LIKELIHOOD, new Attribute[]{
	    			new Attribute.Default<String>(XMLParser.ID, SPOPS)}); 
	    	
	    	// <distribution0>
	    	writer.writeOpenTag(MixedDistributionLikelihood.DISTRIBUTION0); 
	    	
	    	writer.writeIDref(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL, PDIST);    	
	    	
	    	writer.writeCloseTag(MixedDistributionLikelihood.DISTRIBUTION0); 
	    	
	    	// <distribution1>
	    	writer.writeOpenTag(MixedDistributionLikelihood.DISTRIBUTION1); 	    	
	    	writer.writeOpenTag(GammaDistributionModel.GAMMA_DISTRIBUTION_MODEL); 
	    	
	    	writer.writeOpenTag(GammaDistributionModel.SHAPE);
	    	writer.writeText("2");
	    	writer.writeCloseTag(GammaDistributionModel.SHAPE); 
	    	
	    	writer.writeOpenTag(GammaDistributionModel.SCALE);
	    	writer.writeIDref(ParameterParser.PARAMETER, TraitGuesser.Traits.TRAIT_SPECIES + "." + options.POP_MEAN);   
	    	writer.writeCloseTag(GammaDistributionModel.SCALE); 
	    		    	 	
	    	writer.writeCloseTag(GammaDistributionModel.GAMMA_DISTRIBUTION_MODEL); 	    		    	
	    	writer.writeCloseTag(MixedDistributionLikelihood.DISTRIBUTION1); 
	    	
	    	// <data>
	    	writer.writeOpenTag(MixedDistributionLikelihood.DATA);
	    	
	    	writer.writeIDref(ParameterParser.PARAMETER, SpeciesTreeModel.SPECIES_TREE + "." + SPLIT_POPS);  
	    	
	    	writer.writeCloseTag(MixedDistributionLikelihood.DATA); 
	    	
	    	// <indicators>
	    	writer.writeOpenTag(MixedDistributionLikelihood.INDICATORS);
	    	// Needs special treatment - you have to generate "NS" ones and 2(N-1) zeros, where N is the number of species.
	    	// N "1", 2(N-1) "0"
	    	writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{new Attribute.Default<String>(AttributeParser.VALUE, getIndicatorsParaValue())}, true);  
	    	
	    	writer.writeCloseTag(MixedDistributionLikelihood.INDICATORS); 
	    		    	
	    	writer.writeCloseTag(MixedDistributionLikelihood.DISTRIBUTION_LIKELIHOOD);
	    	
    	} else {
    		// STPopulationPrior id="stp" log_root="true"
	    	writer.writeOpenTag(SpeciesTreeBMPrior.STPRIOR, new Attribute[]{
	    			new Attribute.Default<String>(XMLParser.ID, STP),
	    			new Attribute.Default<String>(SpeciesTreeBMPrior.LOG_ROOT, "true")});  
	    	writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE); 
	    	
	    	writer.writeOpenTag(SpeciesTreeBMPrior.TIPS);     	
	    	
	    	writer.writeIDref(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL, PDIST);
	    	
	    	writer.writeCloseTag(SpeciesTreeBMPrior.TIPS); 
	    	
	    	writer.writeOpenTag(SpeciesTreeBMPrior.STSIGMA);     	
	    	
	    	writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
	    			// <parameter id="stsigma" value="1" /> 
	        		new Attribute.Default<String>(XMLParser.ID, SpeciesTreeBMPrior.STSIGMA.toLowerCase()),
	        		new Attribute.Default<String>(AttributeParser.VALUE, "1")}, true);
	    	
	    	writer.writeCloseTag(SpeciesTreeBMPrior.STSIGMA); 
	    	
	    	writer.writeCloseTag(SpeciesTreeBMPrior.STPRIOR); 
    	}
    }
    
    private String getIndicatorsParaValue() {
    	String v = "";
    	
    	for (int i = 0; i < numOfSpecies; i++) {
			if (i == (numOfSpecies - 1)) {
				v = v + "1"; // N 1
			} else {
				v = v + "1 "; // N 1
			}
		}
    	
    	for (int i = 0; i < (numOfSpecies - 1); i++) {    		
    		v = v + " 0 0"; // 2(N-1) 0    		
		}
    	
    	return v;
    }
    
}