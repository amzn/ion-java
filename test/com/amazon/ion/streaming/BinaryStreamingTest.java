
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

package com.amazon.ion.streaming;

import static com.amazon.ion.impl._Private_Utils.newSymbolToken;
import static com.amazon.ion.junit.IonAssert.expectField;

import com.amazon.ion.Decimal;
import com.amazon.ion.IonBinaryWriter;
import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.Timestamp;
import com.amazon.ion.impl._Private_Utils;
import com.amazon.ion.junit.IonAssert;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Date;
import java.util.Iterator;
import org.junit.Test;


public class BinaryStreamingTest
    extends IonTestCase
{
    static final boolean _debug_flag = false;


    //=========================================================================
    // Test cases

    static class TestValue {
        String   name;
        IonType  itype;
        Object   value;
        String[] annotations;

        TestValue(String tv_name, IonType tv_type, Object tv_value) {
            name = tv_name;
            itype = tv_type;
            value = tv_value;
        }

        /** TODO amzn/ion-java/issues/5 why this context? I think it will round-off values */
        static MathContext context = MathContext.DECIMAL128;
        static Decimal makeBigDecimal(double d) {
            Decimal bd = Decimal.valueOf(d, context);

            int scale = bd.scale();
            BigInteger bi = bd.unscaledValue();

            // there are only 52 significant bits in a double value, so we're going to round
            if (bi.bitLength() > 52) {
                // FIXME huh?
                if (scale != scale) throw new NumberFormatException();
            }

            return bd;
        }
        static Decimal makeBigDecimal(String v) {
            Decimal bd = Decimal.valueOf(v, context);

            int scale = bd.scale();
            BigInteger bi = bd.unscaledValue();

            // there are only 52 significant bits in a double value, so we're going to round
            if (bi.bitLength() > 52 || scale != scale) {
                throw new NumberFormatException();
            }

            return bd;
        }
        void writeValue(IonWriter wr) throws IOException {

            if (name.equals("clob_1") || name.equals("clob_2")) {
                assertTrue( true );
            }

            wr.setFieldName(name);
            switch (itype) {
                case NULL:
                    if (value == null) {
                        wr.writeNull();
                    }
                    else {
                        wr.writeNull((IonType)value);
                    }
                    break;
                case BOOL:
                    wr.writeBool((Boolean)value);
                    break;
                case INT:
                    if (value instanceof Byte) {
                        wr.writeInt((Byte)value);
                    }
                    else if (value instanceof Short) {
                        wr.writeInt((Short)value);
                    }
                    else if (value instanceof Integer) {
                        wr.writeInt((Integer)value);
                    }
                    else if (value instanceof Long) {
                        wr.writeInt((Long)value);
                    }
                    else {
                        throw new IllegalStateException("we only write Byte, Short, Integer, or Long to an IonInt");
                    }
                    break;
                case FLOAT:
                    if (value instanceof Float) {
                        wr.writeFloat((Float)value);
                    }
                    else if (value instanceof Double) {
                        wr.writeFloat((Double)value);
                    }
                    else {
                        throw new IllegalStateException("we only write Float or Double to an IonFloat");
                    }
                    break;
                case DECIMAL:
                    if (value instanceof Double) {
                        BigDecimal bd = makeBigDecimal((Double)value);
                        wr.writeDecimal(bd);
                    }
                    else if (value instanceof BigDecimal) {
                        wr.writeDecimal((BigDecimal)value);
                    }
                    else {
                        throw new IllegalStateException("we only write Double and BigDecimal to an IonDecimal");
                    }
                    break;
                case TIMESTAMP:
                    if (value instanceof Date) {
                        Timestamp ti = Timestamp.forDateZ((Date) value);
                        wr.writeTimestamp(ti);
                    }
                    else if (value instanceof String) {
                        Timestamp ti = Timestamp.valueOf((String)value);
                        wr.writeTimestamp(ti);
                    }
                    else if (value instanceof Timestamp) {
                        wr.writeTimestamp((Timestamp)value);
                    }
                    else {
                        throw new IllegalStateException("we only write Date (as is), a TtTimestamp or a String (parsed) to an IonTimestamp");
                    }
                    break;
                case STRING:
                    if (value instanceof String) {
                        wr.writeString((String)value);
                    }
                    else {
                        throw new IllegalStateException("we only write String to an IonString");
                    }
                    break;
                case SYMBOL:
                    if (value instanceof String) {
                        wr.writeSymbol((String)value);
                    }
                    else if (value instanceof Integer) {
                        wr.writeSymbolToken(newSymbolToken((Integer) value));
                    }
                    else {
                        throw new IllegalStateException("we only write String or Integer (a symbol id) to an IonSymbol");
                    }
                    break;
                case BLOB:
                    if (value instanceof byte[]) {
                        wr.writeBlob((byte[])value);
                    }
                    else {
                        throw new IllegalStateException("we only write byte arrays ( byte[] ) to an IonBlob");
                    }
                    break;
                case CLOB:
                    if (value instanceof byte[]) {
                        wr.writeClob((byte[])value);
                    }
                    else {
                        throw new IllegalStateException("we only write byte arrays ( byte[] ) to an IonClob");
                    }
                    break;
                case STRUCT:
                case LIST:
                case SEXP:
                    throw new IllegalStateException("the value writer can't write complex types");
                default:
                    throw new IllegalStateException("the value writer can't write Datagram");
            }
            return;
        }
        void readAndTestValue(IonReader r) throws IOException {

            IonType t = r.next();
            IonAssert.expectField(r, name);

            if ( itype.equals(IonType.NULL) ) {
                // nulls are typed so we test them differently
                if (value != null) {
                    assertEquals(value, t);
                }
                else {
                    assertEquals(IonType.NULL, t);
                }
            }
            else {
                assertEquals(itype, t);
            }

            switch (itype) {
                case NULL:
                    break;
                case BOOL:
                    assertTrue( ((Boolean)value).equals(r.booleanValue()));
                    break;
                case INT:
                    if (value instanceof Byte) {
                        assertTrue( ((Byte)value).equals(r.intValue()) );
                    }
                    else if (value instanceof Short) {
                        assertTrue( ((Short)value).equals(r.intValue()) );
                    }
                    else if (value instanceof Integer) {
                        assertTrue( ((Integer)value).equals(r.intValue()) );
                    }
                    else if (value instanceof Long) {
                        assertTrue( ((Long)value).equals(r.longValue()) );
                    }
                    else {
                        throw new IllegalStateException("we only test Byte, Short, Integer, or Long values against an IonInt");
                    }
                    break;
                case FLOAT:
                    if (value instanceof Float) {
                        double d = r.doubleValue();
                        assertTrue( ((Float)value).equals( (float)d ) );
                    }
                    else if (value instanceof Double) {
                        double d = r.doubleValue();
                        assertTrue( ((Double)value).equals( d ) );
                    }
                    else {
                        throw new IllegalStateException("we only test Float or Double to an IonFloat");
                    }
                    break;
                case DECIMAL:
                    Decimal dec1 = null;
                    Decimal dec2 = r.decimalValue();
                    if (value instanceof Double) {
                        dec1 = makeBigDecimal((Double)value);
                    }
                    else if (value instanceof Decimal) {
                        dec1 = (Decimal)value;
                    }
                    else {
                        throw new IllegalStateException("we only test Double and Decimal to an IonDecimal");
                    }
                    assertPreciselyEquals(dec1, dec2);
                    break;
                case TIMESTAMP:
                    Timestamp actual = r.timestampValue();

                    if (value instanceof Date) {
                        assertEquals(value, actual.dateValue());
                        assertEquals(Timestamp.UTC_OFFSET, actual.getLocalOffset());
                    }
                    else if (value instanceof String) {
                        Timestamp ti2 = Timestamp.valueOf((String)value);
                        assertEquals(ti2, actual);
                    }
                    else if (value instanceof Timestamp) {
                        Timestamp ti2 = (Timestamp)value;
                        assertEquals(ti2, actual);
                    }
                    else {
                        throw new IllegalStateException("we only test Date (as is), a TtTimestamp, or a String (parsed) to an IonTimestamp");
                    }
                    break;
                case STRING:
                    if (value instanceof String) {
                        assertTrue( ((String)value).equals(r.stringValue()) );
                    }
                    else {
                        throw new IllegalStateException("we only test String to an IonString");
                    }
                    break;
                case SYMBOL:
                    if (value instanceof String) {
                        assertTrue( ((String)value).equals( r.stringValue()) );
                    }
                    else if (value instanceof Integer) {
                        assertTrue( ((Integer)value).equals( r.symbolValue().getSid()) );
                    }
                    else {
                        throw new IllegalStateException("we only test String or Integer (a symbol id) to an IonSymbol");
                    }
                    break;
                case BLOB:

                    if (value instanceof byte[]) {
                        byte[] b1 = (byte[])value;
                        byte[] b2 = r.newBytes();
                        assertArrayEquals(b1, b2);
                    }
                    else {
                        throw new IllegalStateException("we only test byte arrays ( byte[] ) to an IonBlob");
                    }
                    break;
                case CLOB:
                    if (value instanceof byte[]) {
                        byte[] c1 = (byte[])value;
                        byte[] c2 = r.newBytes();
                        assertArrayEquals(c1, c2);
                    }
                    else {
                        throw new IllegalStateException("we only test byte arrays ( byte[] ) to an IonClob");
                    }
                    break;
                case STRUCT:
                case LIST:
                case SEXP:
                    throw new IllegalStateException("the value reader can't read complex types");
                default:
                    throw new IllegalStateException("the value reader can't read Datagram");
            }
        }
    }

    @Test
    public void testAllValues()
    throws Exception
    {
        IonBinaryWriter wr = system().newBinaryWriter();
        byte[] buffer = null;

        byte[] _testbytes1 = new byte[5];
        byte[] _testbytes2 = new byte[10000];

        for (int ii=0; ii<5; ii++) {
            _testbytes1[ii] = (byte)('0' + ii);
        }
        for (int ii=0; ii<_testbytes2.length; ii++) {
            _testbytes2[ii] = (byte)(ii & 0xff);
        }

        TestValue[] testvalues = {
               // new TestValue( , , ),
/*
new TestValue("Null",          IonType.NULL, null),
new TestValue("Null.null",     IonType.NULL, IonType.NULL),
new TestValue("Null.bool",     IonType.NULL, IonType.BOOL),
new TestValue("Null.int",      IonType.NULL, IonType.INT),
new TestValue("Null.float",    IonType.NULL, IonType.FLOAT),
new TestValue("Null.decimal",  IonType.NULL, IonType.DECIMAL),
new TestValue("Null.timestamp",IonType.NULL, IonType.TIMESTAMP),
*/

               new TestValue("Null",          IonType.NULL, null),
               new TestValue("Null.null",     IonType.NULL, IonType.NULL),
               new TestValue("Null.bool",     IonType.NULL, IonType.BOOL),

               new TestValue("Null.int",      IonType.NULL, IonType.INT),
               new TestValue("Null.float",    IonType.NULL, IonType.FLOAT),
               new TestValue("Null.decimal",  IonType.NULL, IonType.DECIMAL),

               new TestValue("Null.timestamp",IonType.NULL, IonType.TIMESTAMP),
               new TestValue("Null.string",   IonType.NULL, IonType.STRING),
               new TestValue("Null.symbol",   IonType.NULL, IonType.SYMBOL),

               new TestValue("Null.clob",     IonType.NULL, IonType.CLOB),
               new TestValue("Null.blob",     IonType.NULL, IonType.BLOB),
               new TestValue("Null.list",     IonType.NULL, IonType.LIST),
               new TestValue("Null.sexp",     IonType.NULL, IonType.SEXP),
               new TestValue("Null.struct",   IonType.NULL, IonType.STRUCT),

               new TestValue("bool_true",     IonType.BOOL, Boolean.TRUE),
               new TestValue("bool_false",    IonType.BOOL, Boolean.FALSE),

               new TestValue("int0",          IonType.INT, new Integer(0) ),
               new TestValue("intneg1",       IonType.INT, new Integer(-1) ),
               new TestValue("int1",          IonType.INT, new Integer(1) ),
               new TestValue("intneg32766",   IonType.INT, new Integer(-32766) ),
               new TestValue("int32767",      IonType.INT, new Integer(32767) ),
               new TestValue("int-2g",        IonType.INT, new Integer(-2000000000) ),
               new TestValue("int2g",         IonType.INT, new Integer(2000000000) ),
               new TestValue("intmin",        IonType.INT, Integer.MIN_VALUE ),
               new TestValue("intmax",        IonType.INT, Integer.MAX_VALUE ),

               new TestValue("long0",         IonType.INT, new Long(0L) ),
               new TestValue("long-1",        IonType.INT, new Long(-1L) ),
               new TestValue("long1",         IonType.INT, new Long(1L) ),
               new TestValue("long-4g",       IonType.INT, new Long(-4000000000L) ),
               new TestValue("long4g",        IonType.INT, new Long(4000000000L) ),
               new TestValue("longmin",       IonType.INT, new Long(Long.MIN_VALUE + 1) ),  // we don't reach all the way to MIN_VALUE since we have to negate it for writing (to get the positive value)
               new TestValue("longmin2",      IonType.INT, new Long(-Long.MAX_VALUE) ),
               new TestValue("longmax",       IonType.INT, Long.MAX_VALUE ),

               new TestValue("float_0",       IonType.FLOAT, new Float(0.0) ),
               new TestValue("float_n0",      IonType.FLOAT, new Float(-0.0) ),
               new TestValue("float_a",       IonType.FLOAT, new Float(-1.0) ),
               new TestValue("float_b",       IonType.FLOAT, new Float(1.0) ),
               new TestValue("float_c",       IonType.FLOAT, new Float(-1e10) ),
               new TestValue("float_d",       IonType.FLOAT, new Float(1e-10) ),
               new TestValue("float_e",       IonType.FLOAT, new Float(0.1) ),
               new TestValue("float_f",       IonType.FLOAT, new Float(-0.1) ),
               new TestValue("float_g",       IonType.FLOAT, Float.MIN_VALUE ),
               new TestValue("float_h",       IonType.FLOAT, Float.MAX_VALUE ),
               new TestValue("float_i",       IonType.FLOAT, Float.NaN ),
               new TestValue("float_j",       IonType.FLOAT, Float.NEGATIVE_INFINITY ),
               new TestValue("float_k",       IonType.FLOAT, Float.POSITIVE_INFINITY ),

               new TestValue("double_0",      IonType.FLOAT, new Double(0.0) ),
               new TestValue("double_n0",     IonType.FLOAT, new Double(-0.0) ),
               new TestValue("double_a",      IonType.FLOAT, new Double(-1.0) ),
               new TestValue("double_b",      IonType.FLOAT, new Double(1.0) ),
               new TestValue("double_c",      IonType.FLOAT, new Double(-1e10) ),
               new TestValue("double_d",      IonType.FLOAT, new Double(1e-10) ),
               new TestValue("double_e",      IonType.FLOAT, new Double(0.1) ),
               new TestValue("double_f",      IonType.FLOAT, new Double(-0.1) ),
               new TestValue("double_g",      IonType.FLOAT, Double.MIN_VALUE ),
               new TestValue("double_h",      IonType.FLOAT, Double.MAX_VALUE ),
               new TestValue("double_i",      IonType.FLOAT, Double.NaN ),
               new TestValue("double_j",      IonType.FLOAT, Double.NEGATIVE_INFINITY ),
               new TestValue("double_k",      IonType.FLOAT, Double.POSITIVE_INFINITY ),

               new TestValue("bdd_0",         IonType.DECIMAL, new Double(0.0) ),
               new TestValue("bdd_n0",        IonType.DECIMAL, new Double(-0.0) ),
               new TestValue("bdd_a",         IonType.DECIMAL, new Double(-1.0) ),
               new TestValue("bdd_b",         IonType.DECIMAL, new Double(1.0) ),
               new TestValue("bdd_c",         IonType.DECIMAL, new Double(-1e10) ),
               new TestValue("bdd_d",         IonType.DECIMAL, new Double(1e-10) ),
               new TestValue("bdd_e",         IonType.DECIMAL, new Double(0.1) ),
               new TestValue("bdd_f",         IonType.DECIMAL, new Double(-0.1) ),
               new TestValue("bdd_g",         IonType.DECIMAL, Double.MIN_VALUE ),
               new TestValue("bdd_h",         IonType.DECIMAL, Double.MAX_VALUE ),
//                   new TestValue("bdd_i",         IonType.DECIMAL, Double.NaN ),
//                   new TestValue("bdd_j",         IonType.DECIMAL, Double.NEGATIVE_INFINITY ),
//                   new TestValue("bdd_k",         IonType.DECIMAL, Double.POSITIVE_INFINITY ),

               new TestValue("bdbd_0",        IonType.DECIMAL, TestValue.makeBigDecimal("0.0") ),
               new TestValue("bdbd_n0",       IonType.DECIMAL, TestValue.makeBigDecimal("-0.00") ),
               new TestValue("bdbd_a",        IonType.DECIMAL, TestValue.makeBigDecimal("-1.0") ),
               new TestValue("bdbd_b",        IonType.DECIMAL, TestValue.makeBigDecimal("1.0") ),
               new TestValue("bdbd_c",        IonType.DECIMAL, TestValue.makeBigDecimal("-1e10") ),
               new TestValue("bdbd_d",        IonType.DECIMAL, TestValue.makeBigDecimal("1e-10") ),
               new TestValue("bdbd_e",        IonType.DECIMAL, TestValue.makeBigDecimal("0.1") ),
               new TestValue("bdbd_f",        IonType.DECIMAL, TestValue.makeBigDecimal("-0.1") ),
               new TestValue("bdbd_g",        IonType.DECIMAL, TestValue.makeBigDecimal("0.10") ),
               new TestValue("bdbd_h",        IonType.DECIMAL, TestValue.makeBigDecimal("-0.10") ),
               new TestValue("bdbd_i",        IonType.DECIMAL, TestValue.makeBigDecimal("100") ),
               new TestValue("bdbd_j",        IonType.DECIMAL, TestValue.makeBigDecimal("-100.000") ),

//                   new TestValue("date_d1",       IonType.TIMESTAMP, new Date("1-23-2008") ),
//                   new TestValue("date_d2",       IonType.TIMESTAMP, new Date("1-23-2008T12:53") ),
//                   new TestValue("date_d3",       IonType.TIMESTAMP, new Date("1-23-2008T12:53:24") ),

//                   new TestValue("date_t1",       IonType.TIMESTAMP, IonTokenReader.Type.timeinfo.parse("2008-02Z") ),
               new TestValue("date_t2",       IonType.TIMESTAMP, Timestamp.valueOf("2008-02-15") ),
               new TestValue("date_t3",       IonType.TIMESTAMP, Timestamp.valueOf("0001-01-01") ),
               new TestValue("date_t4",       IonType.TIMESTAMP, Timestamp.valueOf("9999-12-31") ),

               new TestValue("date_t5",       IonType.TIMESTAMP, Timestamp.valueOf("2008-02-15T12:59:59.100000-03:45") ),
               new TestValue("date_t6",       IonType.TIMESTAMP, Timestamp.valueOf("0001-01-01T00:00:00.000000-00:00") ),
               new TestValue("date_t7",       IonType.TIMESTAMP, Timestamp.valueOf("9999-12-31T23:59:59.999999+01:00") ),

               new TestValue("string_1",       IonType.STRING, ""),
               new TestValue("string_2",       IonType.STRING, " "),
               new TestValue("string_3",       IonType.STRING, "abcde"),
               new TestValue("string_4",       IonType.STRING, "123456789012"),
               new TestValue("string_5",       IonType.STRING, "1234567890123"),
               new TestValue("string_6",       IonType.STRING, "12345678901234"),
               new TestValue("string_7",       IonType.STRING, "123456789012345"),
               new TestValue("string_8",       IonType.STRING, "\0\u001f\uffff"),

//                   new TestValue("symbol_1",       IonType.SYMBOL, ""),
               new TestValue("symbol_2",       IonType.SYMBOL, " "),
               new TestValue("symbol_3",       IonType.SYMBOL, "abcde"),
               new TestValue("symbol_4",       IonType.SYMBOL, "123456789012"),
               new TestValue("symbol_5",       IonType.SYMBOL, "1234567890123"),
               new TestValue("symbol_6",       IonType.SYMBOL, "12345678901234"),
               new TestValue("symbol_7",       IonType.SYMBOL, "123456789012345"),
               new TestValue("symbol_8",       IonType.SYMBOL, "\0\u001f\uffff"),

               new TestValue("symbol_9",       IonType.SYMBOL, new Integer(1) ),

               new TestValue("clob_1",       IonType.CLOB, _testbytes1),
               new TestValue("clob_2",       IonType.CLOB, _testbytes2),

               new TestValue("blob_1",       IonType.BLOB, _testbytes1),
               new TestValue("blob_2",       IonType.BLOB, _testbytes2),

        };

        try {
            // we don't really need the struct, but if we use it we get to
            // label all the values
            wr.stepIn(IonType.STRUCT);

            for (TestValue tv : testvalues) {
                tv.writeValue(wr);
            }

            wr.stepOut();
            buffer = wr.getBytes();
        }
        catch (IOException e) {
            throw new Exception(e);
        }

        IonReader r = getStreamingMode().newIonReader(system().getCatalog(), buffer);
        IonType t;

        t = r.next();
        assertTrue( t.equals(IonType.STRUCT) );
        r.stepIn();

        for (TestValue tv : testvalues) {
            tv.readAndTestValue(r);
        }
        r.close();
    }

    @Test
    public void testValue1()
    throws Exception
    {
        String s =
            "item_view::{item_id:\"B00096H8Q4\",marketplace_id:2,"
            +"product:{item_name:["
            +"{value:'''Method 24CT Leather Wipes''',lang:EN_CA},"
            +"{value:'''Method 24CT Chiffons de Cuir''',lang:FR_CA}],"
            +"list_price:{value:18.23,unit:EUR},}"
            +",index_suppressed:true,"
            +"offline_store_only:true,version:2,}";
        IonReader ir = system().newReader(s);
        IonBinaryWriter wr = system().newBinaryWriter();
        wr.writeValues(ir);
        byte[] buffer = wr.getBytes();
        dumpBuffer(buffer, buffer.length);
    }

    @Test
    public void testValue2()
    throws Exception
    {
        String s =
            "item_view::{item_id:\"B00096H8Q4\",marketplace_id:2,"
            +"product:{item_name:["
            +"{value:'''Method 24CT Leather Wipes''',lang:EN_CA},"
            +"{value:'''Method 24CT Chiffons de Cuir''',lang:FR_CA}],"
            +"list_price:{value:18.23,unit:EUR},}"
            +",index_suppressed:true,"
            +"offline_store_only:true,version:2,}";
        IonSystem sys = system();
        IonDatagram dg = sys.getLoader().load(s);
        IonValue v = dg.get(0);
        IonValue v2 = ((IonStruct)v).get("offline_store_only");
        SymbolTable sym = v.getSymbolTable();
        assert v2.getSymbolTable() == sym;
        IonReader ir = system().newReader(s);

        Iterator<String> symbols = sym.iterateDeclaredSymbolNames();
        SymbolTable u = system().newSharedSymbolTable("items", 1, symbols);
        IonBinaryWriter wr = system().newBinaryWriter(u);

        wr.writeValues(ir);
        byte[] buffer = wr.getBytes();
        dumpBuffer(buffer, buffer.length);

        ir = getStreamingMode().newIonReader(system().getCatalog(), buffer);
        wr = system().newBinaryWriter(u);
        wr.writeValues(ir);
        buffer = wr.getBytes();
        dumpBuffer(buffer, buffer.length);
    }
    void dumpBuffer(byte[] buffer, int len)
    {
        if (!_debug_flag) return;
        for (int ii=0; ii<len; ii++) {
            int b = ((int)buffer[ii]) & 0xff;
            if ((ii & 0xf) == 0) {
                System.out.println();
                String x = "     "+ii;
                if (x.length() > 5)  x = x.substring(x.length() - 6);
                System.out.print(x+": ");
            }
            String y = "00"+Integer.toHexString(b);
            y = y.substring(y.length() - 2);
            System.out.print(y+" ");
        }
        System.out.println();

        for (int ii=0; ii<len; ii++) {
            int b = ((int)buffer[ii]) & 0xff;
            if ((ii & 0xf) == 0) {
                System.out.println();
                String x = "     "+ii;
                if (x.length() > 5)  x = x.substring(x.length() - 6);
                System.out.print(x+": ");
            }
            String y = "  " + (char)((b >= 32 && b < 128) ? b : ' ');
            y = y.substring(y.length() - 2);
            System.out.print(y+" ");
        }
        System.out.println();
    }

    @Test
    public void testBoolValue()
        throws Exception
    {
        IonBinaryWriter wr = system().newBinaryWriter();
        byte[] buffer = null;

        try {
            wr.stepIn(IonType.STRUCT);
            wr.setFieldName("Foo");
            wr.writeBool(true);
            wr.stepOut();
            buffer = wr.getBytes();
        }
        catch (IOException e) {
            throw new Exception(e);
        }

        IonReader ir = getStreamingMode().newIonReader(system().getCatalog(), buffer);
        IonType t = ir.next();
        if (t != null) {
            ir.stepIn();
            t = ir.next();
            while (t != null) {
                String name = ir.getFieldName();
                boolean value = ir.booleanValue();
                assertTrue( value );
                if (BinaryStreamingTest._debug_flag) {
                    System.out.println(t + " " + name +": " + value);
                }
                t = ir.next();
            }
        }
        ir.close();
    }


    @Test
    public void testTwoMagicCookies() throws Exception {
        IonBinaryWriter wr = system().newBinaryWriter();
        byte[] buffer = null;

        try {
            wr.stepIn(IonType.STRUCT);
            wr.setFieldName("Foo");
            wr.addTypeAnnotation("boolean");
            wr.writeBool(true);
            wr.stepOut();
            buffer = wr.getBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        byte[] doublebuffer = new byte[buffer.length * 2];
        System.arraycopy(buffer, 0, doublebuffer, 0, buffer.length);
        System.arraycopy(buffer, 0, doublebuffer, buffer.length, buffer.length);

        IonReader ir = getStreamingMode().newIonReader(system().getCatalog(), doublebuffer);

        // first copy
        assertEquals(IonType.STRUCT, ir.next());
        ir.stepIn();
        assertEquals(IonType.BOOL, ir.next());
        expectField(ir, "Foo");
        //assertEquals(ir.getAnnotations(), new String[] { "boolean" });
        String[] annotations = ir.getTypeAnnotations();
        assertTrue(annotations != null && annotations.length == 1);
        if (annotations != null) { // just to shut eclipse up, we already tested this above
            assertTrue("boolean".equals(annotations[0]));
        }
        assertTrue(ir.booleanValue());
        ir.stepOut();

        // second copy
        assertEquals(IonType.STRUCT, ir.next());
        ir.stepIn();
        assertEquals(IonType.BOOL, ir.next());
        expectField(ir, "Foo");
        annotations = ir.getTypeAnnotations();
        assertNotNull(annotations);
        assertEquals(1, annotations.length);
        assertEquals("boolean", annotations[0]);
        assertEquals(true, ir.booleanValue());
        ir.stepOut();

        assertEquals(null, ir.next());
        ir.close();
    }


    @Test
    public void testBoolean() throws Exception {
        IonBinaryWriter wr = system().newBinaryWriter();
        byte[] buffer = null;

        try {
            wr.stepIn(IonType.STRUCT);
            wr.setFieldName("Foo");
            wr.addTypeAnnotation("boolean");
            wr.writeBool(true);
            wr.stepOut();
            buffer = wr.getBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        IonReader ir = getStreamingMode().newIonReader(system().getCatalog(), buffer);
        IonType t = ir.next();
        if (t != null) {
            ir.stepIn();
            t = ir.next();
            while (t != null) {
                assertEquals(t, IonType.BOOL);
                expectField(ir, "Foo");
                //assertEquals(ir.getAnnotations(), new String[] { "boolean" });
                String[] annotations = ir.getTypeAnnotations();
                assertTrue(annotations != null && annotations.length == 1);
                if (annotations != null) { // just to shut eclipse up, we already tested this above
                    assertTrue("boolean".equals(annotations[0]));
                }
                assertEquals(ir.booleanValue(), true);
                t = ir.next();
            }
        }
        ir.close();
    }

    //Test Sample map.
    //{hello=true, Almost Done.=true, This is a test String.=true, 12242.124598129=12242.124598129, Something=null, false=false, true=true, long=9326, 12=-12}
    @Test
    public void testSampleMap() throws Exception {
        IonBinaryWriter wr = system().newBinaryWriter();
        byte[] buffer = null;

        try {
            wr.stepIn(IonType.STRUCT);

            wr.setFieldName("hello");
            wr.writeBool(true);

            wr.setFieldName("Almost Done.");
            wr.writeBool(true);

            wr.setFieldName("This is a test String.");
            wr.writeBool(true);

            wr.setFieldName("12242.124598129");
            wr.writeFloat(12242.124598129);

            wr.setFieldName("Something");
            wr.writeNull();

            wr.setFieldName("false");
            wr.writeBool(false);

            wr.setFieldName("true");
            wr.writeBool(true);

            wr.setFieldName("long");
            wr.writeInt((long) 9326);

            wr.setFieldName("12");
            wr.writeInt(-12);

            wr.stepOut();
            buffer = wr.getBytes();
        } catch (IOException e) {

            throw new RuntimeException(e);
        }

        IonReader ir = getStreamingMode().newIonReader(system().getCatalog(), buffer);
        if (ir.next() != null) {
            ir.stepIn();
            while (ir.next() != null) {
                assertEquals(ir.getType(), IonType.BOOL);
                expectField(ir, "hello");
                assertEquals(ir.booleanValue(), true);

                assertEquals(ir.next(), IonType.BOOL);
                expectField(ir, "Almost Done.");
                assertEquals(ir.booleanValue(), true);

                assertEquals(ir.next(), IonType.BOOL);
                expectField(ir, "This is a test String.");
                assertEquals(ir.booleanValue(), true);

                assertEquals(ir.next(), IonType.FLOAT);
                expectField(ir, "12242.124598129");
                assertEquals(ir.doubleValue(), 12242.124598129);

                assertEquals(ir.next(), IonType.NULL);
                expectField(ir, "Something");
                assertTrue(ir.isNullValue());
                // not:
                //assertEquals(ir.getValueAsString(), null);
//                assertEquals(ir.valueToString(), "null");

                assertEquals(ir.next(), IonType.BOOL);
                expectField(ir, "false");
                assertEquals(ir.booleanValue(), false);

                assertEquals(ir.next(), IonType.BOOL);
                expectField(ir, "true");
                assertEquals(ir.booleanValue(), true);

                assertEquals(ir.next(), IonType.INT);
                expectField(ir, "long");
                assertEquals(ir.longValue(), 9326L);

                assertEquals(ir.next(), IonType.INT);
                expectField(ir, "12");
                assertEquals(ir.intValue(), -12);
            }
        }
        ir.close();
    }

    @Test
    public void testBenchmarkDirect() throws IOException {

        byte[] bytes ;

        IonBinaryWriter wr = system().newBinaryWriter();
        wr.stepIn(IonType.STRUCT);

        wr.setFieldName("12");
        wr.addTypeAnnotation(int.class.getCanonicalName());
        wr.writeInt(-12);

        wr.setFieldName("12242.124598129");
        wr.addTypeAnnotation(double.class.getCanonicalName());
        wr.writeFloat(12242.124598129);

        wr.setFieldName("Almost Done.");
        wr.addTypeAnnotation(boolean.class.getCanonicalName());
        wr.writeBool(true);

        wr.setFieldName("This is a test String.");
        wr.addTypeAnnotation(boolean.class.getCanonicalName());
        wr.writeBool(true);

        wr.setFieldName("false");
        wr.addTypeAnnotation(boolean.class.getCanonicalName());
        wr.writeBool(false);

        wr.setFieldName("long");
        wr.addTypeAnnotation(long.class.getCanonicalName());
        wr.writeInt((long) 9326);

        wr.setFieldName("true");
        wr.addTypeAnnotation(boolean.class.getCanonicalName());
        wr.writeBool(true);

        wr.stepOut();

        bytes = wr.getBytes();

        IonReader ir = getStreamingMode().newIonReader(system().getCatalog(), bytes);
        assertEquals(IonType.STRUCT, ir.next());
        ir.stepIn();

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.INT);
        expectField(ir, "12");
        assertEquals(ir.intValue(), -12);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.FLOAT);
        expectField(ir, "12242.124598129");
        assertEquals(ir.doubleValue(), 12242.124598129);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.BOOL);
        expectField(ir, "Almost Done.");
        assertEquals(ir.booleanValue(), true);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.BOOL);
        expectField(ir, "This is a test String.");
        assertEquals(ir.booleanValue(), true);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.BOOL);
        expectField(ir, "false");
        assertEquals(ir.booleanValue(), false);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.INT);
        expectField(ir, "long");
        assertEquals(ir.longValue(), 9326L);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertTrue(ir.hasNext());
        assertEquals(ir.next(), IonType.BOOL);
        expectField(ir, "true");
        assertEquals(ir.booleanValue(), true);

        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertFalse(ir.hasNext());
        assertEquals(null, ir.next());
        ir.stepOut();
        if (! _Private_Utils.READER_HASNEXT_REMOVED) assertFalse(ir.hasNext());
        assertEquals(null, ir.next());

        ir.close();
    }
}
