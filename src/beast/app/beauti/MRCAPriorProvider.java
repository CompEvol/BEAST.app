package beast.app.beauti;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import beast.app.inputeditor.BEASTObjectPanel;
import beast.app.inputeditor.BeautiDoc;
import beast.app.inputeditor.TaxonSetDialog;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.Tree;
import beast.base.inference.Distribution;
import beast.base.inference.Logger;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.OneOnX;

public class MRCAPriorProvider implements PriorProvider {
	
	@Override
	public List<Distribution> createDistribution(BeautiDoc doc) {
    	MRCAPrior prior = new MRCAPrior();
        try {

            List<Tree> trees = new ArrayList<>();
            doc.scrubAll(true, false);
            State state = (State) doc.pluginmap.get("state");
            for (StateNode node : state.stateNodeInput.get()) {
                if (node instanceof Tree) { // && ((Tree) node).m_initial.get() != null) {
                    trees.add((Tree) node);
                }
            }
            int treeIndex = 0;
            if (trees.size() > 1) {
                String[] treeIDs = new String[trees.size()];
                for (int j = 0; j < treeIDs.length; j++) {
                    treeIDs[j] = trees.get(j).getID();
                }
                treeIndex = JOptionPane.showOptionDialog(null, "Select a tree", "MRCA selector",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        treeIDs, trees.get(0));
            }
            if (treeIndex < 0) {
                return null;
            }
            prior.treeInput.setValue(trees.get(treeIndex), prior);
            TaxonSet taxonSet = new TaxonSet();

            TaxonSetDialog dlg = new TaxonSetDialog(taxonSet, PriorListInputEditor.getTaxonCandidates(prior, doc), doc);
            if (!dlg.showDialog() || dlg.taxonSet.getID() == null || dlg.taxonSet.getID().trim().equals("")) {
                return null;
            }
            taxonSet = dlg.taxonSet;
            if (taxonSet.taxonsetInput.get().size() == 0) {
            	JOptionPane.showMessageDialog(doc.beauti, "At least one taxon should be included in the taxon set",
            			"Error specifying taxon set", JOptionPane.ERROR_MESSAGE);
            	return null;
            }
            int i = 1;
            String id = taxonSet.getID();
            while (doc.pluginmap.containsKey(taxonSet.getID()) && doc.pluginmap.get(taxonSet.getID()) != taxonSet) {
            	taxonSet.setID(id + i);
            	i++;
            }
            BEASTObjectPanel.addPluginToMap(taxonSet, doc);
            prior.taxonsetInput.setValue(taxonSet, prior);
            prior.setID(taxonSet.getID() + ".prior");
            // this sets up the type
            prior.distInput.setValue(new OneOnX(), prior);
            // this removes the parametric distribution
            prior.distInput.setValue(null, prior);

            Logger logger = (Logger) doc.pluginmap.get("tracelog");
            logger.loggersInput.setValue(prior, logger);
        } catch (Exception e) {
            // TODO: handle exception
        }
        List<Distribution> selectedPlugins = new ArrayList<>();
        selectedPlugins.add(prior);
        PriorListInputEditor.addCollapsedID(prior.getID());
        return selectedPlugins;
    }

	
	/* expect args to be TaxonSet, Distribution, tree partition (if any) */
	@Override
	public List<Distribution> createDistribution(BeautiDoc doc, List<Object> args) {
    	MRCAPrior prior = new MRCAPrior();
        TaxonSet taxonSet = (TaxonSet) args.get(0);
        BEASTObjectPanel.addPluginToMap(taxonSet, doc);
        prior.taxonsetInput.setValue(taxonSet, prior);
        prior.setID(taxonSet.getID() + ".prior");
        // this removes the parametric distribution
        prior.distInput.setValue(args.get(1), prior);

        Logger logger = (Logger) doc.pluginmap.get("tracelog");
        logger.loggersInput.setValue(prior, logger);

        if (args.size() <= 2) {
            doc.scrubAll(true, false);
            State state = (State) doc.pluginmap.get("state");
            for (StateNode node : state.stateNodeInput.get()) {
                if (node instanceof Tree) { 
    	            prior.treeInput.setValue(node, prior);
    	            break;
                }
            }
        } else {
        	Object tree = doc.pluginmap.get("Tree.t:" + args.get(2));
            prior.treeInput.setValue(tree, prior);
        }
        
        List<Distribution> selectedPlugins = new ArrayList<>();
        selectedPlugins.add(prior);
        return selectedPlugins;
	}
	
	@Override
	public String getDescription() {
		return "MRCA prior";
	}
	
}