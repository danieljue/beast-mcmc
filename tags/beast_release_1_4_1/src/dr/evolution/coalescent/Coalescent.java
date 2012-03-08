/*
 * Coalescent.java
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

package dr.evolution.coalescent;

import dr.evolution.tree.Tree;
import dr.evolution.util.Units;
import dr.math.Binomial;
import dr.math.MultivariateFunction;

/**
 * A likelihood function for the coalescent. Takes a tree and a demographic model.
 *
 * Parts of this class were derived from C++ code provided by Oliver Pybus.
 *
 * @version $Id: Coalescent.java,v 1.12 2005/05/24 20:25:55 rambaut Exp $
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 */
public class Coalescent implements MultivariateFunction, Units {

	// PUBLIC STUFF

	public Coalescent(Tree tree, DemographicFunction demographicFunction) { 
		this(new TreeIntervals(tree), demographicFunction);
	}
	
	public Coalescent(IntervalList intervals, DemographicFunction demographicFunction) { 
		
		this.intervals = intervals;
		this.demographicFunction = demographicFunction;
	}
	

	/** 
	 * Calculates the log likelihood of this set of coalescent intervals, 
	 * given a demographic model.
	 */
	public double calculateLogLikelihood() {
				
		return calculateLogLikelihood(intervals, demographicFunction);
	}
	
	/** 
	 * Calculates the log likelihood of this set of coalescent intervals, 
	 * given a demographic model.
	 */
	public static final double calculateLogLikelihood(IntervalList intervals,
														DemographicFunction demographicFunction) {
		
		double logL = 0.0;
		
		double startTime = 0.0;
		
		for (int i = 0, n = intervals.getIntervalCount(); i < n; i++) {
			
			double duration = intervals.getInterval(i);
			double finishTime = startTime + duration;

			double intervalArea = demographicFunction.getIntegral(startTime, finishTime);
			int lineageCount = intervals.getLineageCount(i);
			
			if (intervals.getIntervalType(i) == IntervalType.COALESCENT) {
			
				logL += -Math.log(demographicFunction.getDemographic(finishTime)) - 
									(Binomial.choose2(lineageCount)*intervalArea);
									
			} else { // SAMPLE or NOTHING
			
				logL += -(Binomial.choose2(lineageCount)*intervalArea);
			}
			
			startTime = finishTime; 
		}
		
		return logL;
	}
	
	/** 
	 * Calculates the log likelihood of this set of coalescent intervals, 
	 * using an analytical integration over theta.
	 */
	public static final double calculateAnalyticalLogLikelihood(IntervalList intervals) {
	
		if (!intervals.isCoalescentOnly()) {
			throw new IllegalArgumentException("Can only calculate analytical likelihood for pure coalescent intervals");
		}
	
		double lambda = getLambda(intervals);
		int n = intervals.getSampleCount();
		
		double logL = 0.0;
		
		// assumes a 1/theta prior	
		//logLikelihood = Math.log(1.0/Math.pow(lambda,n));
		
		// assumes a flat prior
		logL = Math.log(1.0/Math.pow(lambda,n-1));
		return logL;
	}
	
	/**
	 * Returns a factor lambda such that the likelihood can be expressed as
	 * 1/theta^(n-1) * exp(-lambda/theta). This allows theta to be integrated
	 * out analytically. :-)
	 */
	private static final double getLambda(IntervalList intervals) {
		double lambda = 0.0;
		for (int i= 0; i < intervals.getIntervalCount(); i++) {
			lambda += (intervals.getInterval(i) * intervals.getLineageCount(i));	
		}
		lambda /= 2;
		
		return lambda;
	}

    // **************************************************************
    // MultivariateFunction IMPLEMENTATION
    // **************************************************************

	public double evaluate(double[] argument) {
		for (int i = 0; i < argument.length; i++) {
			demographicFunction.setArgument(i, argument[i]);
		}
		
		return calculateLogLikelihood();
	}
	
	public int getNumArguments() { 
		return demographicFunction.getNumArguments(); 
	}
	
	public double getLowerBound(int n) {
		return demographicFunction.getLowerBound(n); 
	}
	
	public double getUpperBound(int n) {
		return demographicFunction.getUpperBound(n); 
	}

    // **************************************************************
    // Units IMPLEMENTATION
    // **************************************************************

	/**
	 * Sets the units these coalescent intervals are 
	 * measured in.
	 */
	public final void setUnits(int u)
	{
		demographicFunction.setUnits(u);
	}

	/**
	 * Returns the units these coalescent intervals are 
	 * measured in.
	 */
	public final int getUnits()
	{
		return demographicFunction.getUnits();
	}
	
	/** The demographic function. */
	DemographicFunction demographicFunction = null;
	
	/** The intervals. */
	IntervalList intervals = null;
}