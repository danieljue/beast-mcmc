/*
 * DiscretizedBranchRates.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.branchratemodel;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.ParametricDistributionModel;
import dr.inference.model.AbstractModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.xml.*;

import java.util.logging.Logger;

/**
 * @author Alexei Drummond
 * @author Andrew Rambaut
 *
 * @version $Id: DiscretizedBranchRates.java,v 1.11 2006/01/09 17:44:30 rambaut Exp $
 */
public class DiscretizedBranchRates extends AbstractModel implements BranchRateModel  {

    public static final String DISCRETIZED_BRANCH_RATES = "discretizedBranchRates";
    public static final String DISTRIBUTION = "distribution";
    public static final String RATE_CATEGORIES = "rateCategories";
    public static final String SINGLE_ROOT_RATE = "singleRootRate";

    private ParametricDistributionModel distributionModel;
    private TreeModel tree;

    // The rate categories of each branch
    private Parameter rateCategoryParameter;

    // the index of the root node.
    private int rootNodeNumber;
    private int storedRootNodeNumber;

    private final int categoryCount;
    private final double step;
    private final double[] rates;

	public DiscretizedBranchRates(TreeModel tree, Parameter rateCategoryParameter, ParametricDistributionModel model) {

        super(DISCRETIZED_BRANCH_RATES);
        this.tree = tree;

        categoryCount = tree.getNodeCount();
        step = 1.0 / (double)categoryCount;

        rates = new double[categoryCount];

        this.distributionModel = model;

        this.rateCategoryParameter = rateCategoryParameter;
        if (rateCategoryParameter.getDimension() != tree.getNodeCount() -1 ) {
            throw new IllegalArgumentException("The rate category parameter must be of length nodeCount-1");
        }

        for (int i = 0; i < rateCategoryParameter.getDimension(); i++) {
            rateCategoryParameter.setParameterValue(i, i);
        }

        addModel(model);
        addModel(tree);

        addParameter(rateCategoryParameter);

        rootNodeNumber = tree.getRoot().getNumber();
        storedRootNodeNumber = rootNodeNumber;
        
        setupRates();
        shuffleIndices();

	}

	public void handleModelChangedEvent(Model model, Object object, int index) {
        if (model == distributionModel) {
            setupRates();
            fireModelChanged();
        } else if (model == tree) {
            shuffleIndices();
        }
    }

    protected void handleParameterChangedEvent(Parameter parameter, int index) {
        fireModelChanged(null, getNodeNumberFromCategoryIndex(index));
    }

    protected void storeState() {
        storedRootNodeNumber = rootNodeNumber;
    }

    protected void restoreState() {
        setupRates();
        rootNodeNumber = storedRootNodeNumber;
    }

    protected void acceptState() {}

	public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

		public String getParserName() { return DISCRETIZED_BRANCH_RATES; }

		public Object parseXMLObject(XMLObject xo) throws XMLParseException {

			TreeModel tree = (TreeModel)xo.getChild(TreeModel.class);
            ParametricDistributionModel distributionModel = (ParametricDistributionModel)xo.getSocketChild(DISTRIBUTION);

            Parameter rateCategoryParameter = (Parameter)xo.getSocketChild(RATE_CATEGORIES);

			Logger.getLogger("dr.evomodel").info("Using discretized relaxed clock model.");
            Logger.getLogger("dr.evomodel").info("  parametric model = " + distributionModel.getModelName());
            Logger.getLogger("dr.evomodel").info("   rate categories = " + rateCategoryParameter.getDimension());

            if (xo.hasAttribute(SINGLE_ROOT_RATE)) {
                //singleRootRate = xo.getBooleanAttribute(SINGLE_ROOT_RATE);
                Logger.getLogger("dr.evomodel").warning("   WARNING: single root rate is not implemented!");
            }

            if (rateCategoryParameter.getDimension() != tree.getNodeCount() -1 ) {
                throw new XMLParseException(DISCRETIZED_BRANCH_RATES + ": The rate category parameter must be of length nodeCount-1");
            }
                                                   
			return new DiscretizedBranchRates(tree, rateCategoryParameter, distributionModel);
        }

		//************************************************************************
		// AbstractXMLObjectParser implementation
		//************************************************************************

		public String getParserDescription() {
			return
				"This element returns an discretized relaxed clock model." +
                "The branch rates are drawn from a discretized parametric distribution.";
		}

		public Class getReturnType() { return DiscretizedBranchRates.class; }

		public XMLSyntaxRule[] getSyntaxRules() { return rules; }

		private XMLSyntaxRule[] rules = new XMLSyntaxRule[] {
            AttributeRule.newBooleanRule(SINGLE_ROOT_RATE, true, "Whether only a single rate should be used for the two children branches of the root"),
            new ElementRule(TreeModel.class),
            new ElementRule(DISTRIBUTION, ParametricDistributionModel.class, "The distribution model for rates among branches", false),
            new ElementRule(RATE_CATEGORIES, Parameter.class, "The rate categories parameter", false),
        };
    };

    public double getBranchRate(Tree tree, NodeRef node) {

        if (tree.isRoot(node)) {
            throw new IllegalArgumentException("root node doesn't have a rate!");
        }

        int nodeNumber = node.getNumber();

        if (nodeNumber == rootNodeNumber) {
            throw new IllegalArgumentException("INTERNAL ERROR! node with number " + rootNodeNumber + " should be the root node.");
        }

        int rateCategory = (int)Math.round(rateCategoryParameter.getParameterValue(getCategoryIndexFromNodeNumber(nodeNumber)));

        return rates[rateCategory];
    }

    public int getNodeNumberFromCategoryIndex(int categoryIndex) {
        if (categoryIndex >= rootNodeNumber) return categoryIndex + 1;
        return categoryIndex;
    }

    public int getCategoryIndexFromNodeNumber(int nodeNumber) {
        if (nodeNumber > rootNodeNumber) return nodeNumber - 1;
        return nodeNumber;
    }

    /**
     * Calculates the actual rates corresponding to the category indices.
     */
    private void setupRates() {

        double z = step / 2.0;
        for (int i = 0; i < categoryCount; i++) {
            rates[i] = distributionModel.quantile(z);
            z += step;
        }
    }

    private void shuffleIndices() {
        int newRootNodeNumber = tree.getRoot().getNumber();

        //if (newRootNodeNumber != rootNodeNumber) {
        //    System.out.println("old root node number =" + rootNodeNumber);
        //    System.out.println("new root node number =" + newRootNodeNumber);
        //}

        if (rootNodeNumber > newRootNodeNumber) {

            //for (int i = 0; i < rateCategoryParameter.getDimension(); i++) {
            //    System.out.print((int)Math.round(rateCategoryParameter.getParameterValue(i)) + "\t");
            //}
            //System.out.println();

            int oldRateIndex = (int)Math.round(
                    rateCategoryParameter.getParameterValue(newRootNodeNumber));

            int end = Math.min(rateCategoryParameter.getDimension()-1, rootNodeNumber);
            for (int i = newRootNodeNumber; i < end; i++) {
                rateCategoryParameter.setParameterValue(i,rateCategoryParameter.getParameterValue(i+1));
            }

            rateCategoryParameter.setParameterValue(end,oldRateIndex);

            //for (int i = 0; i < rateCategoryParameter.getDimension(); i++) {
            //    System.out.print((int)Math.round(rateCategoryParameter.getParameterValue(i)) + "\t");
            //}
            //System.out.println();

        } else if (rootNodeNumber < newRootNodeNumber) {

            //System.out.println("old root node number =" + rootNodeNumber);
            //System.out.println("new root node number =" + newRootNodeNumber);

            //for (int i = 0; i < rateCategoryParameter.getDimension(); i++) {
            //    System.out.print((int)Math.round(rateCategoryParameter.getParameterValue(i)) + "\t");
            //}
            //System.out.println();

            int end = Math.min(rateCategoryParameter.getDimension()-1, newRootNodeNumber);

            int oldRateIndex = (int)Math.round(
                    rateCategoryParameter.getParameterValue(end));

            for (int i = end; i > rootNodeNumber; i--) {
                rateCategoryParameter.setParameterValue(i,rateCategoryParameter.getParameterValue(i-1));
            }

            rateCategoryParameter.setParameterValue(rootNodeNumber,oldRateIndex);

            //for (int i = 0; i < rateCategoryParameter.getDimension(); i++) {
            //    System.out.print((int)Math.round(rateCategoryParameter.getParameterValue(i)) + "\t");
            //}
            //System.out.println();
        }
        rootNodeNumber = newRootNodeNumber;
    }
}