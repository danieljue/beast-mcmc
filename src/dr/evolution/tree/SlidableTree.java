
package dr.evolution.tree;

import dr.evolution.util.Taxon;
import dr.math.MathUtils;

/**
 * Interface for a binary tree which can support a Mau-et-al MCMC move 
 * in which node heights `slide' and can change topology.
 *
 * @author Graham Jones
 * Date 2012-03-30
 *
 */

/*
The aim is to simplify code by not having to use a MutableTree with its long list of
methods. It is (2012-05-11) partially helping. The users of SlidableTree are

1. AlloppLeggedTree which contains ('is') a SimpleTree (so a MutableTree anyway). In this
case the SlidableTree methods are implemented by calling SimpleTree methods.

2. AlloppDiploidHistory, where it works as intended. A SimpleTree is made for testing purposes.

3. PopsIOSpeciesTreeModel. 2012-05-11 needs to be a Tree for logging. So I
make and update a SimpleTree and delegate to that. Not great.

4. MulSpeciesTreeModel does not use it yet, but could do. Currently a MutableTree and
uses near-copy of JH's code for MCMC moves. Same issue as PopsIOSpeciesTreeModel if use
SlidableTree.

It uses NodeRefs cause that's what SimpleTree uses. This means that clients have
to implement get/setNumber when they have no use for node numbers.

 */


public interface SlidableTree {

    NodeRef getSlidableRoot();

    int getSlidableNodeCount();

    Taxon getSlidableNodeTaxon(NodeRef node);

    double getSlidableNodeHeight(NodeRef node);

    void setSlidableNodeHeight(NodeRef node, double height);

    boolean isExternalSlidable(NodeRef node);

    NodeRef getSlidableChild(NodeRef node, int i);

    void replaceSlidableChildren(NodeRef node, NodeRef lft, NodeRef rgt);

    void replaceSlidableRoot(NodeRef root);


    public class Utils {

        /*
           * This is an implementation of the ideas of
           *
           * Bayesian Phylogenetic Inference via Markov Chain Monte Carlo Methods
           * Bob Mau, Michael A. Newton, Bret Larget
           * Biometrics, Vol. 55, No. 1 (Mar., 1999), pp. 1-12
           *
           * similar to implementation by Joseph Heled in TreeNodeSlide.
           * This version works on a SlidableTree rather than a SimpleTree and is
           * simpler than JH's version. It does not keep track of what was swapped.
           * Instead I assign popvalues to nodes via an ordering of species (see
           * fillinpopvals() in network).
           */


        /*
           * For Mau-Newton-Larget MCMC moves on tree.
           * Do this, then change a node height, then call mnlReconstruct()
           *
           */
        static public NodeRef[] mnlCanonical(SlidableTree tree) {
            final int count = tree.getSlidableNodeCount();
            NodeRef[] order = new NodeRef[count];
            mnlCanonicalSub(tree, tree.getSlidableRoot(), 0, order);
            return order;
        }


        /*
           * For Mau-etal MCMC moves on tree
           */
        static public void mnlReconstruct(SlidableTree tree, NodeRef[] order) {
            final NodeRef root = mnlReconstructSub(tree, 0, (order.length - 1) / 2, order);
            tree.replaceSlidableRoot(root);
        }


        /*
       * ****************** private methods **********************
       */


        static private int mnlCanonicalSub(SlidableTree tree, NodeRef node,
                                           int nextloc, NodeRef[] order) {
            if( tree.isExternalSlidable(node) ) {
                order[nextloc] = node;     assert (nextloc & 0x1) == 0;
                nextloc++;
                return nextloc;
            }
            final boolean swap = MathUtils.nextBoolean();
            nextloc = mnlCanonicalSub(tree, tree.getSlidableChild(node, swap ? 1 : 0), nextloc, order);
            order[nextloc] = node;   assert (nextloc & 0x1) == 1;
            nextloc++;
            nextloc = mnlCanonicalSub(tree, tree.getSlidableChild(node, swap ? 0 : 1), nextloc, order);
            return nextloc;
        }



        static private NodeRef mnlReconstructSub(SlidableTree tree, int from, int to,
                                                 NodeRef[] order) {
            if (from == to) {
                return order[2*from];
            }
            int rootIndex = highestNode(tree, order, from, to);
            NodeRef root = order[2 * rootIndex + 1];
            NodeRef lft = mnlReconstructSub(tree, from, rootIndex, order);
            NodeRef rgt = mnlReconstructSub(tree, rootIndex+1, to, order);
            tree.replaceSlidableChildren(root, lft, rgt);
            return root;
        }






        static private int highestNode(SlidableTree tree, NodeRef[] order, int from, int to) {
            int rootIndex = -1;
            double maxh = -1.0;

            for (int i = from; i < to; ++i) {
                final double h = tree.getSlidableNodeHeight(order[2 * i + 1]);
                if (h > maxh) {
                    maxh = h;
                    rootIndex = i;
                }
            }
            return rootIndex;
        }



    }
}
