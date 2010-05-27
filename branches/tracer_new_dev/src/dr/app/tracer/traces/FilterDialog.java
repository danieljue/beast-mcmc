package dr.app.tracer.traces;

import dr.inference.trace.Filter;
import dr.inference.trace.FilteredTraceList;
import dr.inference.trace.TraceDistribution;
import dr.inference.trace.TraceFactory;
import org.virion.jam.panels.OptionsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @author Walter Xie
 * @version $Id: FrequencyPanel.java,v 1.1.1.2 2006/04/25 23:00:09 rambaut Exp $
 */
public class FilterDialog {
    private JFrame frame;
    List<FilteredTraceList> filteredTraceListGroup;
    String traceName;

    JComboBox treeFileCombo;
    JTextField typeField;
    JTextField nameField;


    FilterPanel filterPanel;
    OptionsPanel tittlePanel;

    public FilterDialog(JFrame frame) {
        this.frame = frame;

        treeFileCombo = new JComboBox();
        treeFileCombo.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (filteredTraceListGroup != null && treeFileCombo.getSelectedIndex() > 0)
                    initComponents(filteredTraceListGroup.get(treeFileCombo.getSelectedIndex()));
            }
        });
        typeField = new JTextField();
        typeField.setColumns(20);
        typeField.setEditable(false);
        nameField = new JTextField();
        nameField.setColumns(30);
        nameField.setEditable(false);

        tittlePanel = new OptionsPanel(12, 12);
        tittlePanel.addComponentWithLabel("Tree File : ", treeFileCombo);
        tittlePanel.addComponentWithLabel("Trace Name : ", nameField);
        tittlePanel.addComponentWithLabel("Trace Type : ", typeField);


    }

    public void showDialog(String traceName, List<FilteredTraceList> filteredTraceListGroup) {
        this.filteredTraceListGroup = filteredTraceListGroup;
        this.traceName = traceName;

        treeFileCombo.removeAllItems();
        for (FilteredTraceList treeFile : filteredTraceListGroup) {
            treeFileCombo.addItem(treeFile.getName());
        }

        treeFileCombo.setSelectedIndex(0);
        initComponents(filteredTraceListGroup.get(0));

        JPanel basePanel = new JPanel(new BorderLayout(0, 0));
        basePanel.add(tittlePanel, BorderLayout.NORTH);
        basePanel.add(filterPanel, BorderLayout.CENTER);

        Object[] options = {"Apply Filter", "Remove Filter", "Cancel"};
        JOptionPane optionPane = new JOptionPane(basePanel,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.YES_NO_CANCEL_OPTION,
                null,
                options,
                options[2]);
        optionPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        final JDialog dialog = optionPane.createDialog(frame, "Create A Filter");
        dialog.pack();
        dialog.setVisible(true);

        Object result = optionPane.getValue();

        FilteredTraceList filteredTraceList = filteredTraceListGroup.get(treeFileCombo.getSelectedIndex());
        TraceDistribution td = filteredTraceList.getDistributionStatistics(filteredTraceList.getTraceIndex(traceName));

        if (result.equals(options[0])) {
            Filter f = filteredTraceList.getFilter(traceName);

            if (f == null) {
                f = new Filter(traceName, td.getTraceType(), filterPanel.getSelectedValues(), td.getValues());
            } else {
                f.setIn(filterPanel.getSelectedValues(), td.getValues());
            }

            filteredTraceList.setFilter(f);

        } else if (result.equals(options[1])) {
            filteredTraceList.removeFilter(traceName);

        } else if (result.equals(options[2])) {

        }


    }

    private void initComponents(FilteredTraceList filteredTraceList) {

        TraceDistribution td = filteredTraceList.getDistributionStatistics(filteredTraceList.getTraceIndex(traceName));

        typeField.setText(td.getTraceType().toString());
        nameField.setText(traceName);

        Filter f = filteredTraceList.getFilter(traceName);

        if (td.getTraceType() == TraceFactory.TraceType.CONTINUOUS) {

        } else {// integer and string
            List vl = td.credSet.getValues();
            String[] all = new String[vl.size()];
            String[] sel;

            if (f == null) {
                sel = null;
            } else {
                sel = f.getIn();
            }

            for (int i = 0; i < vl.size(); i++) {
//                if (f == null || (!Utils.contains(sel, all[i])) )  // f != null && (!Utils.contains(sel, all[i]))
                    all[i] = vl.get(i).toString();                 
            }

            filterPanel = new FilterDiscretePanel(all, sel);
        }


    }

    public String getName() {
        return nameField.getText();
    }

    abstract class FilterPanel extends JPanel {
        abstract Object[] getSelectedValues();
    }

    class FilterDiscretePanel extends FilterPanel {
        JList allValues;
        JList selectedValues;
        JButton selectButton;
        
        FilterDiscretePanel(String[] allValuesArray, String[] selectedValuesArray) {
            setLayout(new FlowLayout());

            allValues = new JList(allValuesArray);
            allValues.setVisibleRowCount(6);
            allValues.setFixedCellWidth(100);
            allValues.setFixedCellHeight(15);
            allValues.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            add(new JScrollPane(allValues));

            if (selectedValuesArray != null) {
                int[] indices = new int[selectedValuesArray.length];
                for (int i = 0; i < indices.length; i++) {
                    for (int j = 0; j < allValuesArray.length; j++) {
                        if (selectedValuesArray[i].equals(allValuesArray[j])) {
                            indices[i] = j;
                            break;
                        }
                    }
                }

                allValues.setSelectedIndices(indices);
            }

            selectButton = new JButton("Select >>>");
            selectButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    selectedValues.setListData(allValues.getSelectedValues());
                }
            });
            add(selectButton);

            if (selectedValuesArray == null) {
                selectedValues = new JList();
            } else {
                selectedValues = new JList(selectedValuesArray);
            }
            selectedValues.setVisibleRowCount(6);
            selectedValues.setFixedCellWidth(100);
            selectedValues.setFixedCellHeight(15);
            selectedValues.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            add(new JScrollPane(selectedValues));
        }

        public Object[] getSelectedValues() {
            return allValues.getSelectedValues();
        }
        
    }

}