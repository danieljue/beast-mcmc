package dr.evomodel.speciation;

import dr.evolution.coalescent.DemographicFunction;
import dr.evolution.coalescent.ScaledDemographic;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Units;
import dr.inference.model.Likelihood;
import dr.inference.model.Model;
import dr.xml.*;
import jebl.util.FixedBitSet;

import java.util.Arrays;

/**
 * Compute coalecent log-liklihood of a set of gene trees embedded inside one species tree.
 *
 * @author joseph
 *         Date: 26/05/2008
 */
public class TreePartitionCoalescent extends Likelihood.Abstract implements Units {
    public static final String SPECIES_COALESCENT = "speciesCoalescent";
    private final SpeciesTreeModel spTree;
    private final SpeciesBindings species;
    private boolean checkCompatibility;
    private final boolean[] compatibleCheckRequited;

    public TreePartitionCoalescent(SpeciesBindings species, SpeciesTreeModel tree) {
        super(tree);
        spTree = tree;
        this.species = species;

        spTree.addModelRestoreListener(this);

        // recompute on any change in geneTree -
        // possible optimization: keep track which tree changed.
        final SpeciesBindings.GeneTreeInfo[] trees = species.getGeneTrees();
        for(SpeciesBindings.GeneTreeInfo geneTree : trees) {
            geneTree.tree.addModelListener(this);
        }

        compatibleCheckRequited = new boolean[trees.length];
        Arrays.fill(compatibleCheckRequited, false);
        checkCompatibility = false;
    }

    // override this for efficiency, otherwise the overriden makeDirty, which results in additional overhead is called
    public void modelRestored(Model model) {
        super.makeDirty();
    }

    // Upon a direct "make dirty" enable all compatibility checks, since the last call to calculateLogLikelihood may have
    // found a non compatible tree and returned -inf. This case is not explicitly saved.
    public void makeDirty() {
        super.makeDirty(); 
        checkCompatibility = true;
        for(int i = 0; i < species.getGeneTrees().length; i++) {
            compatibleCheckRequited[i] = true;
        }
    }

    protected double calculateLogLikelihood() {
        if( checkCompatibility ) {
            boolean compatibility = true;

            for(int i = 0; i < compatibleCheckRequited.length; ++i) {
                if( compatibleCheckRequited[i] ) {

                    if( !spTree.isCompatible(species.getGeneTrees()[i]) ) {
                        compatibility = false;
                    }
                    compatibleCheckRequited[i] = false;
                    //System.out.println("check compatibility:" + species.getGeneTrees()[i].tree.getId() + " - " + compatibility );
                }
            }
            if( !compatibility ) {
                return Double.NEGATIVE_INFINITY;
            }
            checkCompatibility = false;
        }

        double logl = 0;
        int[] info = {0, 0};
        for( SpeciesBindings.GeneTreeInfo geneTree : species.getGeneTrees() ) {
            logl += treeLogLikelihood(geneTree, spTree.getRoot(), info, geneTree.popFactor());
        }

        return logl;
    }

    private final boolean verbose = false;

    private double treeLogLikelihood(SpeciesBindings.GeneTreeInfo geneTree, NodeRef node, int[] info, double popFactor) {
        // number of lineages remaining at node
        int nLineages;
        // location in coalescent list (optimization)
        int indexInClist = 0;
        // accumulated log-likelihood inBranchh from node to it's parent
        double like = 0;

        final double t0 = spTree.getNodeHeight(node);

        final SpeciesBindings.CoalInfo[] cList = geneTree.getCoalInfo();

        if( verbose ) {
            if( spTree.isRoot(node) ) {
                System.err.println("gtree:" + geneTree.tree.getId());
                System.err.println("t0 " + t0);
                for(int k = 0; k < cList.length; ++k) {
                    System.err.println(k + " " + cList[k].ctime + " " + cList[k].sinfo[0] + " " + cList[k].sinfo[1]);
                }
            }
        }

        if( spTree.isExternal(node) ) {
            nLineages = geneTree.nLineages(spTree.speciesIndex(node));
            indexInClist = 0;
        } else {
            //assert spTree.getChildCount(node) == 2;

            nLineages = 0;
            for(int nc = 0; nc < 2; ++nc) {
                final NodeRef child = spTree.getChild(node, nc);
                like += treeLogLikelihood(geneTree, child, info, popFactor);
                nLineages += info[0];
                indexInClist = Math.max(indexInClist, info[1]);
            }

            // The root of every gene tree (last coalescent point) should be always above
            // root of species tree
            assert indexInClist < cList.length;

            // Skip over (presumably, not tested by assert) non interesting coalescent
            // events to the first event before speciation point

            while( cList[indexInClist].ctime < t0 ) {
                ++indexInClist;
            }
        }

        final boolean isRoot = spTree.isRoot(node);

        // Upper limit
        final double stopTime = isRoot ? Double.MAX_VALUE : (t0 + spTree.getBranchLength(node));

        // demographic function is 0 based (relative to node height)
        // time away from node
        double lastTime = 0.0;

        // demographic function across branch
        DemographicFunction demog = spTree.getNodeDemographic(node);
        if( popFactor > 0 ) {
            demog = new ScaledDemographic(demog, popFactor);
        }

//        if(false) {
//            final double duration = isRoot ? cList[cList.length - 1].ctime - t0 : stopTime;
//            double demographicAtCoalPoint = demog.getDemographic(duration);
//            double intervalArea = demog.getIntegral(0, duration);
//            if( demographicAtCoalPoint < 1e-12 * (duration/intervalArea) ) {
//               return Double.NEGATIVE_INFINITY;
//            }
//        }
        // Species sharing this branch
        FixedBitSet subspeciesSet = spTree.spSet(node);

        if( verbose ) {
            System.err.println(Tree.Utils.uniqueNewick(spTree, node) + " nl " + nLineages
                    + " " + subspeciesSet + " t0 - st " + t0 + " - " + stopTime);
        }

        while( nLineages > 1 ) {
            assert ( indexInClist < cList.length );

            final double nextT = cList[indexInClist].ctime;

            if( nextT > stopTime ) {
                break;
            }

            if( nonEmptyIntersection(cList[indexInClist].sinfo, subspeciesSet) ) {
                final double time = nextT - t0;

                final double interval = demog.getIntegral(lastTime, time);
                lastTime = time;

                final int nLineageOver2 = (nLineages * (nLineages - 1)) / 2;
                like -= nLineageOver2 * interval;

                final double pop = demog.getDemographic(time);
                like -= Math.log(pop);

                --nLineages;
            }
            ++indexInClist;
        }


        if( nLineages > 1 ) {
            // add term for No coalescent until root
            final double interval = demog.getIntegral(lastTime, stopTime - t0);

            final int nLineageOver2 = (nLineages * (nLineages - 1)) / 2;

            like -= nLineageOver2 * interval;
        }

        info[0] = nLineages;
        info[1] = indexInClist;
        if( verbose ) {
            System.err.println(Tree.Utils.uniqueNewick(spTree, node) + " stopTime " + stopTime +
                    " nl " + nLineages + " icl " + indexInClist);
        }
        return like;
    }

    public void modelChangedEvent(Model model, Object object, int index) {
        //super.modelChangedEvent(model, object, index);
        // not the above for efficiency, otherwise the overriden makeDirty, which results in additional overhead is called.
        super.makeDirty();
        
        if( model == spTree ) {
          if( object == spTree && index != -1 ) {
            // Species tree scaling
            checkCompatibility = true;
            Arrays.fill(compatibleCheckRequited, true);
          }
        } else {

            final SpeciesBindings.GeneTreeInfo[] trees = species.getGeneTrees();
            for(int i = 0; i < species.getGeneTrees().length; i++) {
                if( trees[i].tree == model ) {
                    checkCompatibility = true;
                    compatibleCheckRequited[i] = true;
                    break;
                }
            }
        }
    }

    private boolean nonEmptyIntersection(FixedBitSet[] sinfo, FixedBitSet subspeciesSet) {
        for( FixedBitSet nodeSpSet : sinfo ) {
            if( nodeSpSet.intersectCardinality(subspeciesSet) == 0 ) {
                return false;
            }
        }
        return true;
    }


    public Type getUnits() {
        return spTree.getUnits();
    }

    public void setUnits(Type units) {
        assert false;
    }

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {
            final SpeciesBindings sb = (SpeciesBindings) xo.getChild(SpeciesBindings.class);
            final SpeciesTreeModel tree = (SpeciesTreeModel) xo.getChild(SpeciesTreeModel.class);
            return new TreePartitionCoalescent(sb, tree);
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return new XMLSyntaxRule[]{
                    new ElementRule(SpeciesBindings.class),
                    new ElementRule(SpeciesTreeModel.class),
            };
        }

        public String getParserDescription() {
            return "Compute coalecent log-liklihood of a set of gene trees embedded inside one species tree.";
        }

        public Class getReturnType() {
            return TreePartitionCoalescent.class;
        }

        public String getParserName() {
            return SPECIES_COALESCENT;
        }
    };
}