package dr.evomodel.graphlikelihood;

import java.util.HashMap;
import java.util.logging.Logger;

import jebl.gui.trees.treeviewer.treelayouts.AbstractTreeLayout;
import dr.evolution.alignment.SimpleSiteList;
import dr.evolution.alignment.SiteList;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.TaxonList;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.graph.GraphModel;
import dr.evomodel.graph.Partition;
import dr.evomodel.graph.PartitionModel;
import dr.evomodel.graph.PartitionModel.PartitionChangedEvent;
import dr.evomodel.sitemodel.SiteModel;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.treelikelihood.AbstractTreeLikelihood;
import dr.evomodel.treelikelihood.GeneralLikelihoodCore;
import dr.evomodel.treelikelihood.LikelihoodCore;
import dr.evomodel.treelikelihood.TipPartialsModel;
import dr.evomodel.treelikelihood.TreeLikelihood;
import dr.inference.model.Model;

public class GraphLikelihood extends AbstractTreeLikelihood {

	public static final String GRAPH_LIKELIHOOD = "graphLikelihood";
    public static final String USE_AMBIGUITIES = "useAmbiguities";
    public static final String ALLOW_MISSING_TAXA = "allowMissingTaxa";
    public static final String STORE_PARTIALS = "storePartials";
    public static final String SCALING_FACTOR = "scalingFactor";
    public static final String SCALING_THRESHOLD = "scalingThreshold";
    public static final String FORCE_JAVA_CORE = "forceJavaCore";
    public static final String FORCE_RESCALING = "forceRescaling";
	SiteList concatSiteList;

	/*
     * TODO: refactor this to derive TreeLikelihood!
	 */
    public GraphLikelihood(
            GraphModel graphModel,
            PartitionModel partitionModel,
            TipPartialsModel tipPartialsModel,
            boolean useAmbiguities,
            boolean allowMissingTaxa,
            boolean forceRescaling) 
    {
        super(GRAPH_LIKELIHOOD, createConcatenatedSiteList(partitionModel), graphModel);
        createConcatenatedSiteMap(partitionModel, siteMap);

        try{

        this.concatSiteList = (SiteList)this.patternList;
        this.partitionModel = partitionModel;

        // BEGIN TreeLikelihood LIKELIHOODCORE INIT CODE
        this.likelihoodCore = new GeneralGraphLikelihoodCore(stateCount);
        String coreName = "Java general";
        // END TreeLikelihood LIKELIHOODCORE INIT CODE

        SiteModel siteModel = partitionModel.getPartition(0).getSiteModel();
        this.integrateAcrossCategories = siteModel.integrateAcrossCategories();
        this.categoryCount = siteModel.getCategoryCount();

    	updatePattern = new boolean[patternList.getPatternCount()];

    	
        // BEGIN TreeLikelihood LOGGER CODE
        final Logger logger = Logger.getLogger("dr.evomodel");
        {
            final String id = getId();
            logger.info("TreeLikelihood(" + ((id != null) ? id : treeModel.getId()) + ") using " + coreName + " likelihood core");

            logger.info("  " + (useAmbiguities ? "Using" : "Ignoring") + " ambiguities in tree likelihood.");
            logger.info("  With " + patternList.getPatternCount() + " unique site patterns.");
        }
        // END TreeLikelihood LOGGER CODE

        likelihoodCore.initialize(nodeCount, patternCount, categoryCount, integrateAcrossCategories);
        probabilities = new double[stateCount * stateCount];

    
        // BEGIN TreeLikelihood TIPPARTIALS CODE (VERBATIM)
        int extNodeCount = treeModel.getExternalNodeCount();
        int intNodeCount = treeModel.getInternalNodeCount();

        if (tipPartialsModel != null) {
            tipPartialsModel.setTree(treeModel);

            tipPartials = new double[patternCount * stateCount];

            for (int i = 0; i < extNodeCount; i++) {
                // Find the id of tip i in the patternList
                String id = treeModel.getTaxonId(i);
                int index = patternList.getTaxonIndex(id);

                if (index == -1) {
                    throw new TaxonList.MissingTaxonException("Taxon, " + id + ", in tree, " + treeModel.getId() +
                            ", is not found in patternList, " + patternList.getId());
                }

                tipPartialsModel.setStates(patternList, index, i, id);
                likelihoodCore.createNodePartials(i);
            }

            addModel(tipPartialsModel);
            //useAmbiguities = true;
        } else {
            for (int i = 0; i < extNodeCount; i++) {
                // Find the id of tip i in the patternList
                String id = treeModel.getTaxonId(i);
                int index = patternList.getTaxonIndex(id);

                if (index == -1) {
                    if (!allowMissingTaxa) {
                        throw new TaxonList.MissingTaxonException("Taxon, " + id + ", in tree, " + treeModel.getId() +
                                ", is not found in patternList, " + patternList.getId());
                    }
                    if (useAmbiguities) {
                        setMissingPartials(likelihoodCore, i);
                    } else {
                        setMissingStates(likelihoodCore, i);
                    }
                } else {
                    if (useAmbiguities) {
                        setPartials(likelihoodCore, patternList, categoryCount, index, i);
                    } else {
                        setStates(likelihoodCore, patternList, index, i);
                    }
                }
            }
        }
        for (int i = 0; i < intNodeCount; i++) {
            likelihoodCore.createNodePartials(extNodeCount + i);
        }

        if (forceRescaling) {
            likelihoodCore.setUseScaling(true);
            logger.info("  Forcing use of partials rescaling.");
        }
        // END TreeLikelihood TIPPARTIALS CODE (VERBATIM)

    
	    } catch (TaxonList.MissingTaxonException mte) {
	        throw new RuntimeException(mte.toString());
	    }

	}
    
    protected static SiteList createConcatenatedSiteList(PartitionModel pm){
    	// assume all SiteList instances have the same taxon list.
    	SimpleSiteList ssl = new SimpleSiteList(pm.getSiteList(0).getDataType(),pm.getSiteList(0));
    	for(int i=0; i<pm.getSiteListCount(); i++){
    		SiteList sl = pm.getSiteList(i);
    		for(int j=0; j<sl.getSiteCount(); j++){
        		ssl.addPattern(sl.getSitePattern(j));
    		}
    	}
    	return ssl;
    }

    protected void createConcatenatedSiteMap(PartitionModel pm, HashMap<SiteList, Integer> siteListMap){
    	int sum = 0;
    	for(int i=0; i<pm.getSiteListCount(); i++){
    		SiteList sl = pm.getSiteList(i);
    		siteListMap.put(sl, sum);
    		sum += sl.getSiteCount();
    	}
    }

    
    
    protected void growNodeStorage(){
    	likelihoodCore.growNodeStorage(updateNode.length);	// double the number
    	nodeCount *= 2;

    	// FIXME: need to update all the data structures here
    	boolean[] tmp = new boolean[updateNode.length*2];
    	System.arraycopy(updateNode, 0, tmp, 0, updateNode.length);
    	for(int i=updateNode.length; i<tmp.length; i++)
    		tmp[i]=false;
    	updateNode = tmp;
    }

    protected void handleModelChangedEvent(Model model, Object object, int index) {
    	if(treeModel.getNodeCount() >= nodeCount)
    	{
    		growNodeStorage(); // need more node storage!
    	}
    	if(model instanceof PartitionModel){
    		// the partitions changed, do something here
    		// just mark all affected site patterns as dirty
    		PartitionChangedEvent pce = (PartitionChangedEvent)object;
    		if(pce.hasNewSection())
    		{
    			// mark only the new section as dirty
    			for(int i=pce.getNewSectionLeft(); i<pce.getNewSectionRight(); i++)
    			{
    				updatePattern[i]=true;
    			}
    		}
    	}else{
    		// otherwise the TreeLikelihood can handle it
    	}
    	// this is not efficient, only temporary for testing
    	for(int i=0; i<updateNode.length; i++)
    		updateNode[i]=true;

    	super.handleModelChangedEvent(model, object, index);
    }

    protected void storeState() {
        likelihoodCore.storeState();
        super.storeState();
    }
    protected void restoreState() {
        likelihoodCore.restoreState();
        super.restoreState();
    }

    /**
     * Calculate the log likelihood of the current state.
     * Ripped nearly verbatim from treeLikelihood
     * TODO: refactor this to derive TreeLikelihood!
     * @return the log likelihood.
     */
    protected double calculateLogLikelihood() {

        if (patternLogLikelihoods == null) {
            patternLogLikelihoods = new double[patternCount];
        }

        final NodeRef root = treeModel.getRoot();
        for(int i=0; i<partitionModel.getPartitionCount(); i++){
            traverse(treeModel, root, partitionModel, partitionModel.getPartition(i));
        }

        double logL = 0.0;
        for (int i = 0; i < patternCount; i++) {
            logL += (patternLogLikelihoods[i]) * patternWeights[i];
        }

        if (logL == Double.NEGATIVE_INFINITY) {
            Logger.getLogger("dr.evomodel").info("TreeLikelihood, " + this.getId() + ", turning on partial likelihood scaling to avoid precision loss");

            // We probably had an underflow... turn on scaling
            likelihoodCore.setUseScaling(true);

            // and try again...
            updateAllNodes();
            updateAllPatterns();
            for(int i=0; i<partitionModel.getPartitionCount(); i++){
                traverse(treeModel, root, partitionModel, partitionModel.getPartition(i));
            }

            logL = 0.0;
            for (int i = 0; i < patternCount; i++) {
                logL += (patternLogLikelihoods[i]) * patternWeights[i];
            }
        }

        //********************************************************************
        // after traverse all nodes and patterns have been updated --
        //so change flags to reflect this.
        for (int i = 0; i < nodeCount; i++) {
            updateNode[i] = false;
        }
        //********************************************************************

        return logL;
    }
    
    
    /**
     * partition-aware traversal
     * updateNode[i]==true indicates all partitions at the node should be updated
     * updatePartition[j]==true indicates all nodes containing partition j to update
     * updateNodePartition[i][j]==true indicates node i, partition j for update.
     */
    protected boolean traverse(Tree tree, NodeRef node, PartitionModel partitionModel, Partition p) 
    {
    	GraphModel gm = (GraphModel)tree;
        boolean update = false;

        int nodeNum = node.getNumber();

        // which parent has this partition?
        NodeRef parent = null;
        GraphModel.Node gnode = (GraphModel.Node)node;
        if(gnode.hasObject(0, p))
        	parent = gm.getParent(node, 0);
    	else
    		parent = gm.getParent(node, 1);

        if(parent==null&&gm.getParent(node)!=null){
        	// partition does not pass through this node.
        	System.err.println("Partition not at node");
        }

        // does this one need to be updated?
        if (parent != null && updateNode[nodeNum]) {
            update = true;
        }
                
        // If the node has children update the partial likelihoods.
        if (tree.getChildCount(node)>0) 
        {
            GraphModel.Node child1 = (GraphModel.Node)gm.getChild(node, 0);
            GraphModel.Node child2 = (GraphModel.Node)gm.getChild(node, 1);

            boolean has1 = gm.hasObjectOnEdge(node, child1, p);
            boolean has2 = child2 != null ? gm.hasObjectOnEdge(node, child2, p) : false;

            // traverse to child nodes if necessary
            boolean update1 = has1 ? traverse(tree, child1, partitionModel, p) : false;
            boolean update2 = has2 ? traverse(tree, child2, partitionModel, p) : false;

            // If we have two children with the partition, and
            // either child node was updated then update this node too
            if (has1&&has2&&(update1 || update2)) 
            {
                final int childNum1 = child1.getNumber();
                final int childNum2 = child2.getNumber();
                
                // First update the transition probability matrix(ices) for child branches
                setNodeMatrix(gm, p, child1);
                setNodeMatrix(gm, p, child2);

                likelihoodCore.setNodePartialsForUpdate(nodeNum);

                // determine the left and right bounds of this partition
                int l = remapSite(p.getSiteList(), p.getLeftSite());
                int r = remapSite(p.getSiteList(), p.getRightSite());
                if (integrateAcrossCategories) {
                    likelihoodCore.calculatePartials(childNum1, childNum2, nodeNum, l, r);
                } else {
                    likelihoodCore.calculatePartials(childNum1, childNum2, nodeNum, siteCategories, l, r);
                }

                if (COUNT_TOTAL_OPERATIONS) {
                    totalOperationCount ++;
                }


                if (parent == null) {
                    // No parent this is the root of the tree -
                    // calculate the pattern likelihoods
                    FrequencyModel frequencyModel = p.getSiteModel().getFrequencyModel();
                    double[] frequencies = frequencyModel.getFrequencies();
                    double[] partials = getRootPartials(p);
                    likelihoodCore.calculateLogLikelihoods(partials, frequencies, patternLogLikelihoods, l, r);
                }

                update = true;
            }
        }

        return update;

    }

    /**
     * Set the child node's matrix immediately prior to integrating partials
     * @param g
     * @param p
     * @param n
     */
    protected void setNodeMatrix(GraphModel g, Partition p, NodeRef n)
    {
        double branchTime = getRateTime(g, p, n);
        int nodeNum = n.getNumber();

        likelihoodCore.setNodeMatrixForUpdate(nodeNum);

        SiteModel siteModel = p.getSiteModel();
        for (int i = 0; i < categoryCount; i++) {
            double branchLength = siteModel.getRateForCategory(i) * branchTime;
            siteModel.getSubstitutionModel().getTransitionProbabilities(branchLength, probabilities);
            likelihoodCore.setNodeMatrix(nodeNum, i, probabilities);
        }
    }
    
    /*
     * computes the total product of rate and time for
     * on the branch at or below n which contains partition p
     * Handles the situation where a partition may be defined at only one
     * child node, so the total rate*time to integrate over includes the
     * next descendant also
     */
    protected double getRateTime(GraphModel g, Partition p, NodeRef n)
    {
        BranchRateModel branchRateModel = p.getBranchRateModel();
        NodeRef parent = g.getParent(n);
    	// find nearest node at or below n where p is
    	// assigned to both children
    	double branchRate = branchRateModel.getBranchRate(g, n);
        // Get the operational time of the branch
        double branchRateTime = branchRate * (g.getNodeHeight(parent) - g.getNodeHeight(n));

        if (branchRateTime < 0.0) {
            throw new RuntimeException("Negative branch length: " + branchRateTime);
        }
        if(g.getChildCount(n)==1){
        	if(g.hasObjectOnEdge(n, g.getChild(n, 0), p))
    			branchRateTime += getRateTime(g, p, g.getChild(n, 0));
    		else
    			System.err.println("Partition dead-end");
        }else if(g.getChildCount(n)==2){
        	boolean has1 = g.hasObjectOnEdge(n, g.getChild(n, 0), p);
        	boolean has2 = g.hasObjectOnEdge(n, g.getChild(n, 1), p);
        	if(has1&&!has2)
    			branchRateTime += getRateTime(g, p, g.getChild(n, 0));
        	if(!has1&&has2)
    			branchRateTime += getRateTime(g, p, g.getChild(n, 1));
        	if(!has1&&!has2)
    			System.err.println("Partition dead-end");
        }
    	return branchRateTime;
    }

    public final double[] getRootPartials(Partition p) {
        if (rootPartials == null) {
            rootPartials = new double[patternCount * stateCount];
        }

        int nodeNum = treeModel.getRoot().getNumber();
        int l = remapSite(p.getSiteList(), p.getLeftSite());
        int r = remapSite(p.getSiteList(), p.getRightSite());
        if (integrateAcrossCategories) {
            // moved this call to here, because non-integrating siteModels don't need to support it - AD
            SiteModel siteModel = p.getSiteModel();
            double[] proportions = siteModel.getCategoryProportions();
            likelihoodCore.integratePartials(nodeNum, proportions, rootPartials, l, r);
        } else {
            likelihoodCore.getPartials(nodeNum, rootPartials, l, r);
        }

        return rootPartials;
    }
    
    /*
     * Converts a partition-local site index to a concatenated site index
     */
    protected int remapSite(SiteList sl, int site){
    	Integer left = siteMap.get(sl);
    	return left.intValue() + site;
    }

    /**
     * the root partial likelihoods (a temporary array that is used
     * to fetch the partials - it should not be examined directly -
     * use getRootPartials() instead).
     */
    private double[] rootPartials = null;
    

    protected PartitionModel partitionModel;
    protected GeneralGraphLikelihoodCore likelihoodCore;
    protected int categoryCount;
    protected final boolean integrateAcrossCategories;
    protected double[] probabilities;
    protected double[] tipPartials;
    protected double[] patternLogLikelihoods = null;
    protected int[] siteCategories = null;
    protected boolean[] updatePartition = null;
    protected boolean[][] updateNodePartition = null;
    
    protected HashMap<SiteList, Integer> siteMap = new HashMap<SiteList, Integer>();
}
