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

import com.amazon.ion.impl._Private_IonSystem;
import com.amazon.ion.junit.Injected;
import com.amazon.ion.junit.Injected.Inject;
import com.amazon.ion.junit.IonAssert;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.SimpleCatalog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

/**
 * Base class with helpers for Ion unit tests.
 */
@RunWith(Injected.class)
public abstract class IonTestCase
    extends Assert
{
    private static final File ION_TESTS_PATH = new File("ion-tests");
    private static final File ION_TESTS_IONTESTDATA_PATH = new File(ION_TESTS_PATH, "iontestdata");
    private static final File ION_TESTS_BULK_PATH = new File(ION_TESTS_PATH, "bulk");

    static
    {
        if (!ION_TESTS_IONTESTDATA_PATH.exists())
        {
            throw new RuntimeException("Cannot locate test data directory.");
        }
    }

    protected enum StreamingMode {
        NEW_STREAMING() {
            @Override
            public IonReader newIonReader(IonCatalog catalog, InputStream inputStream) {
                return IonReaderBuilder.standard().withCatalog(catalog).build(inputStream);
            }

            @Override
            public IonReader newIonReader(IonCatalog catalog, byte[] data) {
                return IonReaderBuilder.standard().withCatalog(catalog).build(data);
            }
        },
        NEW_STREAMING_INCREMENTAL() {
            @Override
            public IonReader newIonReader(IonCatalog catalog, InputStream inputStream) {
                return IonReaderBuilder.standard().withCatalog(catalog).withIncrementalReadingEnabled(true).build(inputStream);
            }

            @Override
            public IonReader newIonReader(IonCatalog catalog, byte[] data) {
                return IonReaderBuilder.standard().withCatalog(catalog).withIncrementalReadingEnabled(true).build(data);
            }
        };

        public abstract IonReader newIonReader(IonCatalog catalog, InputStream inputStream);
        public abstract IonReader newIonReader(IonCatalog catalog, byte[] data);
    }

    // Using an enum makes the test names more understandable than a boolean.
    protected enum StreamCopySpeed { COPY_OPTIMIZED, COPY_NON_OPTIMIZED }

    @Inject("streamingMode")
    public static final StreamingMode[] STREAMING_DIMENSION = StreamingMode.values();

    /**
     * Flag on whether IonSystems generated is
     * {@link IonSystemBuilder#isStreamCopyOptimized()}.
     * <p>
     * This is false by default. Keep this in sync with {@link IonSystemBuilder}!
     */
    private boolean                     myStreamCopyOptimized = false;
    private StreamingMode               myStreamingMode;

    private static boolean              ourSystemPropertiesLoaded = false;
    protected SimpleCatalog             myCatalog;
    protected _Private_IonSystem        mySystem;
    protected IonLoader                 myLoader;

    @Rule
    public ExpectedException myExpectedException = ExpectedException.none();

    @After
    public void tearDown()
        throws Exception
    {
        myCatalog = null;
        mySystem = null;
    }


    //=========================================================================
    // Setters/Getters for injected values

    public StreamingMode getStreamingMode()
    {
        return myStreamingMode;
    }

    public void setStreamingMode(StreamingMode mode)
    {
        myStreamingMode = mode;
    }

    public boolean isStreamCopyOptimized()
    {
        return myStreamCopyOptimized;
    }

    public void setCopySpeed(StreamCopySpeed speed)
    {
        myStreamCopyOptimized = (speed == StreamCopySpeed.COPY_OPTIMIZED);
    }

    //=========================================================================
    // Access to test data

    public static synchronized void loadSystemProperties()
    {
        if (! ourSystemPropertiesLoaded)
        {
            InputStream stream =
                IonTestCase.class.getResourceAsStream("/system.properties");

            if (stream != null)
            {
                Properties props = new Properties();

                try
                {
                    props.load(stream);

                    Iterator entries = props.entrySet().iterator();
                    while (entries.hasNext())
                    {
                        Map.Entry entry = (Map.Entry) entries.next();

                        String key = (String) entry.getKey();

                        // Command-line values override system.properties
                        if (System.getProperty(key) == null)
                        {
                            System.setProperty(key, (String) entry.getValue());
                        }
                    }
                }
                catch (IOException e)
                {
                    synchronized (System.out)
                    {
                        System.out.println("Caught exception while loading system.properties:");
                        e.printStackTrace(System.out);
                    }
                }
            }

            ourSystemPropertiesLoaded = true;
        }
    }


    public static String requireSystemProperty(String prop)
    {
        loadSystemProperties();

        String value = System.getProperty(prop);
        if (value == null)
        {
            value = System.getenv(prop);
        }
        if (value == null) {
            String message = "Missing required system property: " + prop;
            throw new IllegalStateException(message);
        }
        return value;
    }



    public static File getProjectHome()
    {
        String basedir = System.getProperty("user.dir");
        return new File(basedir);
    }

    public static File getProjectFile(String path)
    {
        return new File(getProjectHome(), path);
    }

    /**
     * Gets a {@link File} contained in the IonTests "iontestdata" data
     * directory.
     *
     * @param path is relative to the "iontestdata" directory.
     */
    public static File getTestdataFile(String path)
    {
        return new File(ION_TESTS_IONTESTDATA_PATH, path);
    }

    /**
     * Gets a {@link File} contained in the IonTests "bulk" data directory.
     *
     * @param path is relative to the "bulk" directory
     */
    public static File getBulkTestdataFile(String path)
    {
        return new File(ION_TESTS_BULK_PATH, path);
    }

    /**
     * Reads the content of an Ion file contained in the test data suite.
     *
     * @param path is relative to the {@code testdata} directory.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public IonDatagram loadTestFile(String path)
        throws IOException
    {
        File file = getTestdataFile(path);
        return load(file);
    }

    public IonDatagram load(File ionFile)
        throws IonException, IOException
    {
        IonLoader loader = loader();
        IonDatagram dg;
        if (ionFile.getName().endsWith(".ion")) {
            dg = loader.load(ionFile);
        } else {
            IonReader reader = myStreamingMode.newIonReader(system().getCatalog(), new FileInputStream(ionFile));
            dg = loader.load(reader);
            reader.close();
        }

        // Flush out any encoding problems in the data.
        forceDeepMaterialization(dg);

        return dg;
    }

    /**
     * No op, removal pending the binary backed impl removal
     */
    public void forceDeepMaterialization(IonValue value)
    {
        // No op
    }

    //=========================================================================
    // Fixture Helpers

    /**
     * @return
     *          the singleton IonSystem, stream-copy optimized depending on the injected
     *          {@link #myStreamCopyOptimized}.
     */
    protected _Private_IonSystem system()
    {
        if (mySystem == null)
        {
            mySystem = newSystem(myCatalog);
        }
        return mySystem;
    }

    /**
     * Returns a new IonSystem for each call, using the passed in IonCatalog
     * to build the IonSystem.
     *
     * @param catalog
     *          the catalog to use when building the IonSystem
     *
     * @return
     *          a new IonSystem instance, stream-copy optimized depending on the
     *          injected {@link #myStreamCopyOptimized}.
     */
    protected _Private_IonSystem newSystem(IonCatalog catalog)
    {
        IonSystemBuilder b = IonSystemBuilder.standard().withCatalog(catalog);

        b.withStreamCopyOptimized(myStreamCopyOptimized);

        IonSystem system = b.build();
        return (_Private_IonSystem) system;
    }

    protected SimpleCatalog catalog()
    {
        if (myCatalog == null)
        {
            myCatalog = (SimpleCatalog) system().getCatalog();
        }
        return myCatalog;
    }

    protected IonLoader loader()
    {
        if (myLoader == null)
        {
            myLoader = system().getLoader();
        }
        return myLoader;
    }


    /**
     * Constructs an Ion-encoded string literal with a single escaped character.
     * For example, <code>makeEscapedCharString('n')</code> returns
     * (Java) <code>"\"\\n\""</code>, equivalent to Ion <code>"\n"</code>.
     */
    protected static String makeEscapedCharString(char escape)
    {
        final String QT = "\"";
        final String BS = "\\";

        String result = QT + BS + escape + QT;
        return result;
    }

    protected int systemMaxId()
    {
        return system().getSystemSymbolTable().getMaxId();
    }


    //=========================================================================
    // Encoding helpers

    /**
     * Gets bytes of datagram and loads into a new one.
     */
    public IonDatagram reload(IonDatagram dg)
    {
        byte[] bytes = dg.getBytes();
        checkBinaryHeader(bytes);

        IonDatagram dg1 = loader().load(bytes);
        return dg1;
    }

    /**
     * Put value into a datagram, get bytes, and reload to single value.
     * Result should be equivalent.
     */
    public IonValue reload(IonValue value)
    {
        IonDatagram dg = system().newDatagram(value);
        byte[] bytes = dg.getBytes();
        checkBinaryHeader(bytes);

        try {
            dg = loader().load(bytes);
        } catch (final IonException e) {
            final String hex = BinaryTest.bytesToHex(bytes);
            throw new IonException("Bad bytes: " + hex, e);
        }
        assertEquals(1, dg.size());
        return dg.get(0);
    }



    /**
     * Materializes a shared symtab using
     * {@link IonSystem#newSharedSymbolTable(IonReader)}.
     * No catalog registration is performed.
     */
    public SymbolTable loadSharedSymtab(String serializedSymbolTable)
    {
        IonReader reader = system().newReader(serializedSymbolTable);
        SymbolTable shared = system().newSharedSymbolTable(reader);
        assertTrue(shared.isSharedTable());
        return shared;
    }

    /**
     * Materializes a shared symtab and registeres it in the default
     * catalog.
     *
     * @see #catalog()
     */
    public SymbolTable registerSharedSymtab(String serializedSymbolTable)
    {
        SymbolTable shared = loadSharedSymtab(serializedSymbolTable);
        catalog().putTable(shared);
        return shared;
    }



    //=========================================================================
    // Processing Ion text

    /**
     * Parses text as a sequence of Ion values.
     *
     * @param text must not be <code>null</code>.
     * @return the list of values in <code>text</code>, not <code>null</code>.
     */
    public IonDatagram values(String text)
        throws Exception
    {
        IonLoader loader = system().newLoader();
        return loader.load(text);
    }


    /**
     * Parses text as a single Ion value.  If the text contains more than that,
     * a failure is thrown.
     *
     * @param iterator must not be <code>null</code>.
     * @return a single value, or <code>null</code> if the scanner has nothing
     * on it.
     */
    public IonValue singleValue(Iterator<IonValue> iterator)
    {
        IonValue value = null;

        if (iterator.hasNext())
        {
            value = iterator.next();

            if (iterator.hasNext())
            {
                IonValue part = iterator.next();
                fail("Found unexpected part: " + part);
            }
        }

        return value;
    }



    /**
     * Parses text as a single Ion value.  If the text contains more than that,
     * a failure is thrown.
     *
     * @param text must not be <code>null</code>.
     * @return a single value, not <code>null</code>.
     */
    public IonValue oneValue(String text)
    {
        IonValue value = null;

        Iterator<IonValue> iterator = system().iterate(text);
        if (iterator.hasNext())
        {
            value = iterator.next();

            if (iterator.hasNext())
            {
                IonValue part = iterator.next();
                fail("Found unexpected part <" + part + "> in text: " + text);
            }
        }
        else
        {
            fail("No data found in text: " + text);
        }

        return value;
    }


    public IonDecimal decimal(String text)
    {
        return (IonDecimal) oneValue(text);
    }

    public IonSexp oneSexp(String text)
    {
        return (IonSexp) oneValue(text);
    }

    public IonStruct struct(String text)
    {
        return (IonStruct) oneValue(text);
    }


    public void putParsedValue(IonStruct struct,
                               String fieldName,
                               String singleValue)
    {
        IonValue v = null;
        if (singleValue != null)
        {
            IonSystem system = struct.getSystem();
            v = system.singleValue(singleValue);
        }
        struct.put(fieldName, v);
    }


    /**
     * Parse a broken value, expecting an IonException.
     * @param text can be anything.
     */
    public void badValue(String text)
    {
        try
        {
            oneValue(text);
            fail("Expected IonException");
        }
        catch (IonException e)
        {
            // TODO use a tighter exception type.  SyntaxError?
            // This line is here solely to allow a breakpoint to be set.
            @SuppressWarnings("unused")
            String msg = e.getMessage();
        }
    }


    // TODO add to IonSystem()?
    public byte[] encode(String ionText)
    {
        IonDatagram dg = loader().load(ionText);  // Keep here for breakpoint
        return dg.getBytes();
    }


    public void assertEscape(char expected, char escapedChar)
    {
        String ionText = makeEscapedCharString(escapedChar);
        IonString value = (IonString) oneValue(ionText);
        String valString = value.stringValue();
        assertEquals(1, valString.length());
        assertEquals(expected, valString.charAt(0));
    }


    public static void checkBinaryHeader(byte[] datagram)
    {
        if (datagram.length != 0) // Allow empty "binary" stream
        {
            assertTrue("datagram is too small", datagram.length >= 4);

            assertEquals("datagram cookie byte 1", 0xE0, datagram[0] & 0xff );
            assertEquals("datagram cookie byte 2", 0x01, datagram[1] & 0xff);
            assertEquals("datagram cookie byte 3", 0x00, datagram[2] & 0xff);
            assertEquals("datagram cookie byte 4", 0xEA, datagram[3] & 0xff);
        }
    }


    public static ReaderChecker check(IonReader reader)
    {
        return new ReaderChecker(reader);
    }

    public static IonValueChecker check(IonValue value)
    {
        return new IonValueChecker(value);
    }


    public static void checkType(IonType expected, IonValue actual)
    {
        if (actual.getType() != expected)
        {
            fail("Expected type " + expected + ", found IonValue: " + actual);
        }
    }


    /**
     * Checks that the value is an IonInt with the given value.
     * @param expected may be null to check for null.int
     */
    public static void checkInt(BigInteger expected, IonValue actual)
    {
        checkType(IonType.INT, actual);
        IonInt i = (IonInt) actual;

        if (expected == null) {
            assertTrue("expected null value", actual.isNullValue());
        }
        else
        {
            assertEquals("int content", expected, i.bigIntegerValue());
        }
    }

    /**
     * Checks that the value is an IonInt with the given value.
     * @param expected may be null to check for null.int
     */
    public static void checkInt(Long expected, IonValue actual)
    {
        checkType(IonType.INT, actual);
        IonInt i = (IonInt) actual;

        if (expected == null) {
            assertTrue("expected null value", actual.isNullValue());
        }
        else
        {
            assertEquals("int content", expected.longValue(), i.longValue());
        }
    }

    /**
     * Checks that the value is an IonInt with the given value.
     * @param expected may be null to check for null.int
     */
    public static void checkInt(Integer expected, IonValue actual)
    {
        checkInt((expected == null ? null : expected.longValue()), actual);
    }


    /**
     * Checks that the value is an IonDecimal with the given value.
     * @param expected may be null to check for null.decimal
     */
    public static void checkDecimal(Double expected, IonValue actual)
    {
        checkType(IonType.DECIMAL, actual);
        IonDecimal i = (IonDecimal) actual;

        if (expected == null) {
            assertTrue("expected null value", actual.isNullValue());
        }
        else
        {
            assertEquals("decimal content",
                         expected.doubleValue(), i.doubleValue(), 0d);
        }
    }


    public static void checkTimestamp(String expected, Timestamp actual)
    {
        if (expected == null) {
            assertNull(actual);
        }
        else
        {
            Timestamp expectedTs = Timestamp.valueOf(expected);

            assertEquals("timestamp", expectedTs, actual);
            assertEquals("timestamp content",
                         expected, actual.toString());
        }
    }


    /**
     * Checks that the value is an IonTimestamp with the given value.
     * @param expected may be null to check for null.timestamp
     */
    public static void checkTimestamp(Timestamp expected, IonValue actual)
    {
        checkType(IonType.TIMESTAMP, actual);
        IonTimestamp v = (IonTimestamp) actual;

        Timestamp actualTime = v.timestampValue();

        if (expected == null) {
            assertTrue("expected null value", v.isNullValue());
            assertNull(actualTime);
        }
        else
        {
            assertEquals("timestamp", expected, actualTime);
            assertEquals("timestamp content",
                         expected.toString(), actualTime.toString());
        }
    }

    /**
     * Checks that the value is an IonTimestamp with the given value.
     * @param expected may be null to check for null.timestamp
     */
    public static void checkTimestamp(String expected, IonValue actual)
    {
        checkType(IonType.TIMESTAMP, actual);
        IonTimestamp v = (IonTimestamp) actual;

        if (expected == null) {
            assertTrue("expected null value", v.isNullValue());
            assertNull(v.timestampValue());
        }
        else
        {
            assertEquals("timestamp content",
                         expected, v.timestampValue().toString());
            assertEquals("timestamp content",
                         expected, v.toString());
        }
    }


    /**
     * Checks that the value is an IonFloat with the given value.
     * @param expected may be null to check for null.float
     */
    public static void checkFloat(Double expected, IonValue actual)
    {
        checkType(IonType.FLOAT, actual);
        IonFloat i = (IonFloat) actual;

        if (expected == null) {
            assertTrue("expected null value", actual.isNullValue());
        }
        else
        {
            assertEquals("decimal content",
                         expected.doubleValue(), i.doubleValue(), 0d);
        }
    }

    public static void checkNullNull(IonValue actual)
    {
        checkType(IonType.NULL, actual);
    }


    /**
     * Checks that the value is an IonString with the given text.
     * @param text may be null to check for null.string
     */
    public static void checkString(String text, IonValue value)
    {
        checkType(IonType.STRING, value);
        IonString str = (IonString) value;
        assertEquals("string content", text, str.stringValue());
    }


    /**
     * @param text null means text is unknown
     */
    public static void checkSymbol(String text, int sid, SymbolToken sym)
    {
        assertEquals("SymbolToken.text", text, sym.getText());
        if (text == null) {
            // Local symbols with unknown text may be treated equivalently to symbol zero.
            assertTrue("SymbolToken.id", sid == sym.getSid() || 0 == sym.getSid());
        } else {
            assertEquals("SymbolToken.id", sid, sym.getSid());
        }

        if (text != null)
        {
            assertEquals("SymbolToken.assumeText", text, sym.assumeText());
        }
        else
        {
            try
            {
                sym.assumeText();
                fail("expected exception");
            }
            catch (RuntimeException e) { }
        }
    }


    /**
     * Checks that the value is an IonSymbol with the given name.
     * @param name may be null to check for null.symbol
     */
    public static void checkSymbol(String name, IonValue value)
    {
        checkType(IonType.SYMBOL, value);
        IonSymbol sym = (IonSymbol) value;
        assertEquals("symbol name", name, sym.stringValue());
        assertEquals("isNullValue", name == null, sym.isNullValue());

        SymbolToken is = sym.symbolValue();
        if (name == null)
        {
            assertEquals("IonSymbol.symbolValue()", null, is);
        }
        else
        {
            assertEquals("symbolValue.getText()", name, is.getText());
        }
    }

    /**
     * Checks that the value is an IonSymbol with the given name.
     */
    public static void checkSymbol(String name, int id, IonValue value)
    {
        checkType(IonType.SYMBOL, value);
        IonSymbol sym = (IonSymbol) value;

        assertFalse(value.isNullValue());

        if (name == null && id != 0)
        {
            try {
                sym.stringValue();
                fail("Expected " + UnknownSymbolException.class);
            }
            catch (UnknownSymbolException e)
            {
                assertEquals(id, e.getSid());
            }
        }
        else
        {
            // Note: symbol zero follows this path because it causes stringValue() to return null rather than throw.
            assertEquals("symbol name", name, sym.stringValue());
        }

        // just so we can set a break point on this before we lose all context
        int sid = sym.getSymbolId();
        if (sid != id) {
            assertEquals("symbol id", id, sym.getSymbolId());
        }

        checkSymbol(name, id, sym.symbolValue());
    }

    public static void checkUnknownSymbol(int id, IonValue value)
    {
        checkSymbol(null, id, value);
    }


    public static void checkSymbol(String text, int sid, boolean dupe,
                                   SymbolTable symtab)
    {
        assert !dupe || text != null;

        String msg = "text:" + text + " sid:" + sid;

        if (text != null)
        {
            if (sid != UNKNOWN_SYMBOL_ID)
            {
                assertEquals(msg, text, symtab.findKnownSymbol(sid));
            }

            // Can't do this stuff when we have a duplicate symbol.
            if (! dupe)
            {
                assertEquals(msg, sid, symtab.findSymbol(text));

                SymbolToken symToken = symtab.intern(text);
                assertEquals(msg, sid, symToken.getSid());

                symToken = symtab.find(text);
                assertEquals(msg, sid, symToken.getSid());
            }
        }
        else // No text expected, must have sid
        {
            assertEquals(msg, text /* null */, symtab.findKnownSymbol(sid));
        }
    }

    public static void checkSymbol(String text, int sid, SymbolTable symtab)
    {
        checkSymbol(text, sid, false, symtab);
    }

    public static void checkUnknownSymbol(int sid, SymbolTable symtab)
    {
        checkSymbol(null, sid, false, symtab);
    }

    /**
     * Check that a specific symbol's text isn't known by a symtab.
     * @param text must not be null.
     */
    public static void checkUnknownSymbol(String text, SymbolTable symtab)
    {
        assertEquals(null, symtab.find(text));
        assertEquals(UNKNOWN_SYMBOL_ID, symtab.findSymbol(text));
        if (symtab.isReadOnly())
        {
            try {
                symtab.intern(text);
                fail("Expected exception");
            }
            catch (ReadOnlyValueException e) { }
        }
    }

    /**
     * Check that a specific symbol's text isn't known by a symtab.
     * @param text must not be null.
     * @param sid can be {@link SymbolTable#UNKNOWN_SYMBOL_ID} if not known.
     */
    public static void checkUnknownSymbol(String text, int sid,
                                          SymbolTable symtab)
    {
        checkUnknownSymbol(text, symtab);

        if (sid != UNKNOWN_SYMBOL_ID)
        {
            checkUnknownSymbol(sid, symtab);
        }
    }


    public static void checkAnnotation(String expectedAnnot, IonValue value)
    {
        if (! value.hasTypeAnnotation(expectedAnnot))
        {
            fail("missing annotation " + expectedAnnot
                 + " on IonValue " + value);
        }
    }


    public static void checkLocalTable(SymbolTable symtab)
    {
        assertTrue("table isn't local", symtab.isLocalTable());
        assertFalse("table is shared",  symtab.isSharedTable());
        assertFalse("table is system",  symtab.isSystemTable());
        assertFalse("table is substitute", symtab.isSubstitute());
        assertNotNull("table has imports", symtab.getImportedTables());

        checkUnknownSymbol(" not defined ", UNKNOWN_SYMBOL_ID, symtab);

        SymbolTable system = symtab.getSystemSymbolTable();
        checkSystemTable(system);
        assertEquals(system.getIonVersionId(), symtab.getIonVersionId());
    }

    /**
     * @param symtab must be either system table or empty local table
     */
    public static void checkTrivialLocalTable(SymbolTable symtab)
    {
        SymbolTable system = symtab.getSystemSymbolTable();

        if (symtab.isLocalTable())
        {
            checkLocalTable(symtab);
            assertEquals(system.getMaxId(), symtab.getMaxId());
        }
        else
        {
            assertSame(symtab, system);
            checkSystemTable(symtab);
        }
    }

    public static void checkSystemTable(SymbolTable symtab)
    {
        assertFalse(symtab.isLocalTable());
        assertTrue(symtab.isSharedTable());
        assertTrue(symtab.isSystemTable());
        assertFalse("table is substitute", symtab.isSubstitute());
        assertSame(symtab, symtab.getSystemSymbolTable());
        assertEquals(SystemSymbols.ION_1_0_MAX_ID, symtab.getMaxId());
        assertEquals(ION_1_0, symtab.getIonVersionId());
    }

    public static SymbolTable findImportedTable(SymbolTable localTable,
                                                String importName)
    {
        SymbolTable[] imports = localTable.getImportedTables();
        if (imports == null) return null;

        for (int i = 0; i < imports.length; i++)
        {
            SymbolTable current = imports[i];
            // FIXME why does this allow null?
            if (current != null && importName.equals(current.getName()))
            {
                return current;
            }
        }

        return null;
    }

    /**
     * Checks decimal equality, including precision and negative-zero.
     *
     * @param expected may be null
     * @param actual may be null
     */
    public static void assertPreciselyEquals(BigDecimal expected,
                                             BigDecimal actual)
    {
        assertEquals(expected, actual);
        if (expected != null)
        {
            assertEquals("value",
                         expected.unscaledValue(), actual.unscaledValue());
            assertEquals("scale",
                         expected.scale(), actual.scale());
            assertEquals("isNegativeZero",
                         Decimal.isNegativeZero(expected),
                         Decimal.isNegativeZero(actual));
        }
    }


    public static void assertEquals(IonValue expected, IonValue actual)
    {
        IonAssert.assertIonEquals(expected, actual);
    }


    public static void assertEqualBytes(byte[] expected, int start, int limit,
                                        byte[] actual)
    {
        for (int i = start; i < limit; i++)
        {
            assertEquals(expected[i], actual[i - start]);
        }
    }

    public static void assertArrayContentsSame(Object[] expected, Object[] actual)
    {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertSame(expected[i], actual[i]);
        }
    }

    public void testCloneVariants(IonValue original)
    {
        // Test on IonValue.clone()
        testSimpleClone(original);

        // Test on ValueFactory.clone() with the same ValueFactory
        testValueFactoryClone(original, original.getSystem());

        // Test on ValueFactory.clone() with different ValueFactory (and DOM impls)
        testValueFactoryClone(original, newSystem(new SimpleCatalog()));
    }

    /**
     * Test that a single IonValue created from {@code input}, is
     * equal to its clone through {@link IonValue#clone()}.
     *
     * @param input the original Ion text data
     */
    public void testSimpleClone(String input)
    {
        IonValue original = system().singleValue(input);
        testSimpleClone(original);
    }

    /**
     * Test that a single IonValue is equal to its clone through
     * {@link IonValue#clone()}.
     *
     * @param original the original value
     */
    public void testSimpleClone(IonValue original)
    {
        IonValue clone = original.clone();
        IonAssert.assertIonEquals(original, clone);

        assertSame("ValueFactory of cloned value should be the same " +
                   "reference as the original's",
                   original.getSystem(), clone.getSystem());

        assertNull("Cloned value should not have a container (parent)",
                   clone.getContainer());

        assertFalse("Cloned value should be modifiable", clone.isReadOnly());
    }

    /**
     * Test that a single IonValue is equal to its clone through
     * {@link ValueFactory#clone(IonValue)}.
     *
     * @param original the original value
     * @param newFactory the {@link ValueFactory} for the new clone
     */
    public void testValueFactoryClone(IonValue original,
                                      ValueFactory newFactory)
    {
        IonValue clone = newFactory.clone(original);
        IonAssert.assertIonEquals(original, clone);

        // TODO amzn/ion-java/issues/30 IonSystemLite.clone() on a value that is in IonSystemImpl
        // doesn't seem to copy over local symbol tables.
//        assertEquals(original.toString(), clone.toString());

        assertSame("ValueFactory of cloned value should be the same " +
                   "reference as the factory that cloned it",
                   newFactory, clone.getSystem());

        assertNull("Cloned value should not have a container (parent)",
                   clone.getContainer());

        assertFalse("Cloned value should be modifiable", clone.isReadOnly());
    }

    public void logSkippedTest()
    {
        System.out.println("WARNING: skipped a test in " + getClass().getName());
    }

    /** Temporary bridge from JUnit 3 */
    public void setUp() throws Exception { }


    /** JUnit 4 disables this */
    public static void assertEquals(double expected, double actual)
    {
        assertEquals(expected, actual, 0d);
    }

    /** JUnit 4 disables this */
    public static void assertEquals(String message, double expected, double actual)
    {
        assertEquals(message, expected, actual, 0d);
    }
}
