package dr.exporters;

import dr.util.TabularData;
import dr.xml.*;
import dr.inference.loggers.MCLogger;
import dr.inference.model.Parameter;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Joseph Heled
 *
 * Very lame so far. need checks for possible qouting of elements.
 */
public class CSVExporter extends AbstractXMLObjectParser {


    public String getParserName() {
         return "CSVexport";
     }

     public String getParserDescription() {
         return "Write tabular data as an CSV file.";
     }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {
        String fileName = xo.getStringAttribute(FILE_NAME);

        File csv = MCLogger.getFile(fileName);

        String sep = "\t";
        if( xo.hasAttribute(SEPARATOR) ) {
            sep = xo.getStringAttribute(SEPARATOR);
        }

        try {
            final PrintWriter writer = new PrintWriter(new FileWriter(csv));
            for(int k = 0; k < xo.getChildCount(); ++k) {
                final XMLObject columns = (XMLObject) xo.getChild(k);
                if( columns.getName().equals(COLUMNS) ) {
                    TabularData source = (TabularData)columns.getChild(TabularData.class);
                    // look for columns
                    List<Integer> iColumns = new ArrayList<Integer>() ;
                    List<Parameter[]> adds = new ArrayList<Parameter[]>();
                    int maxAdds = 0;

                    for(int nc = 0; nc < columns.getChildCount(); ++nc) {
                        final Object child = columns.getChild(nc);
                        if( child instanceof XMLObject ) {
                            final XMLObject column = (XMLObject) child;
                            if( column.getName().equals(COLUMN) ) {
                                final String name = column.getStringAttribute(COLUMN_NAME);
                                final int n = source.getColumn(name);
                                if( n < 0 ) {
                                    throw new XMLParseException("unsupported column name" + name);
                                }
                                iColumns.add(n);


                                final int nAddtional = column.getChildCount();
                                Parameter[] additionals = nAddtional > 0 ? new Parameter[nAddtional] : null;
                                for(int nc1 = 0; nc1 < nAddtional; ++nc1) {
                                     additionals[nc1] = (Parameter) column.getChild(nc1);
                                }
                                maxAdds = Math.max(maxAdds, nAddtional);

                                adds.add(additionals);
                            }
                        }
                    }

                    final boolean hasAllAttribute = columns.hasAttribute(ALL_COLUMNS);


                    if( iColumns.size() == 0 ) {
                        // when no columns specified - default is all
                        if (!hasAllAttribute || columns.getBooleanAttribute(ALL_COLUMNS) )  {
                            for(int nc = 0; nc < source.nColumns(); ++nc) {
                                iColumns.add(nc);
                                adds.add(null);
                            }
                        }
                    } else {
                        // some column specification
                        if( hasAllAttribute && columns.getBooleanAttribute(ALL_COLUMNS) ) {
                            for(int nc = 0; nc < source.nColumns(); ++nc) {
                                if( ! iColumns.contains(nc) )  {
                                    iColumns.add(nc);
                                    adds.add(null);
                                }
                            }
                        }
                    }

                    for(int nc = 0; nc < iColumns.size(); ++nc) {
                        if( nc > 0 ) {  writer.print(sep); }
                        writer.print(source.columnName(iColumns.get(nc)));
                    }
                    writer.println();

                    for(int nr = 0; nr < source.nRows(); ++nr) {
                        for(int nc = 0; nc < iColumns.size(); ++nc) {
                          if( nc > 0 ) {  writer.print(sep); }
                            final Object value = source.data(nr, iColumns.get(nc));
                            writer.print(value);
                        }
                        writer.println();
                    }

                    for(int nr = 0; nr < maxAdds; ++nr) {
                        for(int nc = 0; nc < iColumns.size(); ++nc) {
                            if( nc > 0 ) {  writer.print(sep); }
                            final Parameter[] addsnc = adds.get(nc);
                            if( addsnc != null && nr < addsnc.length ) {
                              final Object value = addsnc[nr].getParameterValues()[0];
                              writer.print(value);
                            }
                        }
                        writer.println();
                    }
                }
            }
            writer.close();
        } catch (IOException e) {
            throw new XMLParseException(e.getMessage());
        }
        return null;
    }

    public static final String FILE_NAME = "fileName";
    public static final String SEPARATOR = "separator";
    public static final String COLUMNS = "columns";
    public static final String ALL_COLUMNS = "all";
    public static final String COLUMN = "CSVcolumn";
    public static final String COLUMN_NAME = "name";

    public XMLSyntaxRule[] getSyntaxRules() {
        return new XMLSyntaxRule[] {
                new StringAttributeRule(FILE_NAME,
                        "The name of a BEAST log file (can not include trees, which should be logged separately"),
                new StringAttributeRule(SEPARATOR, "Values separator (default is tab)", true),
                new ElementRule(COLUMNS , new XMLSyntaxRule[] {
                        AttributeRule.newBooleanRule(ALL_COLUMNS, true,
                                "Dump all columns. default is TRUE when no columns are specified, FALSE"),
                        new ElementRule(TabularData.class),
                        new ElementRule(COLUMN, new XMLSyntaxRule[]{
                                AttributeRule.newStringArrayRule(COLUMN_NAME),
                                new ElementRule(Parameter.class)
                        } , "column name", 0, Integer.MAX_VALUE)
                } , "A subset of columns from one source", 1, Integer.MAX_VALUE)
        };
    }


    public Class getReturnType() {
        return null;
    }
}