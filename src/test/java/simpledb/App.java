package simpledb;

import static java.sql.Types.INTEGER;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import simpledb.buffer.Buffer;
import simpledb.buffer.BufferAbortException;
import simpledb.buffer.BufferMgr;
import simpledb.file.BlockId;
import simpledb.file.FileMgr;
import simpledb.file.Page;
import simpledb.index.Index;
import simpledb.log.LogMgr;
import simpledb.materialize.AggregationFn;
import simpledb.materialize.CountFn;
import simpledb.materialize.GroupByPlan;
import simpledb.materialize.MaterializePlan;
import simpledb.materialize.MaxFn;
import simpledb.materialize.MergeJoinPlan;
import simpledb.materialize.SortPlan;
import simpledb.metadata.IndexInfo;
import simpledb.metadata.MetadataMgr;
import simpledb.metadata.TableMgr;
import simpledb.plan.Plan;
import simpledb.plan.ProductPlan;
import simpledb.plan.ProjectPlan;
import simpledb.plan.SelectPlan;
import simpledb.plan.TablePlan;
import simpledb.query.Constant;
import simpledb.query.Expression;
import simpledb.query.Predicate;
import simpledb.query.ProductScan;
import simpledb.query.ProjectScan;
import simpledb.query.Scan;
import simpledb.query.SelectScan;
import simpledb.query.Term;
import simpledb.query.UpdateScan;
import simpledb.record.Layout;
import simpledb.record.RID;
import simpledb.record.RecordPage;
import simpledb.record.Schema;
import simpledb.record.TableScan;
import simpledb.tx.Transaction;

public class App {

    public static void main(String[] args) {

        // 3. FileMgr
        File dbDirectory = new File("datadir");
        FileMgr fm = new FileMgr(dbDirectory, 400);
        String filename = "test.txt";
        // Init BlockId
        BlockId blk = new BlockId(filename, fm.blocksize());

        String msg = "test";
        int pos = 0;

        // Page -> File
        Page page1 = new Page(fm.blocksize());
        page1.setString(pos, msg);
        fm.write(blk, page1);

        // File -> Page
        Page page2 = new Page(fm.blocksize());
        fm.read(blk, page2);
        System.out.println("read message: " + page2.getString(pos));

        // 4.1. LogMgr
        System.out.println("4.1. LogMgr --------------------------");
        String logfile = "simpledb.log";
        new File(dbDirectory, logfile).delete(); // if we don't delete it, the program will fail when reading the contents
        LogMgr lm = new LogMgr(fm, logfile);
        printLogRecords(lm, "The initial empty log file:"); // print an empty log file
        System.out.println("done");
        createRecords(lm, 1, 35);
        printLogRecords(lm, "The log file now has these records:");
        createRecords(lm, 36, 70);
        lm.flush(65);
        printLogRecords(lm, "The log file now has these records:");

        // 4.2. BufferMgr
        System.out.println("4.2. BufferMgr --------------------------");
        BufferMgr bm = new BufferMgr(fm, lm, 3);
        Buffer[] buff = new Buffer[6];
        buff[0] = bm.pin(new BlockId("testfile", 0));
        buff[1] = bm.pin(new BlockId("testfile", 1));
        buff[2] = bm.pin(new BlockId("testfile", 2));
        bm.unpin(buff[1]);
        buff[1] = null;
        buff[3] = bm.pin(new BlockId("testfile", 0)); // block 0 pinned twice
        buff[4] = bm.pin(new BlockId("testfile", 1)); // block 1 repinned
        System.out.println("Available buffers: " + bm.available());
        try {
            System.out.println("Attempting to pin block3...");
            buff[5] = bm.pin(new BlockId("testfile", 3)); // will not work; no buffer available
        } catch (BufferAbortException e) {
            System.out.println("Exception: No available buffers");
        }
        bm.unpin(buff[2]);
        buff[2] = null;
        buff[5] = bm.pin(new BlockId("testfile", 3)); // works as there's available buffer
        System.out.println("Final Buffer Allocation:");
        for (int i = 0; i < buff.length; i++) {
            Buffer b = buff[i];
            if (b != null)
                System.out.println("buff[" + i + "] pinned to block " + b.block());
        }
        // unpin all the pinned buffer
        for (int i = 0; i < buff.length; i++) {
            Buffer b = buff[i];
            if (b != null) {
                bm.unpin(b);
                System.out.println("buff[" + i + "] unpinned from block " + b.block());
            }
        }

        // 5. Concurrency Management
        System.out.println("4. Concurrency Management --------------------------");
        BlockId blk0 = new BlockId("testfile", 0);
        BlockId blk1 = new BlockId("testfile", 1);
        // init
        Transaction tx1 = new Transaction(fm, lm, bm);
        Transaction tx2 = new Transaction(fm, lm, bm);
        tx1.pin(blk0);
        tx2.pin(blk1);
        pos = 0;
        for (int i = 0; i < 6; i++) {
            tx1.setInt(blk0, pos, pos, false); // get xlock through concurMgr
            tx2.setInt(blk1, pos, pos, false); // xlock
            pos += Integer.BYTES;
        }
        tx1.setString(blk0, 30, "abc", false); // xlock
        tx2.setString(blk1, 30, "def", false); // xlock
        tx1.commit();
        tx2.commit();
        printValues(fm, "After initialization:", blk0, blk1);

        // modify
        Transaction tx3 = new Transaction(fm, lm, bm);
        Transaction tx4 = new Transaction(fm, lm, bm);
        tx3.pin(blk0);
        tx4.pin(blk1);
        pos = 0;
        for (int i = 0; i < 6; i++) {
            tx3.setInt(blk0, pos, pos + 100, true);
            tx4.setInt(blk1, pos, pos + 100, true);
            pos += Integer.BYTES;
        }
        System.out.println("setInt is done. now start setString");
        tx3.setString(blk0, 30, "uvw", true);
        tx4.setString(blk1, 30, "xyz", true);
        bm.flushAll(3);
        bm.flushAll(4);
        printValues(fm, "After modifications:", blk0, blk1);
        tx3.rollback();
        printValues(fm, "After rollback", blk0, blk1);
        // tx4 stops here without commiting or rolling back,
        // so all its changes should be undone during recovery.

        // // TODO: recovery as it needs to be executed at startup
        // You cannot just run this because tx4 has lock on blk1
        // but only tx4.ConcurMgr can release it by either tx4.commit() or
        // tx4.rollback()
        // Transaction tx5 = new Transaction(fm, lm, bm);
        // tx5.recover();
        // printValues(fm, "After recovery", blk0, blk1);

        // 6. Record Management
        System.out.println("6. Record Management --------------------------");
        System.out.println("6.1. RecordPage -----------------------");
        Transaction tx = new Transaction(fm, lm, bm);
        Schema sch = new Schema();
        sch.addIntField("A");
        sch.addStringField("B", 9);
        Layout layout = new Layout(sch);
        for (String fldname : layout.schema().fields()) {
            int offset = layout.offset(fldname);
            System.out.println(fldname + " has offset " + offset);
        }
        BlockId blk2 = new BlockId("testfile", 2);
        tx.pin(blk2);
        RecordPage rp = new RecordPage(tx, blk2, layout);
        rp.format(); // fill with zero-value

        System.out.println("Filling the page with random records.");
        int slot = rp.nextAfter(-1); // get the first available slot
        while (slot >= 0) { // nextEmptySlot will return if it reaches the end of the block
            int n = (int) Math.round(Math.random() * 50);
            rp.setInt(slot, "A", n);
            rp.setString(slot, "B", "rec" + n);
            System.out.println("inserting into slot " + slot + ": {" + n + ", " + "rec" + n + "}");
            slot = rp.nextAfter(slot); // get the next available slot
        }

        System.out.println("Deleting these records, whose A-values are less than 25");
        int count = 0;
        slot = rp.nextAfter(-1); // get the first used slot
        while (slot >= 0) {
            int a = rp.getInt(slot, "A");
            String b = rp.getString(slot, "B");
            if (a < 25) {
                count++;
                System.out.println("Deleting slot " + slot + ": {" + a + ", " + b + "}");
                rp.delete(slot);
            }
            slot = rp.nextAfter(slot);
        }
        System.out.println(count + " values under 25 were deleted.");

        System.out.println("Here are the remaining records.");
        slot = rp.nextAfter(-1); // first used slot
        while (slot >= 0) {
            int a = rp.getInt(slot, "A");
            String b = rp.getString(slot, "B");
            System.out.println("slot " + slot + ": {" + a + ", " + b + "}");
            slot = rp.nextAfter(slot);
        }
        tx.unpin(blk2);
        tx.commit();

        System.out.println("6.2. TableScan -----------------------");
        tx = new Transaction(fm, lm, bm);
        System.out.println("Filling the table with 50 random records with TableScan");
        TableScan ts = new TableScan(tx, "T", layout);
        for (int i = 0; i < 50; i++) {
            ts.insert();
            int n = (int) Math.round(Math.random() * 50);
            ts.setInt("A", n);
            ts.setString("B", "rec" + n);
            System.out.println("inserting into slot " + ts.getRid() + ": {" + n + ", " + "rec" + n + "}");
        }

        System.out.println("Deleting these records, whose A-values are les than 25.");
        count = 0;
        ts.beforeFirst();
        while (ts.next()) {
            int a = ts.getInt("A");
            String b = ts.getString("B");
            if (a < 25) {
                count++;
                System.out.println("Deleting slot " + ts.getRid() + ": {" + a + ", " + b + "}");
                ts.delete();
            }
        }
        System.out.println(count + " values under 25 were deleted");

        System.out.println("Here are the remaining records:");
        ts.beforeFirst();
        while (ts.next()) {
            int a = ts.getInt("A");
            String b = ts.getString("B");
            System.out.println("slot " + ts.getRid() + ": {" + a + ", " + b + "}");
        }
        ts.close();
        tx.commit();

        // 7. Metadata Management
        System.out.println("7.1. TableMgr ------------------");
        bm = new BufferMgr(fm, lm, 8); // numbuffs: 3 is not enough
        tx = new Transaction(fm, lm, bm);
        TableMgr tm = new TableMgr(true, tx);
        sch = new Schema();
        sch.addIntField("A");
        sch.addStringField("B", 9);
        tm.createTable("MyTable", sch, tx);

        layout = tm.getLayout("MyTable", tx);
        int size = layout.slotSize();
        Schema sch2 = layout.schema();
        System.out.println("MyTable has slot size" + size);
        System.out.println("Its fields are:");
        for (String fldname : sch2.fields()) {
            String type;
            if (sch2.type(fldname) == INTEGER)
                type = "int";
            else {
                int strlen = sch2.length(fldname);
                type = "varchar(" + strlen + ")";
            }

            System.out.println(fldname + ": " + type);
        }
        tx.commit();

        System.out.println("7.5. MetadataMgr ----------------");
        tx = new Transaction(fm, lm, bm);
        MetadataMgr metadataMgr = new MetadataMgr(true, tx);
        sch = new Schema();
        sch.addStringField("name", 50);
        sch.addIntField("count");

        // Create Table
        metadataMgr.createTable("test_table", sch, tx);

        layout = metadataMgr.getLayout("test_table", tx); // read via TableScan (from the file)
        System.out.println("layout.schema.fields.size (expected: 2): " + layout.schema().fields().size());
        System.out.println("layout.offset for name (expected: 4): " + layout.offset("name"));
        System.out.println("layout.offset for name (expected: 54): " + layout.offset("count"));

        metadataMgr.createView("test_view", "view def", tx);
        String viewdef = metadataMgr.getViewDef("test_view", tx); // read via TableScan (from the file)
        System.out.println("view def: " + viewdef);
        tx.commit();

        // 8. Query Processing
        System.out.println("8.1. SelectScan -------------");
        tx = new Transaction(fm, lm, bm);
        // Schema for T1
        Schema sch1 = new Schema();
        sch1.addIntField("A");
        sch1.addStringField("B", 9);
        Layout layout1 = new Layout(sch1);

        // UpdateScan: insert random data to table T1
        UpdateScan s1 = new TableScan(tx, "T1", layout1);
        s1.beforeFirst();
        int n = 10;
        System.out.println("Inserting " + n + " random records into T1.");
        for (int i = 0; i < n; i++) {
            s1.insert();
            int k = (int) Math.round(Math.random() * 50);
            s1.setInt("A", k);
            s1.setString("B", "rec" + k);
        }
        s1.close();

        // TableScan of T1
        Scan s2 = new TableScan(tx, "T1", layout1);

        // SelectScan
        Constant c = new Constant(10);
        Term t = new Term(new Expression("A"), new Expression(c)); // where A = 10
        Predicate pred = new Predicate(t);
        System.out.println("The predicate is " + pred);
        Scan s3 = new SelectScan(s2, pred);

        while (s3.next())
            System.out.println("A: " + s3.getInt("A") + ", B: " + s3.getString("B"));

        System.out.println("8.2. ProjectScan");
        // ProjectScan
        List<String> fields = Arrays.asList("B");
        Scan s4 = new ProjectScan(s3, fields);
        while (s4.next())
            System.out.println(s4.getString("B"));

        s4.close();
        tx.commit();

        System.out.println("8.3. ProjectScan -------------");
        tx = new Transaction(fm, lm, bm);
        // Schema for T2
        sch2 = new Schema();
        sch2.addIntField("C");
        sch2.addStringField("D", 9);
        Layout layout2 = new Layout(sch2);

        // UpdateScan: insert random data to table T1
        ts = new TableScan(tx, "T2", layout2);
        ts.beforeFirst();
        System.out.println("Inserting " + n + " random records into T2.");
        for (int i = 0; i < n; i++) {
            ts.insert();
            ts.setInt("C", n - i - 1);
            ts.setString("D", "rec" + (n - i - 1));
        }
        ts.close();

        Scan ts1 = new TableScan(tx, "T1", layout1);
        Scan ts2 = new TableScan(tx, "T2", layout2);
        Scan ps = new ProductScan(ts1, ts2);
        ps.beforeFirst();
        System.out.println("prepare scans");
        while (ps.next())
            System.out.println("B: " + ps.getString("B") + ", D: " + ps.getString("D"));
        ps.close(); // call sub scan's close() recurvely and eventually release the target block of
        // underlying tablescan by tx.unpin(rp.block())
        tx.commit(); // release all locks on blocks

        // 10. Planning
        System.out.println("10.1.1. TablePlan-------------");
        metadataMgr.createTable("T1", sch1, tx); // tabcat doesn't have a record for T1 created above
        Plan p1 = new TablePlan(tx, "T1", metadataMgr);

        System.out.println("R(p1): " + p1.recordsOutput());
        System.out.println("B(p1): " + p1.blockAccessed());
        for (String fldname : p1.schema().fields())
            System.out.println("V(p1, " + fldname + "): " + p1.distinctValues(fldname));

        // Select node
        System.out.println("10.1.2. SelectPlan-------------");
        t = new Term(new Expression("A"), new Expression(new Constant(5)));
        pred = new Predicate(t);
        Plan p2 = new SelectPlan(p1, pred);
        System.out.println("R(p2): " + p2.recordsOutput());
        System.out.println("B(p2): " + p2.blockAccessed());
        for (String fldname : p2.schema().fields())
            System.out.println("V(p2, " + fldname + "): " + p2.distinctValues(fldname));

        // Project node
        System.out.println("10.1.3. ProjectPlan-------------");
        ProjectPlan p3 = new ProjectPlan(p2, fields);
        System.out.println("R(p3): " + p3.recordsOutput());
        System.out.println("B(p3): " + p3.blockAccessed());
        for (String fldname : p3.schema().fields())
            System.out.println("V(p2, " + fldname + "): " + p3.distinctValues(fldname));

        Scan s = p3.open();
        while (s.next())
            System.out.println(s.getString("B"));
        s.close();

        // Product node
        System.out.println("10.1.4. ProductPlan-------------");
        metadataMgr.createTable("T2", sch2, tx); // tabcat doesn't have a record for T2 created above
        Plan p4 = new TablePlan(tx, "T2", metadataMgr);
        Plan p5 = new ProductPlan(p1, p4);
        Plan p6 = new SelectPlan(p5, pred);
        System.out.println("R(p6): " + p6.recordsOutput());
        System.out.println("B(p6): " + p6.blockAccessed());
        for (String fldname : p6.schema().fields())
            System.out.println("V(p6, " + fldname + "): " + p6.distinctValues(fldname));

        s = p6.open();
        s.beforeFirst(); // this is necessary for p1 to move to the first position
        while (s.next())
            System.out.println(
                    "A: " + s.getInt("A") + ", B: " + s.getString("B") + ", C: " + s.getInt("C") + ", D: " + s.getString("D"));
        s.close();
        tx.commit();

        // 12 Indexing
        System.out.println("12. Indexing-------------");
        tx = new Transaction(fm, lm, bm);
        metadataMgr = new MetadataMgr(false, tx);
        sch = new Schema();
        sch.addStringField("fld1", 10);
        sch.addIntField("fld2");
        metadataMgr.createTable("T3", sch, tx);
        metadataMgr.createIndex("T3_fld1_idx", "T3", "fld1", tx);

        Plan plan = new TablePlan(tx, "T3", metadataMgr);
        Map<String, IndexInfo> indexes = metadataMgr.getIndexInfo("T3", tx);
        IndexInfo ii = indexes.get("fld1");
        Index idx = ii.open();

        // insert 2 records into T3
        UpdateScan us = (UpdateScan) plan.open();
        us.beforeFirst();
        n = 2;
        System.out.println("Inserting " + n + " records into T3.");
        for (int i = 0; i < n; i++) {
            System.out.println("Inserting " + i + " into T3.");
            us.insert();
            us.setString("fld1", "rec" + i % 2);
            us.setInt("fld2", i % 2);
            // insert index record
            Constant dataval = us.getVal("fld1");
            RID datarid = us.getRid();
            System.out.println("insert index " + dataval + " " + datarid);
            idx.insert(dataval, datarid);
        }

        // Get records without index
        us.beforeFirst();
        while (us.next()) {
            System.out.println("Got data from T3 without index. RID:" + us.getRid() + ", fld1: " + us.getString("fld1"));
        }
        us.close();
        tx.commit(); // need to flush index to disk

        // Get records where fld1 = "rec0" with index
        us = (UpdateScan) plan.open();
        idx = ii.open();
        System.out.println("Get records fld1=rec0 using index ------------------------------------------");
        idx.beforeFirst(new Constant("rec0"));
        while (idx.next()) {
            RID datarid = idx.getDataRid();
            us.moveToRid(datarid);
            System.out.printf("Got data from T3 with index (rec0). RID: %s, fld1: %s\n", us.getRid().toString(), us.getString("fld1"));
        }
        System.out.println("Get records fld1=rec1 using index ------------------------------------------");
        idx.beforeFirst(new Constant("rec1"));
        while (idx.next()) {
            RID datarid = idx.getDataRid();
            us.moveToRid(datarid);
            System.out.printf("Got data from T3 with index (rec1). RID: %s, fld1: %s\n", us.getRid().toString(), us.getString("fld1"));
        }

        idx.close();
        tx.commit();

        // 13. Materialization and Sorting
        System.out.println("13. Materialization and Sorting -------------");
        System.out.println("13.1. Materialization --------");
        tx = new Transaction(fm, lm, bm);
        plan = new TablePlan(tx, "T3", metadataMgr); // metadataMgr created above
        plan = new MaterializePlan(tx, plan);
        Scan scan = plan.open();
        while (scan.next())
            System.out.println("get record from TempTable: " + scan.getVal("fld1"));

        scan.close();
        tx.commit();

        System.out.println("13.2. Sorting --------------------");
        tx = new Transaction(fm, lm, bm);
        plan = new TablePlan(tx, "T1", metadataMgr);
        plan = new SortPlan(tx, plan, Arrays.asList("A"));
        scan = plan.open();
        while (scan.next()) {
            System.out.println("get record from sorted TempTable: " + scan.getVal("A"));
        }

        scan.close();
        tx.commit();

        System.out.println("13.3. GroupBy and Aggregation --------------------");
        tx = new Transaction(fm, lm, bm);
        plan = new TablePlan(tx, "T3", metadataMgr);
        AggregationFn countfn = new CountFn("fld2");
        AggregationFn maxfn = new MaxFn("fld2");
        plan = new GroupByPlan(tx, plan, Arrays.asList("fld1"), Arrays.asList(countfn, maxfn));
        scan = plan.open();
        while (scan.next())
            System.out.println("aggregation result: groupby: " + scan.getVal("fld1") + ", count: "
                            + scan.getVal(countfn.fieldName()) + ", max: " + scan.getVal(maxfn.fieldName()));
        scan.close();
        tx.commit();

        System.out.println("13.4. MergeJoin --------------------");
        bm = new BufferMgr(fm, lm, 16); // buffer 8 is not enough
        tx = new Transaction(fm, lm, bm);
        p1 = new TablePlan(tx, "T1", metadataMgr); // T1 A:int, B:String
        p2 = new TablePlan(tx, "T2", metadataMgr); // T3 fld1:String, fld2:int
        plan = new MergeJoinPlan(tx, p1, p2, "A", "C"); // JOIN ON T1.A = T3.fld2
        scan = plan.open();
        scan.beforeFirst();
        while (scan.next()) {
            System.out.print("merged result:");
            for (String fldname : p1.schema().fields())
                System.out.print(" T1." + fldname + ": " + scan.getVal(fldname) + ",");
            for (String fldname : p2.schema().fields())
                System.out.print(" T2." + fldname + ": " + scan.getVal(fldname) + ",");
            System.out.println();
        }
        scan.close();
        tx.commit();

        // Exercise 13.8. Sort empty table
        System.out.println("13.8. Sort empty table --------------------");
        tx = new Transaction(fm, lm, bm);
        sch = new Schema();
        sch.addIntField("intfld");
        layout = new Layout(sch);
        metadataMgr.createTable("emptytable", sch, tx);
        plan = new TablePlan(tx, "emptytable", metadataMgr);
        plan = new SortPlan(tx, plan, Arrays.asList("intfld"));
        scan = plan.open();
        while (scan.next())
            System.out.println(scan.getInt("intfld"));
        scan.close();
        tx.commit();
    }

    private static void printLogRecords(LogMgr lm, String msg) {
        System.out.println(msg);
        Iterator<byte[]> iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] rec = iter.next();
            Page p = new Page(rec);
            String s = p.getString(0);
            int npos = Page.maxLength(s.length());
            int val = p.getInt(npos);
            System.out.println("[" + s + ", " + val + "]");
        }
        System.out.println();
    }

    private static void createRecords(LogMgr lm, int start, int end) {
        System.out.print("Creating records: ");
        for (int i = start; i <= end; i++) {
            byte[] rec = createLogRecord("record" + i, i + 100);
            int lsn = lm.append(rec);
            System.out.print(lsn + " ");
        }
        System.out.println();
    }

    // Create a log record having two values: a string and an integer.
    private static byte[] createLogRecord(String s, int n) {
        int spos = 0;
        int npos = spos + Page.maxLength(s.length());
        byte[] b = new byte[npos + Integer.BYTES];
        Page p = new Page(b);
        p.setString(spos, s);
        p.setInt(npos, n);
        return b;
    }

    private static void printValues(FileMgr fm, String msg, BlockId blk0, BlockId blk1) {
        System.out.println(msg);
        Page p0 = new Page(fm.blocksize());
        Page p1 = new Page(fm.blocksize());
        fm.read(blk0, p0);
        fm.read(blk1, p1);
        int pos = 0;
        for (int i = 0; i < 6; i++) {
            System.out.print(p0.getInt(pos) + " ");
            System.out.print(p1.getInt(pos) + " ");
            pos += Integer.BYTES;
        }
        System.out.print(p0.getString(30) + " ");
        System.out.print(p1.getString(30) + " ");
        System.out.println();
    }

}
