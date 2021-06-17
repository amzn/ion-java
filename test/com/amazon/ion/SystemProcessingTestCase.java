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

package com.amazon.ion;

import static com.amazon.ion.SymbolTable.UNKNOWN_SYMBOL_ID;
import static com.amazon.ion.SystemSymbols.ION_1_0;
import static com.amazon.ion.SystemSymbols.ION_1_0_SID;
import static com.amazon.ion.SystemSymbols.ION_SHARED_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.ION_SHARED_SYMBOL_TABLE_SID;
import static com.amazon.ion.TestUtils.FERMATA;
import static com.amazon.ion.impl.Symtabs.LocalSymbolTablePrefix;

import com.amazon.ion.impl.SymbolTableTest;
import com.amazon.ion.impl.Symtabs;
import com.amazon.ion.system.SimpleCatalog;
import org.junit.Ignore;
import org.junit.Test;



public abstract class SystemProcessingTestCase
    extends IonTestCase
{
    boolean myMissingSymbolTokensHaveText = true;

    protected abstract void prepare(String text)
        throws Exception;

    protected abstract void startIteration()
        throws Exception;

    protected final void startIteration(String text)
        throws Exception
    {
        prepare(text);
        startIteration();
    }

    protected abstract void startSystemIteration()
        throws Exception;

    protected abstract void nextValue()
        throws Exception;

    protected abstract void stepIn()
        throws Exception;

    protected abstract void stepOut()
        throws Exception;

    protected abstract IonType currentValueType()
        throws Exception;

    abstract SymbolTable currentSymtab();


    abstract Checker check();

    /**
     * @param expectedText null means absent
     */
    final void checkFieldName(String expectedText, int expectedSid)
    {
        check().fieldName(expectedText, expectedSid);
    }


    /**
     * Checks a field name that's missing from the context symbol table,
     * generally because there was no exact match to an import.
     */
    void checkMissingFieldName(String expectedText, int expectedSid)
        throws Exception
    {
        if (myMissingSymbolTokensHaveText && expectedText != null)
        {
            checkFieldName(expectedText, UNKNOWN_SYMBOL_ID);
        }
        else
        {
            checkFieldName(null, expectedSid);
        }
    }

    /** Check the first annotation's text */
    final void checkAnnotation(String expectedText)
    {
        check().annotation(expectedText);
    }

    /** Check the first annotation's text and sid. */
    final void checkAnnotation(String expectedText, int expectedSid)
    {
        check().annotation(expectedText, expectedSid);
    }

    /** Check that all the annotations exist in the given order. */
    final void checkAnnotations(String[] expectedTexts, int[] expectedSids)
    {
        check().annotations(expectedTexts, expectedSids);
    }


    /**
     * Checks an annotation that's missing from the context symbol table,
     * generally because there was no exact match to an import.
     */
    void checkMissingAnnotation(String expectedText, int expectedSid)
        throws Exception
    {
        if (myMissingSymbolTokensHaveText && expectedText != null)
        {
            checkAnnotation(expectedText, UNKNOWN_SYMBOL_ID);
        }
        else
        {
            checkAnnotation(null, expectedSid);
        }
    }


    protected abstract void checkType(IonType expected)
        throws Exception;

    protected abstract void checkString(String expected)
        throws Exception;

    protected abstract void checkSymbol(String expected)
        throws Exception;

    protected abstract void checkSymbol(String expected, int expectedSid)
        throws Exception;

    /**
     * Checks a symbol that's missing from the context symbol table,
     * generally because there was no exact match to an import.
     */
    void checkMissingSymbol(String expectedText, int expectedSid)
        throws Exception
    {
        if (myMissingSymbolTokensHaveText)
        {
            checkSymbol(expectedText, UNKNOWN_SYMBOL_ID);
        }
        else
        {
            checkSymbol(null, expectedSid);
        }
    }


    protected abstract void checkInt(long expected)
        throws Exception;

    protected abstract void checkDecimal(double expected)
        throws Exception;

    protected abstract void checkFloat(double expected)
        throws Exception;

    /**
     * @param expected is the canonical form of the timestamp
     */
    protected abstract void checkTimestamp(Timestamp expected)
        throws Exception;

    /**
     * @param expected is the canonical form of the timestamp
     */
    protected void checkTimestamp(String expected)
        throws Exception
    {
        checkTimestamp(Timestamp.valueOf(expected));
    }

    protected abstract void checkEof()
        throws Exception;


    //=========================================================================

    /** TODO amzn/ion-java/issues/8 This is broken for loaders which are now more lazy */
    @Test @Ignore
    public void testLocalTableResetting()
        throws Exception
    {
        String text = "bar foo $ion_1_0 1 far boo";

        startIteration(text);

        nextValue();
        checkSymbol("bar");

        SymbolTable table1 = currentSymtab();
        checkLocalTable(table1);

        nextValue();
        checkSymbol("foo");
        SymbolTable current = currentSymtab();
        assertSame(table1, current);

        // The symbol table changes here ...
        nextValue();

        // FIXME --- how should this work?
        if (!IonType.INT.equals(currentValueType())) {
            // if we didn't hit the int 1 then we should have the $ion_1_0
            checkSymbol(ION_1_0, ION_1_0_SID);
            nextValue();
            // ???
        }
        checkInt(1);

        // we should have reset to the system symbol table here
        SymbolTable table3 = currentSymtab();
        assertNotSame(table1, table3);
        // nope, this may be the next local that will hold 'far' and 'boo':
        // assertTrue("the reset table should be a trivial table (system or null)", _Private_Utils.isTrivialTable(table3));

        nextValue();
        checkSymbol("far");

        SymbolTable table2 = currentSymtab();
        checkLocalTable(table2);

        assertNotSame("The current and initial symbol tables are the same, " +
        		"they should be different", table1, table2);

        nextValue();
        checkSymbol("boo");
        SymbolTable table4 = currentSymtab();
        assertSame(table2, table4);
    }

    @Test
    public void testTrivialLocalTableResetting()
        throws Exception
    {
        String text = "1 $ion_1_0 2";

        startIteration(text);

        nextValue();
        checkInt(1);

        SymbolTable table1 = currentSymtab();
        checkTrivialLocalTable(table1);

        // move from the 1 to either the $ion_1_0 or the 2
        nextValue();

// FIXME --- how should this work?
//           since there is no symbol table other than
//           the default system the $ion_1_0 in the middle
//           of this sequence isn't meaningful, but if
//           you're asking for system values with a system
//           reader you will see it.  If you convert through
//           a number of readers and writers you might or
//           might not preserve the $ion_1_0
        if (!IonType.INT.equals(currentValueType())) {
            // if we didn't hit the int 2 then we should have the $ion_1_0
            checkSymbol(ION_1_0, ION_1_0_SID);
            nextValue();
        }

        checkInt(2);

        SymbolTable table2 = currentSymtab();
        checkTrivialLocalTable(table2);
        if (table1.isLocalTable() || table2.isLocalTable())
        {
            assertNotSame(table1, table2);
        }
        assertEquals(systemMaxId(), table2.getMaxId());
    }

    @Test
    public void testLocalTableReplacement()
        throws Exception
    {
        String text =
            "$ion_symbol_table::{" +
            "  symbols:[ \"foo\", \"bar\" ]," +
            "}\n" +
            "bar foo\n" +
            "$ion_symbol_table::{" +
            "  symbols:[ \"bar\" ]," +
            "}\n" +
            "bar foo";

        startIteration(text);

        nextValue();
        checkSymbol("bar", systemMaxId() + 2);

        SymbolTable table1 = currentSymtab();
        checkLocalTable(table1);

        nextValue();
        checkSymbol("foo", systemMaxId() + 1);

        // Symtab changes here...

        nextValue();
        checkSymbol("bar", systemMaxId() + 1);

        SymbolTable table2 = currentSymtab();
        checkLocalTable(table2);
        assertNotSame(table1, table2);
        assertTrue(systemMaxId() + 1 <= table2.getMaxId());
        assertTrue(systemMaxId() + 2 >= table2.getMaxId());

        nextValue();
        checkSymbol("foo");
        assertEquals(table2.getMaxId(), currentSymtab().getMaxId());
    }

    @Test
    public void testTrivialLocalTableReplacement()
        throws Exception
    {
        String text =
            "$ion_symbol_table::{" +
            "}\n" +
            "1\n" +
            "$ion_symbol_table::{" +
            "}\n" +
            "2";

        startIteration(text);

        nextValue();
        checkInt(1);

        SymbolTable table1 = currentSymtab();
        // Note: there's no need to require implementations to actually create an LST in this case. It's fine to
        // treat it as a reset to the system symbol table.
        assertFalse("table is substitute", table1.isSubstitute());
        checkUnknownSymbol(" not defined ", UNKNOWN_SYMBOL_ID, table1);

        SymbolTable system = table1.getSystemSymbolTable();
        checkSystemTable(system);
        assertEquals(system.getIonVersionId(), table1.getIonVersionId());
        assertEquals(systemMaxId(), table1.getMaxId());

        nextValue();
        checkInt(2);

        SymbolTable table2 = currentSymtab();
        assertFalse("table is substitute", table2.isSubstitute());
        checkUnknownSymbol(" not defined ", UNKNOWN_SYMBOL_ID, table2);

        system = table2.getSystemSymbolTable();
        checkSystemTable(system);
        assertEquals(system.getIonVersionId(), table2.getIonVersionId());
        assertEquals(systemMaxId(), table2.getMaxId());
    }


    @Test
    public void testLocalSymtabWithOpenContent()
        throws Exception
    {
        String data = "$ion_symbol_table::{open:33,symbols:[\"a\",\"b\"]} b";

        startIteration(data);
        nextValue();
        checkSymbol("b", 11);
    }


    /**
     * Import v2 but catalog has v1.
     */
    @Test
    public void testLocalTableWithLesserImport()
        throws Exception
    {
        final int fred1id = systemMaxId() + 1;
        final int fred2id = systemMaxId() + 2;
        final int fred3id = systemMaxId() + 3;

        final int local = systemMaxId() + Symtabs.FRED_MAX_IDS[2];
        final int local3id = local + 3;

        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        Symtabs.register("fred", 1, catalog);
        Symtabs.register("fred", 2, catalog);

        // {  name:"fred", version:1,
        //    symbols:["fred_1", "fred_2"]}

        // {  name:"fred", version:2,
        //    symbols:["fred_1","fred_2","fred_3","fred_4",]}

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"fred\", version:2, " +
            "            max_id:" + Symtabs.FRED_MAX_IDS[2] + "}]," +
            "}\n" +
            "local1 local2 fred_1 fred_2 fred_3 $12 " +
            "fred_3::$10 [{fred_3:local2}]";
        // TODO { $12:something }
        // TODO $12::something
        // Nesting flushed out a bug at one point


        prepare(text);

        // Remove the imported table: fred version 2
        // We'll read using fred version 1 with a max id of 2
        // which causes fred_3 to "disappear".
        // If forced to, the reader will assign fred_2 to a new local id.
        assertNotNull(catalog.removeTable("fred", 2));

        // at this point the effective symbol list should be:
        // fred::version::'2'::symbols:[
        // 10::   '''fred_1''',
        // 11::   '''fred_2''',
        // 12::        null,
        // 13::        null,
        // local::symbols:[
        // 14::   '''local1''',
        // 15::   '''local2''',
        // 16::   '''fred_3''',
        // 17::   '''fred_4''',

        startIteration();

        nextValue();
        checkSymbol("local1");

        nextValue();
        checkSymbol("local2");

        nextValue();
        checkSymbol("fred_1", fred1id);

        nextValue();
        checkSymbol("fred_2", fred2id);

        nextValue();
        checkMissingSymbol("fred_3", fred3id);

        nextValue();
// TODO checkAbsentSidLiteral("fred_3", fred3id);

        nextValue();
        checkSymbol("fred_1", fred1id);
        checkMissingAnnotation("fred_3", fred3id);

        nextValue();
        stepIn();
            nextValue();
            stepIn();
                nextValue();
                checkMissingFieldName("fred_3", fred3id);
                checkSymbol("local2");

                checkEof();
            stepOut();
            checkEof();
        stepOut();

        checkEof();
    }

    /**
     * Import v2 but catalog has v3.
     */
    @Test
    public void testLocalTableWithGreaterImport()
        throws Exception
    {
        final int fred1id_symtab = systemMaxId() + 1;  // expect 9 + 1 = 10
        final int fred2id_symtab = systemMaxId() + 2;
        final int fred3id_symtab = systemMaxId() + 3;

        final int local = systemMaxId() + Symtabs.FRED_MAX_IDS[2];
        final int local1id = local + 1; // expect 9 + 4 + 1 = 14 id for local1
        final int local2id = local + 2; // 15: id for local2
        final int local3id = local + 3; // 16: id for fred_2, which has been removed from version 2 of the sym tab, so is now local
        final int local4id = local + 4; // 17: id for fred_5, which isn't present when version 2 was defined

        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        Symtabs.register("fred", 2, catalog);
        SymbolTable fredV3 = Symtabs.register("fred", 3, catalog);

        // Make sure our syms don't overlap.
        assertTrue(fredV3.findSymbol("fred_5") != local3id);

        // version: 3: /* Removed fred_2 */
        //"{  name:"fred", version:3," +
        //"  symbols:["fred_1",null,"fred_3","fred_4","fred_5",]}"



        // fred_5 is not in table version 2, so it gets local symbol
        // fred_2 is missing from version 3
        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"fred\", version:2, " +
            "            max_id:" + Symtabs.FRED_MAX_IDS[2] + "}]" +
            "} " +
            "local1 local2 fred_1 fred_2 fred_3 fred_5";

        // at this point the effective symbol list should be:
        // fred::version::'2'::symbols:[
        // 10::   '''fred_1''',
        // 11::        null, // removed when fred version 2 was removed and replaced by fred version 3 (which has the symbol fred_2 removed)
        // 12::   '''fred_3''',
        // 13::   '''fred_4''',
        // local::symbols:[
        // 14::   '''local1''',
        // 15::   '''local2''',
        // 16::   '''fred_2''', // since it's been removed from fred 3 (local 3)
        // 17::   '''fred_5''', // since it's below the max is of fred 2 so not visible (local 4)

        prepare(text);

        // Remove the imported table before decoding the binary.
        assertNotNull(catalog.removeTable("fred", 2));

        startIteration();

        nextValue();
        checkSymbol("local1");

        nextValue();
        checkSymbol("local2");

        nextValue();
        checkSymbol("fred_1", fred1id_symtab);

        nextValue();
        checkMissingSymbol("fred_2", fred2id_symtab);

        nextValue();
        checkSymbol("fred_3", fred3id_symtab);

        nextValue();
        checkSymbol("fred_5");

        checkEof();
    }

    /**
     * Checks that the imported table retrieved from the current symtab has the
     * correct specs (i.e. isSubstitute, name, version, max_id). The correct
     * specs are based on <code>declaredMaxId</code> and
     * <code>testRemovalAfterPrepare</code> parameters.
     *
     * @param declaredMaxId
     *          the max_id of the import declaration being tested
     * @param testRemovalAfterPrepare
     *          flag on whether the exact match of import contained within the
     *          catalog is removed after {@link #prepare(String)}
     * @param catalog
     * @throws Exception
     */
    protected void checkImportTableSpecs(int declaredMaxId,
                                        boolean testRemovalAfterPrepare,
                                        SimpleCatalog catalog)
        throws Exception
    {
        // The exact match of import contained within the catalog
        // is defined as { name: "fred",
        //                 version: 2,
        //                 max_id: Symtabs.FRED_MAX_IDS[2] }

        String text =
            LocalSymbolTablePrefix +
            "{" +
            "  imports:[{name:\"fred\", version:2, " +
            "            max_id:" + declaredMaxId + "}]," +
            "}\n" +
            "local1";

        Symtabs.register("fred", 2, catalog);
        prepare(text);

        if (testRemovalAfterPrepare) {
            assertNotNull(catalog.removeTable("fred", 2));
        }

        startIteration();

        nextValue();
        checkSymbol("local1");

        // There should be only one imported symtab
        SymbolTable[] imports = currentSymtab().getImportedTables();
        assertEquals(1, imports.length);

        // If exact import is removed after prepare, imported symtab must be
        // a substitute table
        // If exact import is not removed after prepare, imported symtab must be
        // a substitute table iff declaredMaxId is different from exact max_id
        assertEquals(testRemovalAfterPrepare || declaredMaxId != Symtabs.FRED_MAX_IDS[2],
                     imports[0].isSubstitute());

        assertEquals(Symtabs.FRED_NAME, imports[0].getName());
        assertEquals(2, imports[0].getVersion());
        assertEquals(declaredMaxId, imports[0].getMaxId());

        checkEof();
    }

    protected void checkImportTableSpecsWithVariants(SimpleCatalog catalog)
        throws Exception
    {
        final int exactMaxId = Symtabs.FRED_MAX_IDS[2];
        assertTrue(exactMaxId > 1);

        // MaxId variants WITHOUT removal of exact match in catalog after prepare
        checkImportTableSpecs(exactMaxId,     // equal max id
                             false,
                             catalog);
        checkImportTableSpecs(exactMaxId - 1, // lesser max id
                             false,
                             catalog);
        checkImportTableSpecs(exactMaxId + 1, // greater max id
                             false,
                             catalog);

        // MaxId variants WITH removal of exact match in catalog after prepare
        checkImportTableSpecs(exactMaxId,     // equal max id
                             true,
                             catalog);
        checkImportTableSpecs(exactMaxId - 1, // lesser max id
                             true,
                             catalog);
        checkImportTableSpecs(exactMaxId + 1, // greater max id
                             true,
                             catalog);
    }

    /**
     * Import v2 but catalog has v1.
     */
    @Test
    public void testSubstituteTableWithLesserVersionImport()
        throws Exception
    {
        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        Symtabs.register("fred", 1, catalog);

        checkImportTableSpecsWithVariants(catalog);
    }

    /**
     * Import v2 but catalog has v3.
     */
    @Test
    public void testSubstituteTableWithGreaterVersionImport()
        throws Exception
    {
        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        Symtabs.register("fred", 3, catalog);

        checkImportTableSpecsWithVariants(catalog);
    }

    /**
     * Import v2 and catalog has v2.
     */
    @Test
    public void testSubstituteTableWithEqualVersionImport()
        throws Exception
    {
        SimpleCatalog catalog = (SimpleCatalog) system().getCatalog();
        Symtabs.register("fred", 2, catalog);

        checkImportTableSpecsWithVariants(catalog);
    }


    @Test
    public void testSidLiteralForIvm()
        throws Exception
    {
        startIteration("$2 $2");
        checkEof();
    }

    @Test
    public void testSidlikeSymbols()
        throws Exception
    {
        String text =
            Symtabs.printLocalSymtab("$7", "$6", "$5")
            + "{ '$7':'$6'::'$7'::'$5' }";

        startIteration(text);
        nextValue();
        stepIn();
        nextValue();
        checkFieldName("$7", 10);
        checkAnnotations(new String[]{ "$6", "$7"}, new int[]{ 11, 10 });
        checkSymbol("$5", 12);
    }

    @Test
    public void testSharedTableNotAddedToCatalog()
        throws Exception
    {
        String text =
            ION_1_0 + " " +
            SymbolTableTest.IMPORTED_1_SERIALIZED +
            " 'imported 2'";
        assertNull(system().getCatalog().getTable("imported"));

        startIteration(text);
        nextValue();
        checkType(IonType.STRUCT);
        checkAnnotation(ION_SHARED_SYMBOL_TABLE,
                        ION_SHARED_SYMBOL_TABLE_SID);

        assertNull(system().getCatalog().getTable("imported"));

        nextValue();
        checkSymbol("imported 2");
    }


    /**
     * Parse Ion string data and ensure it matches expected text.
     */
    protected void checkString(String expectedValue, String ionData)
        throws Exception
    {
        checkString(expectedValue, ionData, ionData);
    }

    /**
     * Parse Ion string data and ensure it matches expected text.
     */
    protected void checkString(String expectedValue,
                              String expectedRendering,
                              String ionData)
        throws Exception
    {
        startIteration(ionData);
        nextValue();
        checkString(expectedValue);
        checkEof();
    }

    @Test
    public void testUnicodeCharacters()
        throws Exception
    {
        String ionData = "\"\\0\"";
        checkString("\0", ionData);

        ionData = "\"\\x01\"";
        checkString("\01", ionData);

        // U+007F is a control character
        ionData = "\"\\x7f\"";
        checkString("\u007f", ionData);

        ionData = "\"\\xff\"";
        checkString("\u00ff", ionData);

        ionData = "\"\\" + "u0110\""; // Carefully avoid Java escape
        checkString("\u0110", ionData);

        ionData = "\"\\" + "uffff\""; // Carefully avoid Java escape
        checkString("\uffff", ionData);

        ionData = "\"\\" + "U0001d110\""; // Carefully avoid Java escape
        checkString("\ud834\udd10", ionData);

        // The largest legal code point
        ionData = "\"\\" + "U0010ffff\""; // Carefully avoid Java escape
        checkString("\udbff\udfff", ionData);
    }


    @Test
    public void testSurrogateGluing()
        throws Exception
    {
        // Three ways to represent each surrogate:
        //  1) The actual UTF-8 or UTF-16
        //  2) The \ u 2-byte escape
        //  3) The \ U 4-byte escape
        String[] highs = { "\uD834", "\\" + "uD834", "\\" + "U0000D834" };
        String[] lows  = { "\uDD10", "\\" + "uDD10", "\\" + "U0000DD10" };

        for (int i = 0; i < highs.length; i++) {
            String high = highs[i];
            for (int j = 0; j < lows.length; j++) {
                String low = lows[j];

                String ionData = '"' + high + low + '"';
                checkString(FERMATA, "\"\\U0001d110\"", ionData);

                ionData = "'''" + high + low + "'''";
                checkString(FERMATA, "\"\\U0001d110\"", ionData);
            }
        }
    }

    @Test
    public void testQuotesInLongStrings()
        throws Exception
    {
        checkString("'", "\"'\"", "'''\\''''");
        checkString("x''y", "\"x''y\"", "'''x''y'''");
        checkString("x'''y", "\"x'''y\"", "'''x''\\'y'''");
        checkString("x\"y", "\"x\\\"y\"", "'''x\"y'''");
        checkString("x\"\"y", "\"x\\\"\\\"y\"", "'''x\"\"y'''");
    }

    // TODO similar tests on clob


    /**
     * Traps a bug in lite DOM transitioning to large size.
     * For e.g. a value's type descriptor's 'length value' (binary encoding) is
     * encoded differently when representation is at least 14 bytes long.
     */
    @Test
    public void testLargeStructWithUnknownFieldNames()
        throws Exception
    {
        startIteration(ION_1_0 +
                       " $ion_symbol_table::{imports:[{name:\"foo\", version: 1, max_id: 10}]}" +
                       " { $10:10, $11:11, $12:12, $13:13, $14:14," +
                       "   $15:15, $16:16, $17:17, $18:18, $19:19 }");
        nextValue();
            stepIn();
            for (int i = 10; i <= 19; i++)
            {
                nextValue();
            }
            checkEof();
            stepOut();
        checkEof();
    }

    @Test
    public void testUnknownFieldNames()
        throws Exception
    {
        startIteration(ION_1_0 + " $ion_symbol_table::{imports:[{name:\"foo\", version: 1, max_id: 3}]} $10 { $11:$11, $12:$12 } ");

        nextValue();
        checkSymbol(null, 10);

        nextValue();
        stepIn();
            nextValue();
            checkMissingFieldName(null, 11);
            checkSymbol(null, 11);

            nextValue();
            checkMissingFieldName(null, 12);
            checkSymbol(null, 12);

            checkEof();
        stepOut();

        checkEof();
    }

    @Test
    public void testUnknownAnnotations()
        throws Exception
    {
        startIteration(ION_1_0 + " $ion_symbol_table::{imports:[{name:\"foo\", version: 1, max_id: 3}]} $10::$10 $11::[ $12::$12 ]");

        nextValue();
        checkSymbol(null, 10);
        checkMissingAnnotation(null, 10);

        nextValue();
        checkMissingAnnotation(null, 11);
        stepIn();
            nextValue();
            checkMissingAnnotation(null, 12);
            checkSymbol(null, 12);

            checkEof();
        stepOut();

        checkEof();
    }


    @Test @Ignore
    public void testPosInt() // TODO rework?
        throws Exception
    {
        startIteration("+1");
        nextValue();
        checkSymbol("+");
        nextValue();
        checkInt(1);
        checkEof();
    }

    @Test @Ignore
    public void testPosDecimal() // TODO rework?
        throws Exception
    {
        startIteration("+123d0");
        nextValue();
        checkSymbol("+");
        nextValue();
        checkDecimal(123D);
        checkEof();
    }

    @Test
    public void testNegativeZeroDecimal()
        throws Exception
    {
        startIteration("-0d0");
        nextValue();
        checkDecimal(-0.d); // TODO this should pass IonBigDecimal
        checkEof();
    }

    @Test @Ignore
    public void testPosFloat() // TODO rework?
        throws Exception
    {
        startIteration("+123e0");
        nextValue();
        checkSymbol("+");
        nextValue();
        checkFloat(123D);
        checkEof();
    }

    @Test @Ignore
    public void testPosTimestamp() // TODO rework?
        throws Exception
    {
        startIteration("+2009-02-18");
        nextValue();
        checkSymbol("+");
        nextValue();
        checkTimestamp("2009-02-18");
        checkEof();
    }

    @Test
    public void testTimestampWithRolloverOffset()
        throws Exception
    {
        String text = "2009-10-01T00:00+01:00";
        startIteration(text);
        nextValue();
        checkTimestamp("2009-10-01T00:00+01:00");
        checkEof();
    }


    @Test
    public void testShortTimestamps()
        throws Exception
    {
        String text = "2007T 2007-04T 2007-04-25 2007-04-25T";

        startIteration(text);
        nextValue();
        checkTimestamp("2007T");
        nextValue();
        checkTimestamp("2007-04T");
        nextValue();
        checkTimestamp("2007-04-25");
        nextValue();
        checkTimestamp("2007-04-25");
        checkEof();
    }

    @Test
    public void testTimestampWithZeroFraction()
        throws Exception
    {
        String text = "2009-11-23T17:04:03.0-04:00";
        startIteration(text);
        nextValue();
        checkTimestamp("2009-11-23T17:04:03.0-04:00");
        checkEof();
    }

    @Test
    public void testNullTimestamp()
        throws Exception
    {
        String text = "null.timestamp";
        startIteration(text);
        nextValue();
        checkTimestamp((Timestamp)null);
        checkEof();
    }

    @Test
    public void testSpecialFloats()
        throws Exception
    {
        startIteration("nan +inf -inf");
        nextValue();
        checkFloat(Double.NaN);
        nextValue();
        checkFloat(Double.POSITIVE_INFINITY);
        nextValue();
        checkFloat(Double.NEGATIVE_INFINITY);
        checkEof();
    }


    //=========================================================================

    @Test
    public void testSystemIterationShowsIvm()
        throws Exception
    {
        String text = ION_1_0;

        prepare(text);
        startSystemIteration();
        nextValue();
        checkSymbol(ION_1_0, ION_1_0_SID);
        SymbolTable st = currentSymtab();

        // system readers don't necessarily support symbol tables
        // but if they do it better be the system table at this point
        if (st != null) {
            assertTrue("expected system table", st.isSystemTable());
            assertEquals(ION_1_0, st.getIonVersionId());
        }
        checkEof();
    }

    // TODO amzn/ion-java/issues/25 test for interspersed IVMs - testSystemIterationShowsInterspersedIvm

    @Test
    public void testHighUnicodeDirectInBlob()
    {
        try {
            loader().load("{{\ufffd}}");
            fail();
        } catch (final IonException e) {/* expected */}
    }

    // TODO test injected symtabs's symtab
    @Test @Ignore
    public void testSymtabOnInjectedSymtab()
        throws Exception
    {
        String text = "local";

        prepare(text);
        startSystemIteration();

        nextValue();
        checkSymbol(ION_1_0, ION_1_0_SID);
        SymbolTable st = currentSymtab();
        assertTrue(st.isSystemTable());
        assertEquals(ION_1_0, st.getIonVersionId());

        nextValue();
        checkType(IonType.STRUCT);
        assertSame(st, currentSymtab());

        nextValue();
        checkSymbol("local", systemMaxId() + 1);
        SymbolTable local = currentSymtab();
        assertTrue(local.isLocalTable());
        assertSame(st, local.getSystemSymbolTable());

        checkEof();
    }


    @Test
    public void testDuplicateAnnotations()
    throws Exception
    {
        int sid = systemMaxId() + 1;

        startIteration("$ion_symbol_table::{symbols:[\"ann\"]} " +
                       "ann::ann::null");
        nextValue();
        checkAnnotations(new String[]{ "ann", "ann" },
                         new int[]{ sid, sid });
    }

    @Test
    public void testIvmWithAnnotationText()
        throws Exception
    {
        startIteration("some_annotation::" + ION_1_0 + " 123");

        // some_annotation::$ion_1_0 is not an IVM, but an annotated
        // user-value symbol
        nextValue();
        checkSymbol(ION_1_0);
        checkAnnotation("some_annotation");

        nextValue();
        checkInt(123);

        checkEof();
    }

    @Test
    public void testIvmWithAnnotationSid()
        throws Exception
    {
        startIteration("$0::" + ION_1_0 + " 123");

        // $0::$ion_1_0 is not an IVM, but an annotated user-value symbol
        nextValue();
        checkSymbol(ION_1_0);
        checkAnnotation(null, 0);

        nextValue();
        checkInt(123);

        checkEof();
    }

    protected int expectedLocalNullSlotSymbolId() {
        return 10;
    }

    protected void checkLocalSymtabWithMalformedSymbolEntry(String symbolValue)
        throws Exception
    {
        String text =
            "$ion_symbol_table::{" +
            "  symbols:[" + symbolValue + "]}\n" +
            "$10";

        startIteration(text);

        nextValue();
        checkSymbol(null, expectedLocalNullSlotSymbolId());

        checkEof();
    }

    /**
     * According to the spec malformed symbol entries must be interpreted as null, this test
     * verifies this behavior
     */
    @Test
    public void testLocalSymtabWithMalformedSymbolEntries()
        throws Exception
    {
        checkLocalSymtabWithMalformedSymbolEntry("null");                       // null
        checkLocalSymtabWithMalformedSymbolEntry("true");                       // boolean
        checkLocalSymtabWithMalformedSymbolEntry("100");                        // integer
        checkLocalSymtabWithMalformedSymbolEntry("0.123");                      // decimal
        checkLocalSymtabWithMalformedSymbolEntry("-0.12e4");                    // float
        checkLocalSymtabWithMalformedSymbolEntry("2013-05-09");                 // timestamp
        checkLocalSymtabWithMalformedSymbolEntry("a_symbol");                   // symbol
        checkLocalSymtabWithMalformedSymbolEntry("{{MTIz}}");                   // blob
        checkLocalSymtabWithMalformedSymbolEntry("{{'''clob_content'''}}");     // clob
        checkLocalSymtabWithMalformedSymbolEntry("{a:123}");                    // struct
        checkLocalSymtabWithMalformedSymbolEntry("[a, b, c]");                  // list
        checkLocalSymtabWithMalformedSymbolEntry("(a b c)");                    // sexp
        checkLocalSymtabWithMalformedSymbolEntry("null.string");                // null.string
        checkLocalSymtabWithMalformedSymbolEntry("['''whee''']");               // string nested inside list
    }

}
