package javax.microedition.rms;
public class RecordStore {
    private RecordStore() {}
    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary)
            throws RecordStoreException, RecordStoreFullException, RecordStoreNotFoundException { return null; }
    public static void deleteRecordStore(String recordStoreName)
            throws RecordStoreException, RecordStoreNotFoundException {}
    public static String[] listRecordStores() { return null; }
    public void closeRecordStore() throws RecordStoreNotOpenException, RecordStoreException {}
    public int addRecord(byte[] data, int offset, int numBytes)
            throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException { return 0; }
    public byte[] getRecord(int recordId)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException { return null; }
    public void setRecord(int recordId, byte[] newData, int offset, int numBytes)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException, RecordStoreFullException {}
    public void deleteRecord(int recordId)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {}
    public int getNextRecordID() throws RecordStoreNotOpenException, RecordStoreException { return 1; }
    public int getNumRecords() throws RecordStoreNotOpenException { return 0; }
    public int getSize() throws RecordStoreNotOpenException { return 0; }
    public int getSizeAvailable() throws RecordStoreNotOpenException { return 0; }
    public String getName() throws RecordStoreNotOpenException { return null; }
}
