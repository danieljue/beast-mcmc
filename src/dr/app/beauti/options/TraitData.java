package dr.app.beauti.options;

import dr.evolution.alignment.Alignment;
import dr.evolution.distance.DistanceMatrix;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class TraitData {
    public static final String TRAIT_SPECIES = "species";

    public static enum TraitType {
        DISCRETE,
        INTEGER,
        CONTINUOUS;

        public String toString() {
            return name().toLowerCase();
        }
    }

    private TraitType traitType = TraitType.DISCRETE;

    private final String fileName;
    private String name;

    protected final BeautiOptions options;

    public TraitData(BeautiOptions options, String name, String fileName, TraitType traitType) {
        this.options = options;
        this.name = name;
        this.fileName = fileName;
        this.traitType = traitType;
    }

    /////////////////////////////////////////////////////////////////////////

    public TraitType getTraitType() {
        return traitType;
    }

    public void setTraitType(TraitType traitType) {
        this.traitType = traitType;
    }

//    public TraitOptions getTraitOptions() {
//        return traitOptions;
//    }

    public int getSiteCount() {
        return 0;
    }

    public int getTaxaCount() {
        return options.taxonList.getTaxonCount();
    }

    public String getDataType() {
        return getTraitType().toString();
    }

    public String getFileName() {
        return fileName;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Set<String> getStatesOfTrait(Taxa taxonList) {
        return getStatesListOfTrait(taxonList, getName());
    }

    // todo this needs to go somewhere else...
    public static String getPhylogeographicDescription() {
        return "Discrete phylogeographic inference in BEAST (PLoS Comput Biol. 2009 Sep;5(9):e1000520)";
    }


    public static Set<String> getStatesListOfTrait(Taxa taxonList, String traitName) {
        Set<String> states = new HashSet<String>();

        if (taxonList == null) {
            throw new IllegalArgumentException("taxon list is null");
        }

        for (int i = 0; i < taxonList.getTaxonCount(); i++) {
            Taxon taxon = taxonList.getTaxon(i);
            String attr = (String) taxon.getAttribute(traitName);

            // ? is used to denote missing data so is not a state...
            if (attr != null && !attr.equals("?")) {
                states.add(attr);
            }
        }


        return states;
    }

    public String toString() {
        return name;
    }
}
