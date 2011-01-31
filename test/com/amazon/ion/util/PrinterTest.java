// Copyright (c) 2007-2011 Amazon.com, Inc.  All rights reserved.

package com.amazon.ion.util;


import static com.amazon.ion.SystemSymbolTable.ION_1_0;

import com.amazon.ion.BlobTest;
import com.amazon.ion.ClobTest;
import com.amazon.ion.IntTest;
import com.amazon.ion.IonBlob;
import com.amazon.ion.IonBool;
import com.amazon.ion.IonClob;
import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonDecimal;
import com.amazon.ion.IonFloat;
import com.amazon.ion.IonInt;
import com.amazon.ion.IonList;
import com.amazon.ion.IonNull;
import com.amazon.ion.IonSexp;
import com.amazon.ion.IonString;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.IonTimestamp;
import com.amazon.ion.IonValue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class PrinterTest
    extends IonTestCase
{
    private Printer myPrinter;


    @Before @Override
    public void setUp()
        throws Exception
    {
        super.setUp();
        myPrinter = new Printer();
    }

    public IonInt int123()
    {
        return system().newInt(123);
    }


    public IonSymbol symbol(String text)
    {
        return system().newSymbol(text);
    }

    public IonSymbol symbolHello()
    {
        return symbol("hello");
    }

    public IonSymbol symbolNotEquals()
    {
        return symbol("!=");
    }

    public void checkRendering(String expected, IonValue value)
        throws Exception
    {
        StringBuilder w = new StringBuilder();
        myPrinter.myOptions.skipRedundantSystemValues = true;
        myPrinter.print(value, w);
        assertEquals(expected, w.toString());
    }


    //=========================================================================
    // Test cases

    @Test
    public void testPrintingAnnotations()
        throws Exception
    {
        IonNull value = (IonNull) oneValue("an::null");
        checkRendering("an::null", value);

        value.addTypeAnnotation("+");
        value.addTypeAnnotation("\u0000");
        checkRendering("an::'+'::'\\0'::null", value);

        myPrinter.setPrintSymbolAsString(true);
        checkRendering("an::'+'::'\\0'::null", value);
        myPrinter.setPrintStringAsJson(true);
        checkRendering("an::'+'::'\\0'::null", value);
        myPrinter.setPrintSymbolAsString(false);
        myPrinter.setPrintStringAsJson(false);

        myPrinter.setSkipAnnotations(true);
        checkRendering("null", value);
        myPrinter.setSkipAnnotations(false);

        IonSexp s = system().newEmptySexp();
        s.add(value);
        checkRendering("(an::'+'::'\\0'::null)", s);
        myPrinter.setPrintSymbolAsString(true);
        checkRendering("(an::'+'::'\\0'::null)", s);
        myPrinter.setPrintStringAsJson(true);
        checkRendering("(an::'+'::'\\0'::null)", s);
    }


    @Test
    public void testPrintingBlob()
        throws Exception
    {
        IonBlob value = system().newNullBlob();
        checkRendering("null.blob", value);

        for (int i = 0; i < BlobTest.TEST_DATA.length; i++)
        {
            BlobTest.TestData td = BlobTest.TEST_DATA[i];
            value.setBytes(td.bytes);

            myPrinter.setPrintBlobAsString(true);
            checkRendering("\"" + td.base64 + "\"", value);

            myPrinter.setPrintBlobAsString(false);
            checkRendering("{{" + td.base64 + "}}", value);
        }

        value = (IonBlob) oneValue("{{}}");
        checkRendering("{{}}", value);

        value.addTypeAnnotation("an");
        checkRendering("an::{{}}", value);
    }


    @Test
    public void testPrintingBool()
        throws Exception
    {
        IonBool value = system().newNullBool();
        checkRendering("null.bool", value);

        value.setValue(true);
        checkRendering("true", value);

        value.setValue(false);
        checkRendering("false", value);

        value.addTypeAnnotation("an");
        checkRendering("an::false", value);
    }


    @Test
    public void testPrintingClob()
        throws Exception
    {
        IonClob value = system().newNullClob();
        checkRendering("null.clob", value);

        value.setBytes(ClobTest.SAMPLE_ASCII_AS_UTF8);
        checkRendering("{{\"" + ClobTest.SAMPLE_ASCII + "\"}}", value);

        // TODO test "real" UTF8 and other encodings.

        value = (IonClob) oneValue("{{\"\"}}");
        checkRendering("{{\"\"}}", value);

        value.addTypeAnnotation("an");
        checkRendering("an::{{\"\"}}", value);

        myPrinter.setPrintClobAsString(true);
        checkRendering("an::\"\"", value);

        value.clearTypeAnnotations();
        value.setBytes(ClobTest.SAMPLE_ASCII_AS_UTF8);
        checkRendering("\"" + ClobTest.SAMPLE_ASCII + "\"", value);

        value = (IonClob) oneValue("{{'''Ab\\0'''}}");
        myPrinter.setPrintClobAsString(false);
        checkRendering("{{\"Ab\\0\"}}", value);
        myPrinter.setPrintClobAsString(true);
        checkRendering("\"Ab\\0\"", value);
        myPrinter.setPrintStringAsJson(true);
        checkRendering("\"Ab\\u0000\"", value);
    }


    @Test
    public void testPrintingDatagram()
        throws Exception
    {
        IonDatagram dg = loader().load("a b c");
        StringBuilder w = new StringBuilder();
        myPrinter.print(dg, w);
        String text = w.toString();
        assertTrue("missing version marker",
                   text.startsWith(ION_1_0 + ' '));
        assertTrue("missing data",
                   text.endsWith(" a b c"));

        // Just force symtab analysis and make sure output is still okay
        dg.getBytes(new byte[dg.byteSize()]);
        text = w.toString();
        assertTrue("missing version marker",
                   text.startsWith(ION_1_0 + ' '));
        assertTrue("missing data",
                   text.endsWith(" a b c"));

        // We shouldn't jnject a local table if its not needed.
        String data = "2 '+' [2,'+']";
        String dataWithIvm = ION_1_0 + ' ' + data;
        dg = loader().load(dataWithIvm);
        checkRendering(dataWithIvm, dg);

        myPrinter.setSkipSystemValues(true);
        checkRendering(data, dg);

        myPrinter.setPrintDatagramAsList(true);
        checkRendering("[2,'+',[2,'+']]", dg);

        myPrinter.setPrintDatagramAsList(false);
        myPrinter.setSkipSystemValues(false);
        myPrinter.setJsonMode();
        checkRendering("[2,\"+\",[2,\"+\"]]", dg);
    }


    @Test
    public void testPrintingDecimal()
        throws Exception
    {
        IonDecimal value = system().newNullDecimal();
        checkRendering("null.decimal", value);

        value.setValue(-123);
        checkRendering("-123.", value);

        value.setValue(456);
        checkRendering("456.", value);

        value.setValue(0);
        checkRendering("0.", value);

        value.addTypeAnnotation("an");
        checkRendering("an::0.", value);

        value = (IonDecimal) oneValue("0d42");
        checkRendering("0d42", value);

        value = (IonDecimal) oneValue("0d+42");
        checkRendering("0d42", value);

        value = (IonDecimal) oneValue("0d-42");
        checkRendering("0d-42", value);

        value = (IonDecimal) oneValue("100d-1");
        checkRendering("10.0", value);

        value = (IonDecimal) oneValue("100d3");
        checkRendering("100d3", value);

        myPrinter.setPrintDecimalAsFloat(true);
        checkRendering("100e3", value);
    }


    @Test
    public void testPrintingFloat()
        throws Exception
    {
        IonFloat value = system().newNullFloat();
        checkRendering("null.float", value);

        value.setValue(-123);
        checkRendering("-123e0", value);

        value.setValue(456);
        checkRendering("456e0", value);

        value.setValue(0);
        checkRendering("0e0", value);

        value.addTypeAnnotation("an");
        checkRendering("an::0e0", value);

        value = (IonFloat) oneValue("1e4");
        // TODO this prints as 10000e0 which is less than ideal.
//      checkRendering("1e4", value);

        value = (IonFloat) oneValue("1e+4");
        // TODO this prints as 10000e0 which is less than ideal.
//      checkRendering("1e4", value);

        value = (IonFloat) oneValue("125e-2");
        checkRendering("125e-2", value);
    }


    @Test
    public void testPrintingInt()
        throws Exception
    {
        IonInt value = system().newNullInt();
        checkRendering("null.int", value);

        value.setValue(-123);
        checkRendering("-123", value);

        value.setValue(456);
        checkRendering("456", value);

        value.setValue(IntTest.A_LONG_INT);
        checkRendering(Long.toString(IntTest.A_LONG_INT), value);

        value.setValue(0);
        checkRendering("0", value);

        value.addTypeAnnotation("an");
        checkRendering("an::0", value);
    }


    @Test
    public void testPrintingList()
        throws Exception
    {
        IonList value = system().newNullList();
        checkRendering("null.list", value);

        value.add(system().newNull());
        checkRendering("[null]", value);

        value.add(int123());
        checkRendering("[null,123]", value);

        value.add(symbolNotEquals());
        value.add(symbolHello());
        value.add(symbol("null"));
        checkRendering("[null,123,'!=',hello,'null']", value);

        value = (IonList) oneValue("[]");
        checkRendering("[]", value);

        value.addTypeAnnotation("an");
        checkRendering("an::[]", value);
    }


    @Test
    public void testPrintingNull()
        throws Exception
    {
        IonNull value = system().newNull();
        checkRendering("null", value);

        value.addTypeAnnotation("an");
        checkRendering("an::null", value);
    }


    @Test
    public void testPrintingSexp()
        throws Exception
    {
        IonSexp value = system().newNullSexp();
        checkRendering("null.sexp", value);

        value.add(system().newNull());
        checkRendering("(null)", value);

        value.add(int123());
        checkRendering("(null 123)", value);

        value.add(symbolNotEquals());
        value.add(symbolHello());
        value.add(symbol("null"));
        checkRendering("(null 123 != hello 'null')", value);

        myPrinter.setPrintSexpAsList(true);
        checkRendering("[null,123,'!=',hello,'null']", value);
        myPrinter.setPrintSymbolAsString(true);
        checkRendering("[null,123,\"!=\",\"hello\",\"null\"]", value);

        value = (IonSexp) oneValue("()");
        checkRendering("[]", value);
        myPrinter.setPrintSexpAsList(false);
        checkRendering("()", value);

        myPrinter.setPrintSymbolAsString(false);
        value.addTypeAnnotation("an");
        checkRendering("an::()", value);
    }


    @Test
    public void testPrintingString()
        throws Exception
    {
        IonString value = system().newNullString();
        checkRendering("null.string", value);

        value.setValue("Adam E");
        checkRendering("\"Adam E\"", value);

        value.setValue("Oh, \"Hello!\"");
        checkRendering("\"Oh, \\\"Hello!\\\"\"", value);

        value.addTypeAnnotation("an");
        checkRendering("an::\"Oh, \\\"Hello!\\\"\"", value);

        value = system().newString("Ab\u0000");
        checkRendering("\"Ab\\0\"", value);
        myPrinter.setPrintStringAsJson(true);
        checkRendering("\"Ab\\u0000\"", value);

        // TODO check escaping
    }


    @Test
    public void testPrintingStruct()
        throws Exception
    {
        IonStruct value = system().newNullStruct();
        checkRendering("null.struct", value);

        value.put("foo", system().newNull());
        checkRendering("{foo:null}", value);

        // TODO this is too strict, order shouldn't matter.
        value.put("123", system().newNull());
        checkRendering("{foo:null,'123':null}", value);

        value.add("foo", int123());
        checkRendering("{foo:null,'123':null,foo:123}", value);

        myPrinter.setPrintSymbolAsString(true);
        checkRendering("{\"foo\":null,\"123\":null,\"foo\":123}", value);

        value = (IonStruct) oneValue("{}");
        checkRendering("{}", value);

        value.addTypeAnnotation("an");
        checkRendering("an::{}", value);

        value.addTypeAnnotation("\u0007");
        value.put("A\u0000", system().newInt(12));
        checkRendering("an::'\\a'::{\"A\\0\":12}", value);
        myPrinter.setPrintStringAsJson(true);
        checkRendering("an::'\\a'::{\"A\\u0000\":12}", value);
    }


    @Test
    public void testPrintingSymbol()
        throws Exception
    {
        IonSymbol value = system().newNullSymbol();
        checkRendering("null.symbol", value);

        value.setValue("Adam E");
        checkRendering("'Adam E'", value);

        // Symbols that look like keywords.
        value.setValue("null");
        checkRendering("'null'", value);
        value.setValue("true");
        checkRendering("'true'", value);
        value.setValue("false");
        checkRendering("'false'", value);
        value.setValue("null.int");
        checkRendering("'null.int'", value);

        // Operators standalone
        value.setValue("%");
        checkRendering("'%'", value);

        value.setValue("Oh, \"Hello!\"");
        checkRendering("'Oh, \"Hello!\"'", value);
        // not: checkRendering("'Oh, \\\"Hello!\\\"'", value);

        myPrinter.setPrintSymbolAsString(true);
        checkRendering("\"Oh, \\\"Hello!\\\"\"", value);
        myPrinter.setPrintSymbolAsString(false);

        value.setValue("Oh, 'Hello there!'");
        checkRendering("'Oh, \\\'Hello there!\\\''", value);

        myPrinter.setPrintSymbolAsString(true);
        checkRendering("\"Oh, 'Hello there!'\"", value);
        myPrinter.setPrintSymbolAsString(false);

        value.addTypeAnnotation("an");
        checkRendering("an::'Oh, \\\'Hello there!\\\''", value);

        // TODO check escaping

        value = system().newSymbol("Ab\u0000");
        checkRendering("'Ab\\0'", value);
        myPrinter.setPrintSymbolAsString(true);
        checkRendering("\"Ab\\0\"", value);
        myPrinter.setPrintStringAsJson(true);
        checkRendering("\"Ab\\u0000\"", value);
        myPrinter.setPrintSymbolAsString(false);
        checkRendering("'Ab\\0'", value);
    }


    @Test
    public void testPrintingTimestamp()
        throws Exception
    {
        IonTimestamp value = system().newNullTimestamp();
        checkRendering("null.timestamp", value);

        value = (IonTimestamp) oneValue("2007-05-15T18:45-00:00");
        checkRendering("2007-05-15T18:45-00:00", value);

        value = (IonTimestamp) oneValue("2007-05-15T18:45Z");
        checkRendering("2007-05-15T18:45Z", value);

        // offset +0 shortens to Z
        value = (IonTimestamp) oneValue("2007-05-15T18:45+00:00");
        checkRendering("2007-05-15T18:45Z", value);

        value = (IonTimestamp) oneValue("2007-05-15T18:45+01:12");
        checkRendering("2007-05-15T18:45+01:12", value);

        value = (IonTimestamp) oneValue("2007-05-15T18:45-10:01");
        checkRendering("2007-05-15T18:45-10:01", value);

        value.addTypeAnnotation("an");
        checkRendering("an::2007-05-15T18:45-10:01", value);

        myPrinter.setPrintTimestampAsString(true);
        checkRendering("an::\"2007-05-15T18:45-10:01\"", value);

        myPrinter.setJsonMode();
        checkRendering("" + value.getMillis(), value);

        // TODO test printTimestampAsMillis
    }

    @Test
    public void testJsonEscapeNonBmp() throws Exception {
        // JIRA ION-33
        // JIRA ION-64
        final byte[] literal = new StringBuilder()
            .append("'''")
            .append('\uDAF7')
            .append('\uDE56')
            .append("'''")
            .toString()
            .getBytes("UTF-8")
            ;

        final IonDatagram dg = loader().load(literal);
        final StringBuilder out = new StringBuilder();
        final Printer json = new Printer();
        json.setJsonMode();
        json.print(dg.get(0), out);

        assertEquals(
            "\"\\uDAF7\\uDE56\"".toLowerCase(),
            out.toString().toLowerCase()
        );
    }
}
