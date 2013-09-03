/*
 * CountableMixtureBranchRatesParser.java
 *
 * Copyright (c) 2002-2013 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.evomodelxml.branchratemodel;

import dr.evolution.tree.Tree;
import dr.evolution.util.Taxa;
import dr.evolution.util.TaxonList;
import dr.evomodel.branchratemodel.CountableMixtureBranchRates;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import dr.xml.*;

import java.util.logging.Logger;

/**
 */
public class CountableMixtureBranchRatesParser extends AbstractXMLObjectParser {

    public static final String COUNTABLE_CLOCK_BRANCH_RATES = "countableMixtureBranchRates";
    public static final String RATES = "rates";
    public static final String ALLOCATION = "rateCategories";
    public static final String CATEGORY = "category";
    public static final String RANDOMIZE = "randomize";

    public String getParserName() {
        return COUNTABLE_CLOCK_BRANCH_RATES;
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        Parameter ratesParameter = (Parameter) xo.getElementFirstChild(RATES);
        Parameter allocationParameter = (Parameter) xo.getElementFirstChild(ALLOCATION);
        TreeModel treeModel = (TreeModel) xo.getChild(TreeModel.class);

        Logger.getLogger("dr.evomodel").info("Using a countable mixture molecular clock model.");

        CountableMixtureBranchRates model = new CountableMixtureBranchRates(treeModel, ratesParameter, allocationParameter);

        if (!xo.getAttribute(RANDOMIZE, true)) {
            for (int i = 0; i < xo.getChildCount(); ++i) {
                if (xo.getChild(i) instanceof XMLObject) {
                    XMLObject xoc = (XMLObject) xo.getChild(i);
                    if (xoc.getName().equals(LocalClockModelParser.CLADE)) {
                        TaxonList taxonList = (TaxonList) xoc.getChild(TaxonList.class);

                        boolean includeStem = xoc.getAttribute(LocalClockModelParser.INCLUDE_STEM, false);
                        boolean excludeClade = xoc.getAttribute(LocalClockModelParser.EXCLUDE_CLADE, false);
                        int rateCategory = xoc.getIntegerAttribute(CATEGORY) - 1; // XML index-start = 1 not 0
                        try {
                            model.setClade(taxonList, rateCategory, includeStem, excludeClade);
                        } catch (Tree.MissingTaxonException e) {
                            throw new XMLParseException("Unable to find taxon in countable mixture model: " + e.getMessage());
                        }
                    }
                }
            }

        } else {
            model.randomize();
        }
        return model;
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public String getParserDescription() {
        return
                "This element provides a strict clock model. " +
                        "All branches have the same rate of molecular evolution.";
    }

    public Class getReturnType() {
        return CountableMixtureBranchRates.class;
    }

    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }

    private final XMLSyntaxRule[] rules = {
            new ElementRule(TreeModel.class),
            new ElementRule(RATES, Parameter.class, "The molecular evolutionary rate parameter", false),
            new ElementRule(ALLOCATION, Parameter.class, "Allocation parameter", false),
            AttributeRule.newBooleanRule(RANDOMIZE, true),
            new ElementRule(LocalClockModelParser.CLADE,
                    new XMLSyntaxRule[]{
//                            AttributeRule.newBooleanRule(RELATIVE, true),
                            AttributeRule.newIntegerRule(CATEGORY, false),
                            AttributeRule.newBooleanRule(LocalClockModelParser.INCLUDE_STEM, true, "determines whether or not the stem branch above this clade is included in the siteModel (default false)."),
                            AttributeRule.newBooleanRule(LocalClockModelParser.EXCLUDE_CLADE, true, "determines whether to exclude actual branches of the clade from the siteModel (default false)."),
                            new ElementRule(Taxa.class, "A set of taxa which defines a clade to apply a different site model to"),
                    }, 0, Integer.MAX_VALUE),
    };
}
