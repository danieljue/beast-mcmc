package dr.app.seqgen;

import dr.evolution.io.*;
import dr.evolution.tree.Tree;
import dr.evolution.tree.NodeRef;
import dr.evolution.datatype.Nucleotides;
import dr.evomodel.substmodel.*;
import dr.evomodel.sitemodel.GammaSiteModel;
import dr.evomodel.sitemodel.SiteModel;

import java.io.*;
import java.util.List;

import jebl.evolution.io.NexusExporter;
import jebl.evolution.alignments.Alignment;
import jebl.evolution.alignments.BasicAlignment;
import jebl.evolution.sequences.*;
import jebl.evolution.taxa.Taxon;
import jebl.math.Random;

/**
 * @author Andrew Rambaut
 * @version $Id$
 */
public class SeqGen {

    public SeqGen(String treeFileName, String outputFileName) {
        int length = 750;

        double[] frequencies = new double[] { 0.25, 0.25, 0.25, 0.25 };
        double kappa = 10.0;
        double alpha = 0.5;
        double substitutionRate = 1.0;
        int categoryCount = 8;

        FrequencyModel freqModel = new FrequencyModel(Nucleotides.INSTANCE, frequencies);

        HKY hkyModel = new HKY(kappa, freqModel);
        GammaSiteModel siteModel = new GammaSiteModel(hkyModel, alpha, categoryCount);

        Tree tree = null;

        FileReader reader = null;
        try {
            reader = new FileReader(treeFileName);
            TreeImporter importer = new NexusImporter(reader);

            tree =  importer.importNextTree();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (Importer.ImportException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Alignment alignment = simulateAlignment(tree, length, substitutionRate, freqModel, hkyModel, siteModel);

        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFileName);
            NexusExporter exporter = new NexusExporter(writer);

            exporter.exportAlignment(alignment);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private Alignment simulateAlignment(Tree tree, int length,
                                        double substitutionRate,
                                        FrequencyModel freqModel,
                                        SubstitutionModel substModel,
                                        SiteModel siteModel) {

        int[] initialSequence = new int[length];

        drawSequence(initialSequence, freqModel);

        int[] siteCategories = new int[length];

        drawSiteCategories(siteModel, siteCategories);

        double[] rates = new double[siteModel.getCategoryCount()];
        for (int i = 0; i < rates.length; i++) {
            rates[i] = siteModel.getRateForCategory(i) * substitutionRate;
        }

        for (int i = 0; i < tree.getChildCount(tree.getRoot()); i++) {
            NodeRef child = tree.getChild(tree.getRoot(), i);
            evolveSequences(initialSequence, tree, child, substModel, siteCategories, rates);
        }

        BasicAlignment alignment = new BasicAlignment();
        List<NucleotideState> nucs = jebl.evolution.sequences.Nucleotides.getCanonicalStates();
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            NodeRef node = tree.getExternalNode(i);
            int[] seq = (int[])tree.getNodeTaxon(node).getAttribute("seq");
            State[] states = new State[seq.length];
            for (int j = 0; j < states.length; j++) {
                states[j] = nucs.get(seq[j]);
            }
            BasicSequence sequence = new BasicSequence(SequenceType.NUCLEOTIDE,
                    Taxon.getTaxon(tree.getNodeTaxon(node).getId()),
                    states);
            alignment.addSequence(sequence);
        }

        return alignment;
    }

    private void drawSiteCategories(SiteModel siteModel, int[] siteCategories) {
        double[] categoryProportions = siteModel.getCategoryProportions();
        double[] cumulativeProportions = new double[categoryProportions.length];
        cumulativeProportions[0] = categoryProportions[0];
        for (int i = 1; i < cumulativeProportions.length; i++) {
            cumulativeProportions[i] = cumulativeProportions[i - 1] + categoryProportions[i];
        }

        for (int i = 0; i < siteCategories.length; i++) {
            siteCategories[i] = draw(cumulativeProportions);
        }
    }

    private void drawSequence(int[] initialSequence, FrequencyModel freqModel) {
        double[] freqs = freqModel.getCumulativeFrequencies();
        for (int i = 0; i < initialSequence.length; i++) {
            initialSequence[i] = draw(freqs);
        }
    }

    private void evolveSequences(int[] sequence0,
                                 Tree tree, NodeRef node,
                                 SubstitutionModel substModel,
                                 int[] siteCategories,
                                 double[] categoryRates) {
        int stateCount = substModel.getDataType().getStateCount();

        int[] sequence1 = new int[sequence0.length];

        double[][][] transitionProbabilities = new double[siteCategories.length][stateCount][stateCount];

        for (int i = 0; i < categoryRates.length; i++) {
            double branchLength = tree.getBranchLength(node) * categoryRates[i];
            double[] tmp = new double[stateCount * stateCount];
            substModel.getTransitionProbabilities(branchLength, tmp);

            int l = 0;
            for (int j = 0; j < stateCount; j ++) {
                transitionProbabilities[i][j][0] = tmp[l];
                l++;
                for (int k = 1; k < stateCount; k ++) {
                    transitionProbabilities[i][j][k] = transitionProbabilities[i][j][k - 1] + tmp[l];
                    l++;
                }

            }
        }

        evolveSequence(sequence0, siteCategories, transitionProbabilities, sequence1);

        if (!tree.isExternal(node)) {
            for (int i = 0; i < tree.getChildCount(node); i++) {
                NodeRef child = tree.getChild(node, i);
                evolveSequences(sequence1, tree, child, substModel, siteCategories, categoryRates);
            }
        } else {
            tree.getNodeTaxon(node).setAttribute("seq", sequence1);
        }
    }

    private void evolveSequence(int[] ancestralSequence,
                                int[] siteCategories,
                                double[][][] cumulativeTransitionProbabilities,
                                int[] descendentSequence) {

        for (int i = 0; i < ancestralSequence.length; i++) {
            descendentSequence[i] = draw(cumulativeTransitionProbabilities[siteCategories[i]][ancestralSequence[i]]);
        }
    }

    /**
     * draws a state from using a set of cumulative frequencies (last value should be 1.0)
     * @param cumulativeFrequencies
     * @return
     */
    private int draw(double[] cumulativeFrequencies) {
        double r = Random.nextDouble();

        // defensive - make sure that it is actually set...
        int state = -1;

        for (int j = 0; j < cumulativeFrequencies.length; j++) {
            if (r < cumulativeFrequencies[j]) {
                state = j;
                break;
            }
        }

        assert(state != -1);

        return state;
    }

    public static void main(String[] argv) {
        new SeqGen(argv[0], argv[1]);
    }
}
