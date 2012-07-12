package dr.app.bss;

import jam.framework.Exportable;
import jam.panels.ActionPanel;
import jam.panels.OptionsPanel;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import dr.app.gui.components.RealNumberField;

@SuppressWarnings("serial")
public class EpochModelPanel extends JPanel implements Exportable {

	private BeagleSequenceSimulatorFrame frame;
	private BeagleSequenceSimulatorData data;
	private OptionsPanel optionPanel;

	private JComboBox substitutionCombo;
	private RealNumberField[] substitutionParameterFields = new RealNumberField[BeagleSequenceSimulatorData.substitutionParameterNames.length];
	private int epochCount = 1;
	
	public EpochModelPanel(final BeagleSequenceSimulatorFrame frame,
			final BeagleSequenceSimulatorData data) {

		super();

		this.frame = frame;
		this.data = data;

		setOpaque(false);
		setLayout(new BorderLayout());

		optionPanel = new OptionsPanel(12, 12, SwingConstants.CENTER);
		add(optionPanel, BorderLayout.NORTH);
		
		substitutionCombo = new JComboBox();
		substitutionCombo.setOpaque(false);

		for (String substitutionModel : BeagleSequenceSimulatorData.substitutionModels) {
			substitutionCombo.addItem(substitutionModel);
		}// END: fill loop

		substitutionCombo.addItemListener(new ListenSubstitutionCombo());

		for (int i = 0; i < BeagleSequenceSimulatorData.substitutionParameterNames.length; i++) {
			substitutionParameterFields[i] = new RealNumberField();
			substitutionParameterFields[i].setColumns(8);
			substitutionParameterFields[i].setValue(data.substitutionParameterValues[i]);
		}// END: fill loop

		setSubstitutionArguments();
		
		
		
		
	}// END: Constructor

	private void setSubstitutionArguments() {

		optionPanel.removeAll();
		optionPanel.addComponents(new JLabel("Branch substitution model:"), substitutionCombo);
		optionPanel.addSeparator();
		optionPanel.addLabel("Set parameter values:");

		int substModelIndex = substitutionCombo.getSelectedIndex();

		for (int j = 0; j < epochCount; j++) {

			for (int i = 0; i < data.substitutionParameterIndices[substModelIndex].length; i++) {

				int k = data.substitutionParameterIndices[substModelIndex][i];

				JPanel panel = new JPanel(new BorderLayout(6, 6));
				panel.add(substitutionParameterFields[k], BorderLayout.WEST);
				panel.setOpaque(false);
				optionPanel.addComponentWithLabel(BeagleSequenceSimulatorData.substitutionParameterNames[k] + ":", panel);

			}// END: indices loop

		}
		
        ActionPanel actionPanel = new ActionPanel(false);
        actionPanel.setAddAction(addEpochAction);
        actionPanel.setRemoveAction(removeEpochAction);
        optionPanel.add(actionPanel);
        
		addEpochAction.setEnabled(true);
		if (epochCount == 1) {
			removeEpochAction.setEnabled(false);
		} else {
			removeEpochAction.setEnabled(true);
		}
        
	}// END: setSubstitutionArguments

	private class ListenSubstitutionCombo implements ItemListener {
		public void itemStateChanged(ItemEvent ie) {

			setSubstitutionArguments();
			frame.fireModelChanged();

		}// END: actionPerformed
	}// END: ListenSubstitutionCombo

	public void collectSettings() {

		data.substitutionModel = substitutionCombo.getSelectedIndex();
		for (int i = 0; i < BeagleSequenceSimulatorData.substitutionParameterNames.length; i++) {

			data.substitutionParameterValues[i] = substitutionParameterFields[i].getValue();

		}// END: fill loop
	}// END: collectSettings
	
	// TODO
	private Action addEpochAction = new AbstractAction("+") {

		public void actionPerformed(ActionEvent ae) {

			epochCount++;
		
			setSubstitutionArguments();
			
			System.out.println("epochCount: " + epochCount);
		}// END: actionPerformed
	};
	
	// TODO	
	private Action removeEpochAction = new AbstractAction("-") {

		public void actionPerformed(ActionEvent ae) {

			if (epochCount > 1) {
				epochCount--;
			}
			
			setSubstitutionArguments();
			
			System.out.println("epochCount: " + epochCount);	
		}// END: actionPerformed
	};
	
	public JComponent getExportableComponent() {
		return this;
	}//END: getExportableComponent

}// END: class
