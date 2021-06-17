/*
 * Copyright 2007-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.ion.impl;

import static com.amazon.ion.SymbolTable.UNKNOWN_SYMBOL_ID;
import com.amazon.ion.SymbolToken;
import static com.amazon.ion.SystemSymbols.ION;
import static com.amazon.ion.SystemSymbols.ION_1_0;
import static com.amazon.ion.SystemSymbols.ION_1_0_SID;
import static com.amazon.ion.SystemSymbols.ION_SHARED_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.NAME;
import static com.amazon.ion.SystemSymbols.NAME_SID;
import static com.amazon.ion.SystemSymbols.SYMBOLS;
import static com.amazon.ion.impl.Symtabs.printLocalSymtab;
import static com.amazon.ion.impl._Private_Utils.EMPTY_STRING_ARRAY;
import static com.amazon.ion.impl._Private_Utils.stringIterator;
import static com.amazon.ion.impl._Private_Utils.symtabTree;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonException;
import com.amazon.ion.IonInt;
import com.amazon.ion.IonList;
import com.amazon.ion.IonMutableCatalog;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSexp;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.ReadOnlyValueException;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SystemSymbols;
import com.amazon.ion.Timestamp;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.ion.system.SimpleCatalog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.amazon.ion.util.Equivalence;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @see SharedSymbolTableTest
 */
@SuppressWarnings("deprecation")
public class SymbolTableTest
    extends IonTestCase
{
    public static final String LocalSymbolTablePrefix = ION_SYMBOL_TABLE + "::";
    public static final String SharedSymbolTablePrefix = ION_SHARED_SYMBOL_TABLE + "::";


    public final static int IMPORTED_1_MAX_ID = 2;
    public final static int IMPORTED_2_MAX_ID = 4;
    public final static int IMPORTED_3_MAX_ID = 5;

    public final static String IMPORTED_1_SERIALIZED =
        SharedSymbolTablePrefix +
        "{" +
        "  name:'''imported''', version:1," +
        "  symbols:['''imported 1''', '''imported 2''']" +
        "}";


    public SymbolTable registerImportedV1()
    {
        SymbolTable shared = registerSharedSymtab(IMPORTED_1_SERIALIZED);
        assertEquals(IMPORTED_1_MAX_ID, shared.getMaxId());

        SymbolTable importedTable =
            system().getCatalog().getTable("imported", 1);
        assertSame(shared, importedTable);

        return importedTable;
    }

    public SymbolTable registerImportedV2()
    {
        IonSystem system = system();
        String importingText =
            SharedSymbolTablePrefix +
            "{" +
            "  name:'''imported''', version:2," +
            "  symbols:[" +
            "    '''imported 1'''," +
            "    '''imported 2'''," +
            "    '''fred3'''," +
            "    '''fred4'''," +
            "  ]" +
            "}";
        SymbolTable shared = registerSharedSymtab(importingText);
        assertEquals(IMPORTED_2_MAX_ID, shared.getMaxId());

        SymbolTable importedTable =
            system.getCatalog().getTable("imported", 2);
        assertSame(shared, importedTable);

        return importedTable;
    }

    public SymbolTable registerImportedV3()
    {
        IonSystem system = system();
        String importingText =
            SharedSymbolTablePrefix +
            "{" +
            "  name:'''imported''', version:3," +
            "  symbols:[" +
            "    '''imported 1'''," +
            "    null," +            // Removed 'imported 2'
            "    '''fred3'''," +
            "    '''fred4'''," +
            "    '''fred5'''," +
            "  ]" +
            "}";
        SymbolTable shared = registerSharedSymtab(importingText);
        assertEquals(IMPORTED_3_MAX_ID, shared.getMaxId());

        SymbolTable importedTable =
            system.getCatalog().getTable("imported", 3);
        assertSame(shared, importedTable);

        return importedTable;
    }


    //=========================================================================
    // Test cases

    @Test
    public void testLocalSymbolTableAppend()
    {
        String text =
            LocalSymbolTablePrefix +
                "{" +
                "  symbols:[ \"s1\", \"s2\"]" +
                "}\n" +
            LocalSymbolTablePrefix +
                "{" +
                "  imports:" + ION_SYMBOL_TABLE + "," +
                "  symbols:[ \"s3\", \"s4\", \"s5\"]" +
                "}\n" +
                "null";

        SymbolTable symbolTable = oneValue(text).getSymbolTable();

        checkLocalTable(symbolTable);

        // table contains all symbols and SIDs are in correct order
        checkSymbol("s1", systemMaxId() + 1, symbolTable);
        checkSymbol("s2", systemMaxId() + 2, symbolTable);
        checkSymbol("s3", systemMaxId() + 3, symbolTable);
        checkSymbol("s4", systemMaxId() + 4, symbolTable);
        checkSymbol("s5", systemMaxId() + 5, symbolTable);

        checkUnknownSymbol("unknown", UNKNOWN_SYMBOL_ID, symbolTable);
        checkUnknownSymbol(33, symbolTable);
    }

    @Test
    public void testLocalSymbolTableMultiAppend()
    {
        String text =
            LocalSymbolTablePrefix +
                "{" +
                "  symbols:[ \"s1\", \"s2\"]" +
                "}\n" +
            LocalSymbolTablePrefix +
                "{" +
                "  imports:" + ION_SYMBOL_TABLE + "," +
                "  symbols:[ \"s3\"]" +
                "}\n" +
            LocalSymbolTablePrefix +
                "{" +
                "  imports:" + ION_SYMBOL_TABLE + "," +
                "  symbols:[\"s4\", \"s5\"]" +
                "}\n" +
            LocalSymbolTablePrefix +
                "{" +
                "  imports:" + ION_SYMBOL_TABLE + "," +
                "  symbols:[\"s6\"]" +
                "}\n" +
                "null";

        SymbolTable symbolTable = oneValue(text).getSymbolTable();

        checkLocalTable(symbolTable);

        // table contains all symbols and SIDs are in correct order
        checkSymbol("s1", systemMaxId() + 1, symbolTable);
        checkSymbol("s2", systemMaxId() + 2, symbolTable);
        checkSymbol("s3", systemMaxId() + 3, symbolTable);
        checkSymbol("s4", systemMaxId() + 4, symbolTable);
        checkSymbol("s5", systemMaxId() + 5, symbolTable);
        checkSymbol("s6", systemMaxId() + 6, symbolTable);

        checkUnknownSymbol("unknown", UNKNOWN_SYMBOL_ID, symbolTable);
        checkUnknownSymbol(33, symbolTable);
    }

    @Test
    public void testLocalSymbolTableAppendEmptyList()
    {
        String original =
            LocalSymbolTablePrefix +
                "{" +
                "  symbols:[ \"s1\"]" +
                "}\n";

        String appended = original +
            LocalSymbolTablePrefix +
                "{" +
                "  imports:" + ION_SYMBOL_TABLE + "," +
                "  symbols:[]" +
                "}\n";

        SymbolTable originalSymbolTable = oneValue(original + "null").getSymbolTable();
        SymbolTable appendedSymbolTable = oneValue(appended + "null").getSymbolTable();

        SymbolToken originalSymbol = originalSymbolTable.find("s1");
        SymbolToken appendedSymbol = appendedSymbolTable.find("s1");

        assertEquals(originalSymbol.getSid(), appendedSymbol.getSid());
    }

    @Test
    public void testLocalSymbolTableAppendImportBoundary() throws IOException
    {
        String text =
            LocalSymbolTablePrefix +
                "{" +
                "  symbols:[ \"s11\"]" +
                "}\n" +
                "1\n" +
            LocalSymbolTablePrefix +
                "{" +
                "  imports:" + ION_SYMBOL_TABLE + "," +
                "  symbols:[ \"s21\"]" +
                "}\n" +
                "null";

        IonDatagram datagram = loader().load(text);

        SymbolTable symbolTable = datagram.get(0).getSymbolTable();
        assertSame(symbolTable, datagram.get(1).getSymbolTable());
        symbolTable.intern("o1");
        symbolTable.intern("a1");

        // new symbols don't influence SIDs for existing symbols; they are appended
        checkSymbol("s11", systemMaxId() + 1, symbolTable);
        checkSymbol("s21", systemMaxId() + 2, symbolTable);
        checkSymbol("o1", systemMaxId() + 3, symbolTable);
        checkSymbol("a1", systemMaxId() + 4, symbolTable);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        datagram.writeTo(writer);
        writer.close();
        IonDatagram roundtripped = loader().load(out.toByteArray());
        assertTrue(Equivalence.ionEquals(datagram, roundtripped));
    }

    @Test
    public void testMutateDomAfterLocalSymbolTableAppend() throws IOException {
        String text =
                LocalSymbolTablePrefix +
                "{" +
                "   imports:[{name:\"foo\", version:1, max_id:1}], " +
                "   symbols:[\"s1\", \"s2\"]" +
                "}\n" +
                "$10\n" + // Symbol with unknown text from "foo"
                "$11\n" + // s1
                LocalSymbolTablePrefix +
                "{" +
                "   imports:" + ION_SYMBOL_TABLE + "," +
                "   symbols:[\"s3\"]" +
                "}\n" +
                "$12"; // s2
        IonDatagram datagram = loader().load(text);
        datagram.add(1, system().newSymbol("abc"));
        datagram.add(system().newSymbol(new SymbolTokenImpl(null, 13))); // s3
        datagram.add(system().newSymbol(new SymbolTokenImpl(null, 10))); // Symbol with unknown text from "foo"
        ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream binaryOutput = new ByteArrayOutputStream();
        List<String> fooSymbols = Collections.singletonList("bar");
        SymbolTable fooTable = system().newSharedSymbolTable("foo", 1, fooSymbols.iterator());
        IonWriter textWriter = IonTextWriterBuilder.standard().withImports(fooTable).build(textOutput);
        datagram.writeTo(textWriter);
        textWriter.close();
        IonWriter binaryWriter = IonBinaryWriterBuilder.standard().withImports(fooTable).build(binaryOutput);
        datagram.writeTo(binaryWriter);
        binaryWriter.close();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(fooTable);
        IonSystem system = IonSystemBuilder.standard().withCatalog(catalog).build();
        IonDatagram textRoundtrip = system.getLoader().load(textOutput.toByteArray());
        IonDatagram binaryRoundtrip = system.getLoader().load(binaryOutput.toByteArray());
        assertEquals(textRoundtrip, binaryRoundtrip);
        assertEquals("bar", ((IonSymbol)textRoundtrip.get(0)).stringValue());
        assertEquals("abc", ((IonSymbol)textRoundtrip.get(1)).stringValue());
        assertEquals("s1", ((IonSymbol)textRoundtrip.get(2)).stringValue());
        assertEquals("s2", ((IonSymbol)textRoundtrip.get(3)).stringValue());
        assertEquals("s3", ((IonSymbol)textRoundtrip.get(4)).stringValue());
        assertEquals("bar", ((IonSymbol)textRoundtrip.get(0)).stringValue());
    }

    @Test
    public void testSymtabsPrintLocalSymtabWithGaps()
        throws Exception
    {
        String expected =
            "$ion_symbol_table::{" +
            "  symbols:[\"amazon\",\"website\",null,]" + // NB: unquoted null
            "}";

        String actual = printLocalSymtab("amazon", "website", null);

        // remove all whitespaces before check
        assertEquals(expected.replaceAll("\\s+", ""),
                     actual.replaceAll("\\s+", ""));
    }

    @Test
    public void testInitialSystemSymtab()
    {
        final SymbolTable systemTable = system().getSystemSymbolTable(ION_1_0);
        assertEquals(ION, systemTable.getName());

        String text = "0";
        IonValue v = oneValue(text);
        SymbolTable st = v.getSymbolTable();
        assertSame(systemTable, st.getSystemSymbolTable());

        IonDatagram dg = loader().load(text);
        IonSymbol sysId = (IonSymbol) dg.systemGet(0);
        checkSymbol(ION_1_0, ION_1_0_SID, sysId);
        assertSame(systemTable, sysId.getSymbolTable());

        v = dg.get(0);
        st = v.getSymbolTable();
        assertSame(systemTable, st.getSystemSymbolTable());
    }

    public void testSystemFindSymbol()
    {
        SymbolTable st = system().getSystemSymbolTable();
        checkSymbol(NAME, NAME_SID, st);
    }

    @Test(expected = ReadOnlyValueException.class)
    public void testSystemSymtabAddSymbol()
    {
        SymbolTable st = system().getSystemSymbolTable();
        st.intern("hello");
    }


    @Test
    public void testSystemSymtabIsReadOnly()
    {
        SymbolTable st = system().getSystemSymbolTable();
        assertTrue(st.isSharedTable());
        assertTrue("system symtab should be read-only", st.isReadOnly());
    }


    @Test
    public void testLocalTable()
    {
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  symbols:[ \"foo\", \"bar\"]," +
            "}\n" +
            "null";

        SymbolTable symbolTable = oneValue(text).getSymbolTable();
        checkLocalTable(symbolTable);

        checkSymbol("foo", systemMaxId() + 1, symbolTable);
        checkSymbol("bar", systemMaxId() + 2, symbolTable);

        checkUnknownSymbol("not there", UNKNOWN_SYMBOL_ID, symbolTable);
        checkUnknownSymbol(33, symbolTable);
    }


    @Test
    public void testParsedLocalTableMakeReadOnly()
        throws Exception
    {
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  symbols:[ \"foo\", \"bar\"]," +
            "}\n" +
            "null";

        SymbolTable symbolTable = oneValue(text).getSymbolTable();
        symbolTable.intern("baz");
        symbolTable.makeReadOnly();
        symbolTable.intern("baz");

        try {
            symbolTable.intern("boo");
            fail("expected exception");
        }
        catch (ReadOnlyValueException e) { }
    }


    @Test
    public void testImportsFollowSymbols()
    {
        registerImportedV1();

        final int import1id = systemMaxId() + 1;
        final int local1id = systemMaxId() + IMPORTED_1_MAX_ID + 1;
        final int local2id = local1id + 1;

        String importingText =
            "$ion_1_0 "+
            LocalSymbolTablePrefix +
            "{" +
            "  symbols:[ '''local1''' ]," +
            "  imports:[{name:'''imported''', version:1, max_id:2}]," +
            "}\n" +
            "local2\n" +  // This symbol is added to end of locals
            "local1\n" +
            "'imported 1'";

        Iterator<IonValue> scanner = system().iterate(importingText);

        IonValue value = scanner.next();
        SymbolTable symtab = value.getSymbolTable();
        checkLocalTable(symtab);
        checkSymbol("local2", value);

        value = scanner.next();
        checkSymbol("local1", local1id, value);

        value = scanner.next();
        checkSymbol("imported 1", import1id, value);
    }


    /**
     * Attempts to override system symbols are ignored.
     */
    @Test
    public void testOverridingSystemSymbolId()
    {
        String importingText =
            LocalSymbolTablePrefix +
            "{" +
            "  symbols:[ '''" + NAME + "''' ]," +
            "}\n" +
            "null";

        Iterator<IonValue> scanner = system().iterate(importingText);
        IonValue v = scanner.next();
        SymbolTable symtab = v.getSymbolTable();
        assertTrue(symtab.isLocalTable());
        checkSymbol(NAME, NAME_SID, symtab);
    }


    @Test
    public void testOverridingImportedSymbolId()
    {
        SymbolTable importedTable = registerImportedV1();

        final int import1id = systemMaxId() + 1;
        final int import1DupId = systemMaxId() + importedTable.getMaxId() + 1;

        String importingText =
            "$ion_1_0 "+
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:'''imported''', version:1, max_id:2}]," +
            "  symbols:[ '''imported 1''' ]," +
            "}\n" +
            "'imported 1'\n" +
            "$" + import1id;

        Iterator<IonValue> scanner = system().iterate(importingText);

        IonValue value = scanner.next();
        checkSymbol("imported 1", import1id, value);

        SymbolTable symtab = value.getSymbolTable();
        checkLocalTable(symtab);
        checkSymbol("imported 1", import1id, symtab);
        checkSymbol("imported 1", import1DupId, /* dupe */ true, symtab);

        // Here the input text is $NNN  but it comes back correctly.
        value = scanner.next();
        checkSymbol("imported 1", import1id, value);
    }


    @Test
    public void testInjectingMaxIdIntoImport() // TODO implement
    {
        SymbolTable importedTable = registerImportedV1();

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:'''imported''',version:1}],\n" +
            "}\n" +
            "null";
        IonDatagram dg = loader().load(text);

        SymbolTable symbolTable = dg.get(0).getSymbolTable();
        checkLocalTable(symbolTable);

        SymbolTable[] imported = symbolTable.getImportedTables();
        assertEquals(1, imported.length);
        assertSame(importedTable, imported[0]);

        // Check that the encoded table has max_id on import
        byte[] binary = dg.getBytes();
        dg = loader().load(binary);
        IonStruct symtabStruct = (IonStruct) dg.systemGet(1);
        IonList imports = (IonList) symtabStruct.get("imports");
        IonStruct importStruct = (IonStruct) imports.get(0);
        checkString("imported", importStruct.get("name"));

        IonValue maxIdValue = importStruct.get("max_id");
        assertNotNull("max_id wasn't injected into import", maxIdValue);
        checkInt(IMPORTED_1_MAX_ID, maxIdValue);
    }


    @Test
    public void testLocalTableWithMissingImport()
        throws IOException
    {
        // Use a big symtab to get beyond any default allocation within the
        // dummy symtab.  This was done to trap a bug in LocalSymbolTable.
        ArrayList<String> syms = new ArrayList<String>();
        int maxId = 50;
        for (int i = 1; i <= maxId; i++)
        {
            syms.add("S" + i);
        }

        SymbolTable table =
            system().newSharedSymbolTable("T", 1, syms.iterator());

        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        catalog.putTable(table);

        final int import1id = systemMaxId() + 1;
        final int import2id = systemMaxId() + 2;

        final int local1id = systemMaxId() + maxId + 1;
        final int local2id = local1id + 1;


        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  symbols:[ \"local1\", \"local2\" ]," +
            "  imports:[{name:'''T''', version:1," +
            "            max_id:" + maxId + "}]," +
            "}\n" +
            "local1 local2 S1 S2";
        byte[] binary = encode(text);

        // Remove the imported table before decoding the binary.
        assertSame(table, catalog.removeTable("T", 1));

        IonDatagram dg = loader().load(binary);
        checkSymbol("local1", local1id, dg.get(0));
        checkSymbol("local2", local2id, dg.get(1));
        checkUnknownSymbol(import1id, dg.get(2));
        checkUnknownSymbol(import2id, dg.get(3));

        SymbolTable st = dg.get(3).getSymbolTable();
        checkLocalTable(st);
        checkUnknownSymbol("S1", import1id, st);
        checkUnknownSymbol("S2", import2id, st);
        assertEquals(local1id - 1, st.getImportedMaxId());

        SymbolTable dummy = checkFirstImport("T", 1, new String[maxId], st);
        assertTrue(dummy.isSubstitute());
        checkUnknownSymbol("S1", import1id, dummy);
        checkUnknownSymbol("S2", import2id, dummy);

        SymbolTable[] importedTables = st.getImportedTables();
        assertEquals(1, importedTables.length);
    }

    /**
     * Import v2 but catalog has v1.
     */
    @Test
    public void testLocalTableWithLesserImport()
        throws IOException
    {
        final int import1id = systemMaxId() + 1;
        final int import2id = systemMaxId() + 2;
        final int fred3id   = systemMaxId() + 3;

        final int maxLocalId = systemMaxId() + IMPORTED_2_MAX_ID + 2;

        registerImportedV1();
        registerImportedV2();

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:2, " +
            "            max_id:" + IMPORTED_2_MAX_ID + "}]," +
            "}\n" +
            "local1 local2 'imported 1' 'imported 2' fred3";
        byte[] binary = encode(text);

        // Remove the imported table before decoding the binary.
        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        assertNotNull(catalog.removeTable("imported", 2));

        IonDatagram dg = loader().load(binary);

        checkSymbol("local1", dg.get(0));
        checkSymbol("local2", dg.get(1));
        checkSymbol("imported 1", import1id, dg.get(2));
        checkSymbol("imported 2", import2id, dg.get(3));
        checkUnknownSymbol(fred3id, dg.get(4));

        SymbolTable st = dg.get(0).getSymbolTable();
        checkFirstImport("imported", 2,
                         new String[]{"imported 1", "imported 2", null, null},
                         st);
        assertTrue(st.isLocalTable());
        assertEquals(maxLocalId, st.getMaxId());
    }

    /**
     * Import v2 but catalog has v3.
     */
    @Test
    public void testLocalTableWithGreaterImport()
        throws IOException
    {
        final int import1id = systemMaxId() + 1;
        final int import2id = systemMaxId() + 2;
        final int fred3id   = systemMaxId() + 3;

        final int maxLocalId = systemMaxId() + IMPORTED_2_MAX_ID + 3;

        registerImportedV1();
        registerImportedV2();
        registerImportedV3();

        // fred5 is not in table version 2, so it gets local symbol
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:2, " +
            "            max_id:" + IMPORTED_2_MAX_ID + "}]," +
            "}\n" +
            "local1 local2 'imported 1' 'imported 2' fred3 fred5";
        byte[] binary = encode(text);

        // Remove the imported table before decoding the binary.
        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        assertNotNull(catalog.removeTable("imported", 2));

        IonDatagram dg = loader().load(binary);

        checkSymbol("local1", dg.get(0));
        checkSymbol("local2", dg.get(1));
        checkSymbol("imported 1", import1id, dg.get(2));
        checkUnknownSymbol(import2id, dg.get(3));
        checkSymbol("fred3", fred3id, dg.get(4));
        checkSymbol("fred5", dg.get(5));

        SymbolTable st = dg.get(0).getSymbolTable();
        checkFirstImport("imported", 2,
                         new String[]{"imported 1", null, "fred3", "fred4"},
                         st);
        SymbolTable imported = st.getImportedTables()[0];
        checkUnknownSymbol("fred5", 5, imported);

        assertTrue(st.isLocalTable());
        assertEquals(maxLocalId, st.getMaxId());
    }

    @Test
    public void testRepeatedImport()
    {
        SymbolTable importedV1 = registerImportedV1();
        SymbolTable importedV2 = registerImportedV2();

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:1}," +
            "           {name:\"imported\", version:2}]," +
            "}\n" +
            "null";

        IonValue v = oneValue(text);
        SymbolTable symbolTable = v.getSymbolTable();
        SymbolTable[] importedTables = symbolTable.getImportedTables();
        assertEquals(2, importedTables.length);
        assertSame(importedV1, importedTables[0]);
        assertSame(importedV2, importedTables[1]);
        assertEquals(systemMaxId() + IMPORTED_1_MAX_ID + IMPORTED_2_MAX_ID,
                     symbolTable.getMaxId());

        checkSymbol("imported 1", 10, symbolTable);
        checkSymbol("imported 2", 11, symbolTable);
        checkSymbol("fred3", 14, symbolTable);
        checkSymbol("fred4", 15, symbolTable);

        checkSymbol("imported 1", 12, /* dupe */ true, symbolTable);
        checkSymbol("imported 2", 13, /* dupe */ true, symbolTable);
    }

    @Test
    public void testDupLocalSymbolOnDatagram() throws Exception {
        final IonSystem ion1 = system();
        final SymbolTable st = ion1.newSharedSymbolTable("foobar", 1, Arrays.asList("s1").iterator());
        final IonMutableCatalog cat = new SimpleCatalog();
        cat.putTable(st);

        // amzn/ion-java/issues/46 has the datagram producing something like:
        // $ion_1_0 $ion_symbol_table::{imports:[{name: "foobar", version: 1, max_id: 1}], symbols: ["s1", "l1"]} $11 $12
        // local table should not have "s1", user values should be $10 $11
        IonDatagram dg = ion1.newDatagram(st);
        dg.add().newSymbol("s1");
        dg.add().newSymbol("l1");

        final IonSystem ion2 = newSystem(cat);

        dg = ion2.getLoader().load(dg.getBytes());
        checkSymbol("s1", 10, dg.get(0));
        checkSymbol("l1", 11, dg.get(1));
    }


    @Test
    public void testMalformedImportsField()
    {
        testMalformedImportsField("[]");
        testMalformedImportsField("null.list");
        testMalformedImportsField("null");
        testMalformedImportsField("'''hello'''");
        testMalformedImportsField("imports");
        testMalformedImportsField("a_symbol");


        testMalformedImportsField("[{}]");
        testMalformedImportsField("[null.struct]");
        testMalformedImportsField("[null]");
        testMalformedImportsField("[1009]");
        testMalformedImportsField("[a_symbol]");
    }

    private void testMalformedImportsField(String value)
    {
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:" + value + "," +
            "  symbols:['''local''']" +
            "}\n" +
            "null";
        IonValue v = oneValue(text);
        SymbolTable symbolTable = v.getSymbolTable();
        assertEquals(0, symbolTable.getImportedTables().length);
        checkSymbol("local", systemMaxId() + 1, symbolTable);
        assertEquals(systemMaxId() + 1, symbolTable.getMaxId());
    }


    @Test
    public void testImportWithMalformedName()
    {
        SymbolTable importedV1 = registerImportedV1();

        testImportWithMalformedName(importedV1, null);      // missing field
        testImportWithMalformedName(importedV1, " \"\" ");  // empty string
        testImportWithMalformedName(importedV1, "null.string");
        testImportWithMalformedName(importedV1, "null");
        testImportWithMalformedName(importedV1, "'syms'");  // symbol
        testImportWithMalformedName(importedV1, "123");

        // Cannot import system symtab
        testImportWithMalformedName(importedV1, "'''$ion'''");
    }

    private void testImportWithMalformedName(SymbolTable importedV1,
                                             String name)
    {
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:1}," +
            "           {version:1," + fieldText("name", name) + "}]," +
            "}\n" +
            "null";

        IonValue v = oneValue(text);
        SymbolTable symbolTable = v.getSymbolTable();
        SymbolTable[] importedTables = symbolTable.getImportedTables();
        assertEquals(1, importedTables.length);
        assertSame(importedV1, importedTables[0]);
        assertEquals(systemMaxId() + IMPORTED_1_MAX_ID,
                     symbolTable.getMaxId());
    }


    // TODO test getUsedTable(null)
    // TODO test getImportedTable(null)

    @Test
    public void testImportWithZeroMaxId()
        throws Exception
    {
        registerImportedV1();

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:1, max_id:0}]," +
            "  symbols:['''local''']" +
            "}\n" +
            "null";
        IonValue v = oneValue(text);
        SymbolTable symbolTable = v.getSymbolTable();

        SymbolTable imported =
            checkFirstImport("imported", 1, EMPTY_STRING_ARRAY, symbolTable);
        assertTrue(imported.isSubstitute());
        checkUnknownSymbol("imported 1", 1, imported);

        checkSymbol("local", systemMaxId() + 1, symbolTable);
        checkUnknownSymbol("imported 1", UNKNOWN_SYMBOL_ID, symbolTable);
    }

    @Test
    public void testImportWithBadMaxId()
    {
        SymbolTable importedV1 = registerImportedV1();

        testImportWithBadMaxId(importedV1, "null.int");
        testImportWithBadMaxId(importedV1, "null");
        testImportWithBadMaxId(importedV1, "not_an_int");
//        testImportWithBadMaxId(importedV1, "0");  Zero isn't bad, its zero!
        testImportWithBadMaxId(importedV1, "-1");
        testImportWithBadMaxId(importedV1, "-2223");

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:1}]," +
            "}\n" +
            "null";
        IonValue v = oneValue(text);
        assertSame(importedV1, v.getSymbolTable().getImportedTables()[0]);

        SimpleCatalog catalog = (SimpleCatalog)system().getCatalog();
        catalog.removeTable(importedV1.getName(), importedV1.getVersion());
        badValue(text);
    }

    public void testImportWithBadMaxId(SymbolTable expected, String maxIdText)
    {
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"imported\", version:1," +
            "            max_id:" + maxIdText +
            "  }]," +
            "}\n" +
            "null";
        IonValue v = oneValue(text);
        assertSame(expected, v.getSymbolTable().getImportedTables()[0]);
    }


    //-------------------------------------------------------------------------
    // Local symtab creation

    @Test
    public void testEmptyLocalSymtabCreation()
    {
        SymbolTable st = system().newLocalSymbolTable();
        checkEmptyLocalSymtab(st);

        st = system().newLocalSymbolTable((SymbolTable[])null);
        checkEmptyLocalSymtab(st);

        st = system().newLocalSymbolTable(new SymbolTable[0]);
        checkEmptyLocalSymtab(st);


        SymbolTable systemTable = system().getSystemSymbolTable();

        st = system().newLocalSymbolTable(systemTable);
        checkEmptyLocalSymtab(st);

        st = system().newLocalSymbolTable(new SymbolTable[]{ systemTable });
        checkEmptyLocalSymtab(st);
    }

    public void checkEmptyLocalSymtab(SymbolTable st)
    {
        SymbolTable systemTable = system().getSystemSymbolTable();

        checkLocalTable(st);
        assertSame(systemTable, st.getSystemSymbolTable());
        assertEquals(systemTable.getMaxId(), st.getMaxId());
        assertEquals(systemTable.getMaxId(), st.getImportedMaxId());
        assertEquals(0, st.getImportedTables().length);
    }

    @Test
    public void testBasicLocalSymtabCreation()
    {
        SymbolTable systemTable = system().getSystemSymbolTable();
        SymbolTable fred1 = Symtabs.CATALOG.getTable("fred", 1);

        SymbolTable st = system().newLocalSymbolTable(systemTable, fred1);
        final int importedMaxId = systemTable.getMaxId() + fred1.getMaxId();

        checkLocalTable(st);
        assertSame(systemTable, st.getSystemSymbolTable());
        assertEquals(importedMaxId, st.getMaxId());
        assertEquals(importedMaxId, st.getImportedMaxId());
        assertEquals(1, st.getImportedTables().length);
        assertSame(fred1, st.getImportedTables()[0]);

        st = system().newLocalSymbolTable(systemTable);
        checkEmptyLocalSymtab(st);
    }

    @Test
    public void testSymtabImageMaintenance()
    {
        IonSystem system = system();
        SymbolTable st = ((_Private_ValueFactory)system).getLstFactory().newLocalSymtab(system.getSystemSymbolTable());
        st.intern("foo");
        IonStruct image = symtabTree(st, system);
        st.intern("bar");
        image = symtabTree(st, system);
        IonList symbols = (IonList) image.get(SYMBOLS);
        assertEquals("[\"foo\",\"bar\"]", symbols.toString());
    }


    @Test
    public void testBadLocalSymtabCreation()
    {
        SymbolTable systemTable = system().getSystemSymbolTable();
        SymbolTable fred1 = Symtabs.CATALOG.getTable("fred", 1);

        try
        {
            system().newLocalSymbolTable((SymbolTable)null);
        }
        catch (NullPointerException e) { }

        try
        {
            system().newLocalSymbolTable(fred1, systemTable);
        }
        catch (IllegalArgumentException e) { }

        try
        {
            system().newLocalSymbolTable(fred1, null);
        }
        catch (NullPointerException e) { }

        try
        {
            system().newLocalSymbolTable(fred1,
                                         system().newLocalSymbolTable());
        }
        catch (IllegalArgumentException e) { }

    }

    //-------------------------------------------------------------------------
    // Shared symtab creation

    @Test
    public void testBasicSharedSymtabExtension()
    {
        String[] syms = { "a", null, "b" };
        SymbolTable st =
            system().newSharedSymbolTable("ST", 1,
                                          Arrays.asList(syms).iterator());
        checkSharedTable("ST", 1, new String[]{"a", "b"}, st);


        // Now create version two
        catalog().putTable(st);

        String[] syms2 = { "c", "a" };
        SymbolTable st2 =
            system().newSharedSymbolTable("ST", 2,
                                          Arrays.asList(syms2).iterator());
        checkSharedTable("ST", 2, new String[]{"a", "b", "c"}, st2);
    }


    @Test
    public void testSharedSymtabCreationWithDuplicates()
    {
        String[] syms = { "a", "b", "a", "c" };
        SymbolTable st =
            system().newSharedSymbolTable("ST", 1,
                                          Arrays.asList(syms).iterator());
        checkSharedTable("ST", 1, new String[]{"a", "b", "c"}, st);
    }


    @Test @Ignore // TODO amzn/ion-java/issues/12
    public void testSharedSymtabCreationWithEmptyName()
    {
        String[] syms = { "a", "b", "", "c" };
        SymbolTable st =
            system().newSharedSymbolTable("ST", 1,
                                          Arrays.asList(syms).iterator());
        checkSharedTable("ST", 1, new String[]{"a", "b", "c"}, st);
    }


    @Test
    public void testEmptySharedSymtabCreation()
    {
        String[] noStrings = new String[0];

        SymbolTable st = system().newSharedSymbolTable("ST", 1, null);
        checkSharedTable("ST", 1, noStrings, st);

        st = system().newSharedSymbolTable("ST", 1,
                                           Arrays.asList(noStrings).iterator());
        checkSharedTable("ST", 1, noStrings, st);
    }

    @Test
    public void testSharedSymtabCreationWithImports()
    {
        SymbolTable fred1   = Symtabs.CATALOG.getTable("fred", 1);
        SymbolTable ginger1 = Symtabs.CATALOG.getTable("ginger", 1);

        String[] syms = { "a", "fred_1", "b" };
        SymbolTable st =
            system().newSharedSymbolTable("ST", 1,
                                          Arrays.asList(syms).iterator(),
                                          fred1);
        checkSharedTable("ST", 1,
                         new String[]{"fred_1", "fred_2", "a", "b"},
                         st);
        assertEquals(fred1.findSymbol("fred_1"), st.findSymbol("fred_1"));

        // Again, with two imports

        st = system().newSharedSymbolTable("ST", 1,
                                           Arrays.asList(syms).iterator(),
                                           fred1, ginger1);
        checkSharedTable("ST", 1,
                         new String[]{"fred_1", "fred_2", "g1", "g2", "a", "b"},
                         st);
    }

    @Test
    public void testNewSharedSymtabFromReaderWithImports()
    {
        SymbolTable v1 = registerImportedV1();

        String text =
            SharedSymbolTablePrefix+
            "{ name:\"ST\", version:1, " +
            "  imports:[{name:\"imported\", version:1," +
            "            max_id:" + v1.getMaxId() +
            "  }]," +
            "  symbols:[\"imported 1\"]" +
            "}";

        SymbolTable st =
            system().newSharedSymbolTable(system().newReader(text));
        assertEquals(v1.findSymbol("imported 1"),
                     st.findSymbol("imported 1"));

    }

    private SymbolTable extendSymtab(String name, int version, String... syms)
    {
        return system().newSharedSymbolTable(name, version,
                                             stringIterator(syms));
    }


    @Test(expected = IonException.class)
    public void testNewSharedSymtabMissingPriorVersion()
    {
        extendSymtab("ST", 2, "a");
    }

    @Test
    public void testNewSharedSymtabNullName()
    {
        try
        {
            extendSymtab(null, 1, "a");
            fail("expected exception");
        }
        catch (IllegalArgumentException e) { }
    }

    @Test
    public void testNewSharedSymtabEmptyName()
    {
        try
        {
            extendSymtab("", 1, "a");
            fail("expected exception");
        }
        catch (IllegalArgumentException e) { }
    }

    @Test
    public void testNewSharedSymtabZeroVersion()
    {
        try
        {
            extendSymtab("ST", 0, "a");
            fail("expected exception");
        }
        catch (IllegalArgumentException e) { }
    }

    @Test
    public void testNewSharedSymtabNegativeVersion()
    {
        try
        {
            extendSymtab("ST", -1, "a");
            fail("expected exception");
        }
        catch (IllegalArgumentException e) { }
    }

    // TODO test import that has null sid
    // TODO test insertion of empty symbol

    public static void checkSharedTable(String name, int version,
                                        String[] expectedSymbols,
                                        SymbolTable actual)
    {
        assertTrue(actual.isSharedTable());
        assertFalse(actual.isSystemTable());
        assertEquals(null, actual.getSystemSymbolTable());

        assertTrue("shared symtab should be read-only", actual.isReadOnly());

        assertEquals(name, actual.getName());
        assertEquals(version, actual.getVersion());
        assertEquals(0, actual.getImportedMaxId());

        assertEquals("symbol count",
                     expectedSymbols.length, actual.getMaxId());

        Set<String> foundSymbols = new HashSet<String>();

        Iterator<String> iter = actual.iterateDeclaredSymbolNames();
        for (int i = 0; i < expectedSymbols.length; i++)
        {
            int sid = i+1;
            String text = expectedSymbols[i];

            assertTrue(iter.hasNext());
            assertEquals(text, iter.next());

            boolean dupe = text != null && !foundSymbols.add(text);

            checkSymbol(text, sid, dupe, actual);
        }

        assertFalse(iter.hasNext());

        checkUnknownSymbol(" not defined ", UNKNOWN_SYMBOL_ID, actual);
    }

    IonStruct writeIonRep(SymbolTable st)
        throws IOException
    {
        IonList container = system().newEmptyList();
        st.writeTo(system().newTreeWriter(container));
        IonStruct stStruct = (IonStruct) container.get(0);
        return stStruct;
    }

    static void checkFirstImport(String name, int version, String[] expectedSymbols,
                          IonStruct symtab)
        throws IOException
    {
        IonList imports = (IonList) symtab.get(SystemSymbols.IMPORTS);
        IonStruct i0 = (IonStruct) imports.get(0);
        checkString(name, i0.get(SystemSymbols.NAME));
        checkInt(version, i0.get(SystemSymbols.VERSION));
        checkInt(expectedSymbols.length, i0.get(SystemSymbols.MAX_ID));
    }

    SymbolTable checkFirstImport(String name, int version,
                                 String[] expectedSymbols,
                                 SymbolTable st)
        throws IOException
    {
        IonStruct stStruct = writeIonRep(st);
        checkFirstImport(name, version, expectedSymbols, stStruct);

        SymbolTable importedTable = st.getImportedTables()[0];
        checkSharedTable(name, version, expectedSymbols, importedTable);

        return importedTable;
    }


    //-------------------------------------------------------------------------
    // Testing symbols field

    @Test
    public void testSystemIdOnNonStruct()
    {
        String text = "$ion_1_0::12";
        IonInt v = (IonInt) oneValue(text);
        checkInt(12, v);
    }

    @Test
    public void testSymbolTableOnNonStruct()
    {
        String text = "$ion_symbol_table::12";
        IonInt v = (IonInt) oneValue(text);
        checkInt(12, v);
    }

    @Test
    public void testNestedSystemId()
    {
        String text = "($ion_1_0)";
        IonSexp v = oneSexp(text);
        checkSymbol(ION_1_0, v.get(0));
    }

    public IonList serialize(final SymbolTable table) throws IOException {
        final IonList container = system().newEmptyList();
        table.writeTo(system().newWriter(container));
        return container;
    }

    @Test
    public void testDoubleWrite() throws IOException {
        final SymbolTable table =
            system().newSharedSymbolTable("foobar", 1, Arrays.asList("moo").iterator());
        assertEquals(serialize(table), serialize(table));
    }


    private static String fieldText(String fieldName, String value)
    {
        if (value == null) return "";
        assert value.length() > 0;

        return fieldName + ':' + value;
    }


    @Test
    public void testWriteWithSymbolTable() throws IOException
    {
        // this example code is the fix for a previous defect
        // which resulted in a bad assertion in
        // IonWriterUser.close_local_symbol_table_copy

        IonDatagram data;

        data = system().newDatagram();

        insert_local_symbol_table(data);
        append_some_data(data);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = system().newTextWriter(out);
        writer.writeValue(data);
        writer.close();

        // dataMap.put("value", out.toByteArray());
        byte[] bytes = out.toByteArray();
        assertNotNull(bytes);
    }

    private void insert_local_symbol_table(IonDatagram data)
    {
        IonStruct local_symbol_table = system().newEmptyStruct();
        local_symbol_table.addTypeAnnotation("$ion_symbol_table");

        IonList symbols = system().newEmptyList();
        symbols.add(system().newString("one"));
        symbols.add(system().newString("two"));

        local_symbol_table.add("symbols", symbols);

        data.add(local_symbol_table);
    }

    private void append_some_data(IonDatagram data)
    {
        IonStruct contents = system().newEmptyStruct();
        contents.add("one", system().newInt(1));
        contents.add("two", system().newInt(2));

        data.add(contents);
    }


    @Test
    public void testIterateDeclaredSymbolNames()
    {
        SymbolTable fred3 = Symtabs.CATALOG.getTable("fred", 3);
        int sid = fred3.getImportedMaxId();

        Iterator<String> names = fred3.iterateDeclaredSymbolNames();
        while (names.hasNext())
        {
            sid++;
            String name = names.next();
            assertSame(fred3.findKnownSymbol(sid), name);
        }

        assertEquals("last sid", fred3.getMaxId(), sid);
    }

    @Test
    public void testExtendingSharedSymbolTableWithHoles()
    {
        String serializedSymtab =
            "$ion_shared_symbol_table::{" +
            "  name:\"Test\", version:3," +
            "  symbols:[ \"one\", 2, \"three\", null, \"\" ]" +
            "}";

        registerSharedSymtab(serializedSymtab);

        Iterator<String> newSymbols =
            Arrays.asList("four", null, "five").iterator();
        SymbolTable v4 = system().newSharedSymbolTable("Test", 4, newSymbols);

        checkSharedTable("Test", 4,
                         new String[]{"one", null, "three", null, null,
                                      "four", "five"},
                         v4);
    }

    @Test
    public void testSymtabBinaryInjection() throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IonWriter writer = system().newBinaryWriter(baos);
        writer.stepIn(IonType.LIST);
        writer.writeNull();
        writer.writeInt(10);
        writer.writeFloat(10.0);
        writer.writeTimestamp(new Timestamp(2013, 1, 1));
        writer.writeSymbol("abc");  // this is where symbol table injection happens
        writer.writeString("abc");
        writer.stepOut();
        writer.finish();

        IonReader reader = system().newReader(baos.toByteArray());
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertEquals(IonType.NULL, reader.next());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals(IonType.STRING, reader.next());
        assertEquals(null, reader.next());
        reader.stepOut();
        assertEquals(null, reader.next());
    }

}
