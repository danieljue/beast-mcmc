package dr.inference.model;

/*
@author Max Tolkoff
*/

//Designed to return a data matrix post computation if asked. Designed for latent liabilities
public class MatrixMatrixProduct extends MatrixParameter implements VariableListener {
    MatrixParameter left;
    MatrixParameter right;
    private final int leftDim;
    private final int rightDim;
    private final int midDim;
    double[][] storedValues;
    boolean[][] areValuesStored;
    private Bounds bounds=null;

    public MatrixMatrixProduct(MatrixParameter[] params) {
        super(null, params);

        this.left=params[0];
        this.right=params[1];
        storedValues=new double[left.getRowDimension()][right.getColumnDimension()];
        areValuesStored=new boolean[left.getRowDimension()][right.getColumnDimension()];

        for (int i = 0; i <left.getRowDimension() ; i++) {
            for (int j = 0; j <right.getColumnDimension() ; j++) {
                areValuesStored[i][j]=false;
            }

        }
        leftDim=left.getRowDimension();
        midDim=left.getColumnDimension();
        rightDim=right.getColumnDimension();
    }


    public void variableChangedEvent(Variable variable, int index, ChangeType type) {
//        if(variable==right)
//            for (int i = 0; i <getRowDimension() ; i++) {
//                areValuesStored[i][index%rightDim]=false;
//            }
//        if(variable==left)
//            for (int i = 0; i <getColumnDimension() ; i++) {
//                areValuesStored[(index)/midDim][i]=false;
//            }
        fireParameterChangedEvent(index, type);
    }

    @Override
    public int getDimension(){
        return leftDim*rightDim;

    }

    public void addBounds(Bounds bounds) {
        this.bounds = bounds;
    }

    protected void storeValues() {
        left.storeParameterValues();
        right.storeParameterValues();
    }

    protected void restoreValues() {
        left.restoreParameterValues();
        right.restoreVariableValues();
    }

//    protected void acceptValues() {
//        left.acceptParameterValues();
//        right.acceptParameterValues();
//    }


    public double getParameterValue(int i, int j) {
        double sum = 0;
        if (!areValuesStored[i][j]) {
            for (int k = 0; k < midDim; k++) {
                {
                    sum += left.getParameterValue(i, k) * right.getParameterValue(k, j);
                }

            }
        storedValues[i][j] = sum;
//        areValuesStored[i][j]=true;
        }
        else{
            sum=storedValues[i][j];
        }
        return sum;
    }

    @Override
    public double[][] getParameterAsMatrix() {
        return super.getParameterAsMatrix();
    }

    public Parameter getParameter(int PID) {
        double[] answer=new double[left.getRowDimension()];
        for (int i = 0; i <rightDim ; i++) {
            answer[i]=getParameterValue(i, PID);
        }
        return new Parameter.Default(answer);
    }

    public int getRowDimension(){
        return leftDim;
    }

    public int getColumnDimension(){
        return rightDim;
    }

    @Override
    public double getParameterValue(int dim) {
        return getParameterValue(dim/getRowDimension(),dim%rightDim);
    }

    private void throwError(String functionName) throws RuntimeException {
        throw new RuntimeException("Object " + getId() + " is a deterministic function. Calling "
                + functionName + " is not allowed");
    }

    public void setParameterValue(int dim, double value) {
        throwError("setParameterValue()");
    }

    public void setParameterValueQuietly(int dim, double value) {
        throwError("setParameterValueQuietly()");
    }

    @Override

    public void setParameterValueNotifyChangedAll(int dim, double value) {
        throwError("setParameterValueNotifyChangedAll()");
    }


//    @Override
//    public String getParameterName() {
//        if (getId() == null) {
//            StringBuilder sb = new StringBuilder("product");
//            sb.append(".").append(left.getId());
//            sb.append(".").append(right.getId());
//            setId(sb.toString());
//        }
//        return getId();
//    }

    @Override
    public Bounds<Double> getBounds() {
        return bounds;
    }

    public void addDimension(int index, double value) {
        throw new RuntimeException("Not yet implemented.");
    }

    public double removeDimension(int index) {
        throw new RuntimeException("Not yet implemented.");
    }
};

