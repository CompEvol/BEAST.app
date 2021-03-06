package beast.app.inputeditor;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.tree.TraitSet;
import beast.base.evolution.tree.Tree;

import java.awt.*;
import java.text.DateFormat;
import java.time.format.DateTimeParseException;
import java.util.EventObject;
import java.util.List;
import java.util.Map;

public class TipDatesInputEditor extends BEASTObjectInputEditor {

    private static String DATE_FORMAT_HELP_MESSAGE =
            "If the radio button on the second line is selected, this format string " +
                    "will be used to convert the entries in the second column of the table " +
                    "into heights (ages in years before the most recent sample) in the " +
                    "second column.\n" +
                    "\n" +
                    "The format string may be any combination of the following special characters " +
                    "and other delimiting characters such as '-' or '/'.\n" +
                    "\n" +
                    "  Symbol  Meaning                     Examples\n" +
                    "  ------  -------                     --------\n" +
                    "   G       era                         AD; Anno Domini; A\n" +
                    "   u       year                        2004; 04\n" +
                    "   y       year-of-era                 2004; 04\n" +
                    "   D       day-of-year                 189\n" +
                    "   M/L     month-of-year               7; 07; Jul; July; J\n" +
                    "   d       day-of-month                10\n" +
                    "\n" +
                    "   Q/q     quarter-of-year             3; 03; Q3; 3rd quarter\n" +
                    "   Y       week-based-year             1996; 96\n" +
                    "   w       week-of-week-based-year     27\n" +
                    "   W       week-of-month               4\n" +
                    "   E       day-of-week                 Tue; Tuesday; T\n" +
                    "   e/c     localized day-of-week       2; 02; Tue; Tuesday; T\n" +
                    "   F       week-of-month               3\n" +
                    "\n" +
                    "(The table above is an extract from the documentation for the DateTimeFormatter " +
                    "class which is used by BEAST to parse dates.)\n" +
                    "\n" +
                    "Symbols in the above table representing numeric quantities (e.g. day-of-month " +
                    "and month-of-year) may be repeated to allow for zero-padding.  For instance, " +
                    "the format string \"d/m/y\" will match dates such as \"1/3/2004\" while \"dd/mm/y\" " +
                    "will match \"01/03/2004\".  In addition, use \"yy\" to match the short form of the year.\n" +
                    "\n" +
                    "Be aware that in all cases it is necessary that the format chosen be able to pinpoint a " +
                    "single day in the calendar. For instance, \"d/m\" is not a valid format string as it " +
                    "doesn't allow days to be uniquely identified.";


    public TipDatesInputEditor() {
    	super();
    }    
    public TipDatesInputEditor(BeautiDoc doc) {
        super(doc);
    }
    private static final long serialVersionUID = 1L;

    DateFormat dateFormat = DateFormat.getDateInstance();

    @Override
    public Class<?> type() {
        return Tree.class;
    }
    Tree tree;
    TraitSet traitSet;
    JComboBox<TraitSet.Units> unitsComboBox;
    JComboBox<String> relativeToComboBox;
    List<String> taxa;
    Object[][] tableData;
    boolean[] recordValid;
    JTable table;
    String m_sPattern = ".*(\\d\\d\\d\\d).*";
    JScrollPane scrollPane;
    List<Taxon> taxonsets;

    JRadioButton numericRadioButton, formattedDateRadioButton;
    ButtonGroup radioButtonGroup;
    JComboBox<String> dateFormatComboBox;

    @Override
    public void init(Input<?> input, BEASTInterface beastObject, int itemNr, ExpandOption isExpandOption, boolean addButtons) {
        m_bAddButtons = addButtons;
        this.itemNr = itemNr;
        if (itemNr >= 0) {
            tree = (Tree) ((List<?>) input.get()).get(itemNr);
        } else {
            tree = (Tree) input.get();
        }
        if (tree != null) {
            try {
                m_input = ((BEASTInterface) tree).getInput("trait");
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            m_beastObject = tree;
            traitSet = tree.getDateTrait();

            Box box = Box.createVerticalBox();

            JCheckBox useTipDates = new JCheckBox("Use tip dates", traitSet != null);
            useTipDates.addActionListener(e -> {
                    JCheckBox checkBox = (JCheckBox) e.getSource();
                    try {
                        if (checkBox.isSelected()) {
                            if (traitSet == null) {
                                traitSet = new TraitSet();
                                traitSet.initByName("traitname", "date",
                                        "taxa", tree.getTaxonset(),
                                        "value", "");
                                traitSet.setID("dateTrait.t:" + BeautiDoc.parsePartition(tree.getID()));
                            }
                            tree.setDateTrait(traitSet);
                        } else {
                            tree.setDateTrait(null);
                        }

                        refreshPanel();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                });
            Box box2 = Box.createHorizontalBox();
            box2.add(useTipDates);
            box2.add(Box.createHorizontalGlue());
            box.add(box2);

            if (traitSet != null) {
                box.add(createButtonBox());
                box.add(createListBox());
            }
            add(box);
        }
    } // init

    private Component createListBox() {
        taxa = traitSet.taxaInput.get().asStringList();
        String[] columnData = new String[]{"Name", "Date (raw value)", "Height"};
        tableData = new Object[taxa.size()][3];
        recordValid = new boolean[taxa.size()];
        convertTraitToTableData();
        // set up table.
        // special features: background shading of rows
        // custom editor allowing only Date column to be edited.
        table = new JTable(tableData, columnData) {
            private static final long serialVersionUID = 1L;

            // method that induces table row shading
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                //even index, selected or not selected
                comp.setForeground(Color.black);
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.lightGray);
                } else if (!recordValid[Index_row]) {
                    comp.setForeground(Color.WHITE);
                    comp.setBackground(Color.RED);
                } else if (Index_row % 2 == 0 && !isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(new Color(237, 243, 255));
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        // set up editor that makes sure only doubles are accepted as entry
        // and only the Date column is editable.
        table.setDefaultEditor(Object.class, new TableCellEditor() {
            JTextField m_textField = new JTextField();
            int m_iRow,
                    m_iCol;

            @Override
            public boolean stopCellEditing() {
                table.removeEditor();
                String text = m_textField.getText();

                tableData[m_iRow][m_iCol] = text;
                convertTableDataToTrait();
                convertTraitToTableData();
                return true;
            }

            @Override
            public boolean isCellEditable(EventObject anEvent) {
                return table.getSelectedColumn() == 1;
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int rowNr, int colNr) {
                if (!isSelected) {
                    return null;
                }
                m_iRow = rowNr;
                m_iCol = colNr;
                m_textField.setText((String) value);
                return m_textField;
            }

            @Override
            public boolean shouldSelectCell(EventObject anEvent) {
                return false;
            }

            @Override
            public void removeCellEditorListener(CellEditorListener l) {
            }

            @Override
            public Object getCellEditorValue() {
                return null;
            }

            @Override
            public void cancelCellEditing() {
            }

            @Override
            public void addCellEditorListener(CellEditorListener l) {
            }
        });
        int fontsize = table.getFont().getSize();
        table.setRowHeight(24 * fontsize / 13);
        scrollPane = new JScrollPane(table);

        return scrollPane;
    } // createListBox

    private void clearTable(boolean heightsOnly) {
         for (int i = 0; i < tableData.length; i++) {
            tableData[i][0] = taxa.get(i);
            if (!heightsOnly)
                tableData[i][1] = "0";
             tableData[i][2] = "0";
        }
    }

    /* synchronise table with data from traitSet BEASTObject */
    private void convertTraitToTableData() {
        clearTable(false);

        String[] traits = traitSet.traitsInput.get().split(",");
        for (String trait : traits) {
            trait = trait.replaceAll("\\s+", " ");
            String[] strs = trait.split("=");
            if (strs.length != 2) {
                break;
            }

            String taxonID = normalize(strs[0]);
            int taxonIndex = taxa.indexOf(taxonID);

            if (taxonIndex >= 0) {
                tableData[taxonIndex][1] = normalize(strs[1]);
                tableData[taxonIndex][0] = taxonID;
            } else {
            	Log.warning.println("WARNING: File contains taxon " + taxonID + " that cannot be found in alignment");
            }
        }

        boolean numericParseError = false;
        boolean dateParseError = false;
        double [] convertedValues = new double[taxa.size()];

        for (int i=0; i<tableData.length; i++) {
            recordValid[i] = true;

            try {
                convertedValues[i] = traitSet.convertValueToDouble((String) tableData[i][1]);
            } catch (DateTimeParseException ex) {
                dateParseError = true;
                recordValid[i] = false;
            } catch (IllegalArgumentException ex) {
                numericParseError = true;
                recordValid[i] = false;
            }
        }

        if (dateParseError) {
            JOptionPane.showMessageDialog(this,
                    "<html>Error interpreting one or more trait values as a formatted date or numeric value.<br><br>" +
                            "Problem taxa will be highlighted in table.</html>",
                    "Date parsing error",
                    JOptionPane.ERROR_MESSAGE);
            clearTable(true);
        } else if (numericParseError) {
            JOptionPane.showMessageDialog(this,
                    "Error interpreting one or more trait values as a numeric value.<br><br>" +
                            "Problem taxa will be highlighted in table.</html>",
                    "Date parsing error",
                    JOptionPane.ERROR_MESSAGE);
            clearTable(true);
        } else {
            if (traitSet.traitNameInput.get().equals(TraitSet.DATE_BACKWARD_TRAIT)) {
                Double minDate = Double.MAX_VALUE;
                for (int i = 0; i < tableData.length; i++) {
                    minDate = Math.min(minDate, convertedValues[i]);
                }
                for (int i = 0; i < tableData.length; i++) {
                    tableData[i][2] = convertedValues[i] - minDate;
                }
            } else {
                Double maxDate = 0.0;
                for (int i = 0; i < tableData.length; i++) {
                    maxDate = Math.max(maxDate, convertedValues[i]);
                }
                for (int i = 0; i < tableData.length; i++) {
                    tableData[i][2] = maxDate - convertedValues[i];
                }
            }
        }

        if (table != null) {
            for (int i = 0; i < tableData.length; i++) {
                table.setValueAt(tableData[i][1], i, 1);
                table.setValueAt(tableData[i][2], i, 2);
            }
        }
    } // convertTraitToTableData

    private String normalize(String str) {
        if (str.charAt(0) == ' ') {
            str = str.substring(1);
        }
        if (str.endsWith(" ")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    /**
     * synchronise traitSet BEAST object with table data
     */
    private void convertTableDataToTrait() {
        String trait = "";
        for (int i = 0; i < tableData.length; i++) {
            trait += taxa.get(i) + "=" + tableData[i][1];
            if (i < tableData.length - 1) {
                trait += ",\n";
            }
        }
        try {
            traitSet.traitsInput.setValue(trait, traitSet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * create box with comboboxes for selectin units and trait name *
     */
    private Box createButtonBox() {
        Box buttonBox = Box.createHorizontalBox();

        JLabel label = new JLabel("Dates specified: ");
        label.setAlignmentY(Component.TOP_ALIGNMENT);
        label.setMaximumSize(label.getPreferredSize());
        buttonBox.add(label);

        Box formatBox = Box.createVerticalBox();
        Box formatBoxFirstLine = Box.createHorizontalBox();
        Box formatBoxSecondLine = Box.createHorizontalBox();

        radioButtonGroup = new ButtonGroup();
        numericRadioButton = new JRadioButton("numerically as");
        numericRadioButton.setToolTipText("Interpret values as numerical times.");

        if (traitSet.dateTimeFormatInput.get() == null)
            numericRadioButton.setSelected(true);

        numericRadioButton.addActionListener(e -> {
            traitSet.dateTimeFormatInput.setValue(null, traitSet);
            refreshPanel();
        });

        radioButtonGroup.add(numericRadioButton);
        formatBoxFirstLine.add(numericRadioButton);

        unitsComboBox = new JComboBox<>(TraitSet.Units.values());
        unitsComboBox.setSelectedItem(traitSet.unitsInput.get());
        unitsComboBox.addActionListener(e -> {
                String selected = unitsComboBox.getSelectedItem().toString();
                try {
                    traitSet.unitsInput.setValue(selected, traitSet);
                    //System.err.println("Traitset is now: " + m_traitSet.m_sUnits.get());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        Dimension d = unitsComboBox.getPreferredSize();
        unitsComboBox.setMaximumSize(unitsComboBox.getPreferredSize());
        unitsComboBox.setSize(d);
        unitsComboBox.setEnabled(numericRadioButton.isSelected());
        formatBoxFirstLine.add(unitsComboBox);

        relativeToComboBox = new JComboBox<>(new String[]{"Since some time in the past", "Before the present"});
        relativeToComboBox.setToolTipText("Whether dates go forward or backward");
        if (traitSet.traitNameInput.get().equals(TraitSet.DATE_BACKWARD_TRAIT)) {
            relativeToComboBox.setSelectedIndex(1);
        } else {
            relativeToComboBox.setSelectedIndex(0);
        }
        relativeToComboBox.addActionListener(e -> {
                String selected = TraitSet.DATE_BACKWARD_TRAIT;
                if (relativeToComboBox.getSelectedIndex() == 0) {
                    selected = TraitSet.DATE_FORWARD_TRAIT;
                }
                try {
                    traitSet.traitNameInput.setValue(selected, traitSet);
                    Log.warning.println("Relative position is now: " + traitSet.traitNameInput.get());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                convertTraitToTableData();
            });
        relativeToComboBox.setMaximumSize(relativeToComboBox.getPreferredSize());
        relativeToComboBox.setEnabled(numericRadioButton.isSelected());
        formatBoxFirstLine.add(relativeToComboBox);
        formatBoxFirstLine.add(Box.createHorizontalGlue());
        formatBox.add(formatBoxFirstLine);

        formattedDateRadioButton = new JRadioButton("as dates with format");
        formattedDateRadioButton.setToolTipText("Interpret values as dates with the given format.");

        if (traitSet.dateTimeFormatInput.get() != null)
            formattedDateRadioButton.setSelected(true);

        formattedDateRadioButton.addActionListener(e -> {
            traitSet.dateTimeFormatInput.setValue(dateFormatComboBox.getSelectedItem(), traitSet);
            refreshPanel();
        });
        radioButtonGroup.add(formattedDateRadioButton);
        formatBoxSecondLine.add(formattedDateRadioButton);

        String[] dateFormatExamples = {
                "dd/M/yyyy",
                "M/dd/yyyy",
                "yyyy/M/dd",
                "dd-M-yyyy",
                "M-dd-yyyy",
                "yyyy-M-dd"};

        dateFormatComboBox = new JComboBox<>(dateFormatExamples);
        dateFormatComboBox.setToolTipText("Set format used to parse date values");
        dateFormatComboBox.setEditable(true);
        if (traitSet.dateTimeFormatInput.get() != null)
            dateFormatComboBox.setSelectedItem(traitSet.dateTimeFormatInput.get());
        else
            dateFormatComboBox.setSelectedItem(dateFormatExamples[0]);
        dateFormatComboBox.setMaximumSize(dateFormatComboBox.getPreferredSize());
        dateFormatComboBox.setEnabled(formattedDateRadioButton.isSelected());
        dateFormatComboBox.addActionListener(e -> {
            traitSet.dateTimeFormatInput.setValue(dateFormatComboBox.getSelectedItem(), traitSet);
            refreshPanel();
        });
        formatBoxSecondLine.add(dateFormatComboBox);

        JButton dateFormatHelpButton = new JButton("?");
        dateFormatHelpButton.addActionListener(e ->
                WrappedOptionPane.showWrappedMessageDialog(this,
                        DATE_FORMAT_HELP_MESSAGE, Font.MONOSPACED));
        formatBoxSecondLine.add(dateFormatHelpButton);

        formatBoxSecondLine.add(Box.createHorizontalGlue());

        formatBox.add(formatBoxSecondLine);
        formatBox.setAlignmentY(TOP_ALIGNMENT);
        buttonBox.add(formatBox);

        buttonBox.add(Box.createHorizontalGlue());

        JButton guessButton = new JButton("Auto-configure");
        guessButton.setAlignmentY(TOP_ALIGNMENT);
        guessButton.setToolTipText("Automatically configure dates based on taxon names");
        guessButton.setName("Guess");
        guessButton.addActionListener(e -> {
                GuessPatternDialog dlg = new GuessPatternDialog(null, m_sPattern);
                dlg.allowAddingValues();
                StringBuilder traitBuilder = new StringBuilder();
                switch (dlg.showDialog("Guess dates")) {
                    case canceled:
                        return;

                    case trait:
                        Map<String,String> traitMap = dlg.getTraitMap();
                        for (String taxon : taxa) {
                            if (!traitMap.containsKey(taxon))
                                continue;

                            if (traitBuilder.length()>0) {
                                traitBuilder.append(",");
                            }

                            traitBuilder.append(taxon).append("=")
                                    .append(traitMap.get(taxon));
                        }
                        break;

                    case pattern:
                        for (String taxon : taxa) {
                            String match = dlg.match(taxon);
                            if (match == null || match.isEmpty()) {
                                return;
                            }
                            if (traitBuilder.length() > 0) {
                                traitBuilder.append(",");
                            }
                            traitBuilder.append(taxon).append("=").append(match);
                        }
                        break;
                }
                try {
                    traitSet.traitsInput.setValue(traitBuilder.toString(), traitSet);
                } catch (Exception ex) {
                    // TODO: handle exception
                }
                refreshPanel();
            });
        buttonBox.add(guessButton);


        JButton clearButton = new JButton("Clear");
        clearButton.setAlignmentY(TOP_ALIGNMENT);
        clearButton.setToolTipText("Set all dates to zero");
        clearButton.addActionListener(e -> {
                try {
                    traitSet.traitsInput.setValue("", traitSet);
                } catch (Exception ex) {
                    // TODO: handle exception
                }
                refreshPanel();
            });
        buttonBox.add(clearButton);

        return buttonBox;
    } // createButtonBox

    public static void main(String[] args) {

        WrappedOptionPane.showWrappedMessageDialog(DATE_FORMAT_HELP_MESSAGE);
    }
}
