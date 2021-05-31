package src;

public class Data {
    private int numberOfRecords, recordLength;

    public Data(int nRec, int recLen) throws IllegalArgumentException {
        if (nRec <= 0 || recLen <= 0) {
            throw new IllegalArgumentException("Cannot have 0 or less records. Each record's size must be greater than 0");
        }

        this.numberOfRecords = nRec;
        this.recordLength = recLen;
    }

    public int[] execute() {
        return new int[]{numberOfRecords, recordLength};
    }
}
