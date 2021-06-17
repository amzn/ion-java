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

import static com.amazon.ion.impl._Private_Utils.READER_HASNEXT_REMOVED;

import com.amazon.ion.impl._Private_Utils;
import com.amazon.ion.util.IonStreamUtils;
import java.io.File;
import java.io.FilenameFilter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


public class TestUtils
{
    public static final String US_ASCII_CHARSET_NAME = "US-ASCII";

    public static final Charset US_ASCII_CHARSET =
        Charset.forName(US_ASCII_CHARSET_NAME);

    public static final String UTF16BE_CHARSET_NAME = "UTF-16BE";

    public static final Charset UTF16BE_CHARSET =
        Charset.forName(UTF16BE_CHARSET_NAME);

    public static final String BAD_IONTESTS_FILES               = "bad";
    public static final String BAD_TIMESTAMP_IONTESTS_FILES     = "bad/timestamp";

    public static final String GOOD_IONTESTS_FILES              = "good";
    public static final String GOOD_TIMESTAMP_IONTESTS_FILES    = "good/timestamp";

    public static final String EQUIVS_IONTESTS_FILES            = "good/equivs";
    public static final String EQUIVS_TIMESTAMP_IONTESTS_FILES  = "good/timestamp/equivTimeline";

    public static final String NON_EQUIVS_IONTESTS_FILEs        = "good/non-equivs";



    public static final FilenameFilter TEXT_ONLY_FILTER = new FilenameFilter()
    {
        public boolean accept(File dir, String name)
        {
            return name.endsWith(".ion");
        }
    };

    public static final FilenameFilter NOT_MARKDOWN_FILTER = new FilenameFilter()
    {
        public boolean accept(File dir, String name)
        {
            return !name.endsWith(".md");
        }
    };

    public static final class And implements FilenameFilter
    {
        private final FilenameFilter[] myFilters;

        public And(FilenameFilter... filters) { myFilters = filters; }

        public boolean accept(File dir, String name)
        {
            for (FilenameFilter filter : myFilters)
            {
                if (! filter.accept(dir, name)) return false;
            }
            return true;
        }
    }

    /**
     * A FilenameFilter that filters file names based on an optional parent
     * directory pathname, and a required file name. Refer to
     * {@link FileIsNot#FileIsNot(String...)} constructor.
     */
    public static final class FileIsNot implements FilenameFilter
    {
        private final String[] mySkips;

        /**
         * {@code filesToSkip} must be of the form ".../filename.ion" where
         * ... is optional and is the parent directory pathname of the file
         * to skip. The leading slash is also optional.
         * <p>
         * Examples of valid parameters are:
         * <ul>
         *      <li>{@code iontestdata/good/non-equivs/filename.ion}</li>
         *      <li>{@code non-equivs/filename.ion}</li>
         *      <li>{@code filename.ion}</li>
         * </ul>
         *
         * @param filesToSkip
         */
        public FileIsNot(String... filesToSkip) { mySkips = filesToSkip; }

        public boolean accept(File dir, String name)
        {
            for (String skip : mySkips)
            {
                // Remove leading slash if it already exists
                if (name.startsWith("/"))
                {
                    name = name.substring(1);
                }

                String fullFilePath = "/" + dir.getPath() + "/" + name;

                if (fullFilePath.endsWith(skip)) return false;
            }
            return true;
        }
    }

    public static final FilenameFilter GLOBAL_SKIP_LIST =
        new And(
            // Skips documentation that accompanies some test vectors
            NOT_MARKDOWN_FILTER,   
            new FileIsNot(
                      "bad/clobWithNullCharacter.ion"          // TODO amzn/ion-java/43
                      ,"bad/emptyAnnotatedInt.10n"             // TODO amzn/ion-java/55
                      ,"good/subfieldVarUInt32bit.ion"         // TODO amzn/ion-java/62
                      ,"good/subfieldVarUInt.ion"              // Note: this passes but takes too long. That's fine; it's not a realistic use case.
                      ,"good/utf16.ion"                        // TODO amzn/ion-java/61
                      ,"good/utf32.ion"                        // TODO amzn/ion-java/61
                      ,"good/whitespace.ion"
                      ,"good/item1.10n"                        // TODO amzn/ion-java#126 (roundtrip symbols with unknown text)
                      ,"bad/typecodes/type_6_length_0.10n"     // TODO amzn/ion-java#272
                      ,"good/typecodes/T7-large.10n"           // TODO amzn/ion-java#273 
                      ,"good/equivs/clobNewlines.ion"          // TODO amzn/ion-java#274
            )
        );


    private static void testdataFiles(FilenameFilter filter,
                                      File dir,
                                      List<File> results,
                                      boolean recurse)
    {
        String[] fileNames = dir.list();
        if (fileNames == null)
        {
            String message = "Not a directory: " + dir.getAbsolutePath();
            throw new IllegalArgumentException(message);
        }

        // Sort the fileNames so they are listed in order.
        // This is not a functional requirement but it helps humans scanning
        // the output looking for a specific file.
        Arrays.sort(fileNames);

        for (String fileName : fileNames)
        {
            File testFile = new File(dir, fileName);
            if (testFile.isDirectory())
            {
                if (recurse)
                {
                    testdataFiles(filter, testFile, results, recurse);
                }
            }
            else if (filter == null || filter.accept(dir, fileName))
            {
                results.add(testFile);
            }
        }
    }

    public static File[] testdataFiles(FilenameFilter filter,
                                       boolean recurse,
                                       String... testdataDirs)
    {
        ArrayList<File> files = new ArrayList<File>();

        for (String testdataDir : testdataDirs)
        {
            File dir = IonTestCase.getTestdataFile(testdataDir);
            if (! dir.isDirectory())
            {
                String message =
                    "testdataDir is not a directory: "
                        + dir.getAbsolutePath();
                throw new IllegalArgumentException(message);
            }

            testdataFiles(filter, dir, files, recurse);
        }

        return files.toArray(new File[files.size()]);
    }


    public static File[] testdataFiles(FilenameFilter filter,
                                       String... testdataDirs)
    {
        return testdataFiles(filter, /* recurse */ true, testdataDirs);
    }


    public static File[] testdataFiles(String... testdataDirs)
    {
        return testdataFiles(null, testdataDirs);
    }


    //========================================================================


    public static byte[] ensureBinary(IonSystem system, byte[] ionData)
    {
        if (IonStreamUtils.isIonBinary(ionData)) return ionData;

        IonDatagram dg = system.getLoader().load(ionData);
        return dg.getBytes();
    }

    public static byte[] ensureText(IonSystem system, byte[] ionData)
    {
        if (! IonStreamUtils.isIonBinary(ionData)) return ionData;

        IonDatagram dg = system.getLoader().load(ionData);
        String ionText = dg.toString();
        return _Private_Utils.utf8(ionText);
    }


    //========================================================================

    /**
     * Performs a "deep read" of the reader's current value, including scalar
     * data. This therefore exercises all the {@code *Value()} methods of the
     * reader.
     *
     * @see #deepRead(IonReader)
     */
    public static void consumeCurrentValue(IonReader reader)
    {
        consumeCurrentValue(reader, true);
    }

    public static void consumeCurrentValue(IonReader reader,
                                           boolean flgMaterializeScalars)
    {
        IonType t = reader.getType();
        if (t == null) return;

        reader.getFieldNameSymbol();
        reader.getTypeAnnotationSymbols();

        switch (t)
        {
            case NULL:
            case BOOL:
            case INT:
            case FLOAT:
            case DECIMAL:
            case TIMESTAMP:
            case STRING:
            case SYMBOL:
            case BLOB:
            case CLOB:
                if ( flgMaterializeScalars )
                    materializeScalar(reader);
                break;

            case STRUCT:
            case LIST:
            case SEXP:
                reader.stepIn();
                deepRead( reader, flgMaterializeScalars );
                reader.stepOut();
                break;

            default:
                throw new IllegalStateException("unexpected type: " + t);
        }
    }


    /**
     * Reads everything until the end of the current container, traversing
     * down nested containers.
     *
     * @param reader
     *
     * @see SexpTest#readAll(IonReader)
     */
    public static void deepRead(IonReader reader)
    {
        deepRead( reader, true );
    }

    public static void deepRead(IonReader reader, boolean flgMaterializeScalars)
    {
        while (doNext(reader) != null )
        {
            consumeCurrentValue(reader, flgMaterializeScalars);
        }
    }

    @SuppressWarnings("deprecation")
    private static IonType doNext(IonReader reader)
    {
        boolean hasnext = true;
        IonType t = null;

        if (! READER_HASNEXT_REMOVED) {
            hasnext = reader.hasNext();
        }
        if (hasnext) {
            t = reader.next();
        }
        return t;
    }


    @SuppressWarnings("unused")
    private static void materializeScalar(IonReader reader)
    {
        IonType t = reader.getType();

        if (t == null) {
            return;
        }
        if (reader.isNullValue()) {
            return;
        }

        switch (t)
        {
            case NULL:
                // we really shouldn't get here, but it's not really an issue
                reader.isNullValue();
                break;
            case BOOL:
                boolean b = reader.booleanValue();
                break;
            case INT:
                BigInteger big = reader.bigIntegerValue();
                break;
            case FLOAT:
                double f = reader.doubleValue();
                break;
            case DECIMAL:
                BigDecimal bd = reader.bigDecimalValue();
                break;
            case TIMESTAMP:
                Timestamp time = reader.timestampValue();
                break;
            case STRING:
                String s = reader.stringValue();
                break;
            case SYMBOL:
                SymbolToken tok = reader.symbolValue();
                break;
            case BLOB:
            case CLOB:
                int bs = reader.byteSize();
                // Extract the content to dig up encoding issues (could be text).
                reader.newBytes();
                break;

            case STRUCT:
            case LIST:
            case SEXP:
                break;

            default:
                throw new IllegalStateException("unexpected type: " + t);
        }
    }


    public static String hexDump(final String str)
    {
        final byte[] utf16Bytes = _Private_Utils.encode(str, UTF16BE_CHARSET);
        StringBuilder buf = new StringBuilder(utf16Bytes.length * 4);
        for (byte b : utf16Bytes) {
            buf.append(Integer.toString(0x00FF & b, 16));
            buf.append(' ');
        }
        return buf.toString();
    }

    public static String hexDump(final byte[] octets)
    {
        final StringBuilder builder = new StringBuilder();
        for (byte octet : octets) {
            builder.append(String.format("%02X ", octet));
        }
        return builder.toString();
    }

    /**
     * Returns object equality, accepting null references as arguments.
     * Will return true if both references are null or if both references are non-null and compares as true
     * via {@link Object#equals(Object)}.
     */
    public static boolean equals(final Object first, final Object second)
    {
        if (first == second)
        {
            return true;
        }
        if ((first != null && second == null) || (first == null && second != null))
        {
            return false;
        }

        return first.equals(second);
    }

    /**
     * Compares two symbol tables for equivalent content.
     * Two symbol tables compare equally if they are both of the same type (i.e. <i>system</i>, <i>shared</i>, <i>local</i>),
     * they have the same name and version, and they contain the same declared symbols and imported symbol tables
     * (in the same import order, and equality by this definition).
     */
    public static boolean symbolTableEquals(final SymbolTable first, final SymbolTable second)
    {
        // reference checks
        if (first == second)
        {
            return true;
        }
        if ((first != null && second == null) || (first == null && second != null))
        {
            return false;
        }

        // symbol table type checks
        if (first.isSystemTable() != second.isSystemTable())
        {
            return false;
        }
        if (first.isSharedTable() != second.isSharedTable())
        {
            return false;
        }
        if (first.isLocalTable() != second.isLocalTable())
        {
            return false;
        }

        // check name/version
        if (!equals(first.getName(), second.getName()))
        {
            return false;
        }
        if (first.getVersion() != second.getVersion())
        {
            return false;
        }

        if (first.getMaxId() != second.getMaxId()) {
            return false;
        }

        // check imports
        final SymbolTable[] firstImports = first.getImportedTables();
        final SymbolTable[] secondImports = second.getImportedTables();
        if (firstImports != null && secondImports == null)
        {
            return false;
        }
        if (firstImports == null && secondImports != null)
        {
            return false;
        }
        if (firstImports != null && secondImports != null)
        {
            if (firstImports.length != secondImports.length)
            {
                return false;
            }
            for (int i = 0; i < firstImports.length; i++)
            {
                if (!symbolTableEquals(firstImports[i], secondImports[i]))
                {
                    return false;
                }
            }
        }

        // check declared symbols
        final Iterator<String> firstSymbols = first.iterateDeclaredSymbolNames();
        final Iterator<String> secondSymbols = second.iterateDeclaredSymbolNames();
        while (firstSymbols.hasNext() && secondSymbols.hasNext())
        {
            final String firstNextSymbol = firstSymbols.next();
            final String secondNextSymbol = secondSymbols.next();
            if (!equals(firstNextSymbol, secondNextSymbol))
            {
                return false;
            }
        }
        if (firstSymbols.hasNext() != secondSymbols.hasNext())
        {
            return false;
        }

        return true;
    }

    /**
     * U+00A5 YEN SIGN
     * UTF-8 (hex)      0xC2 0xA5 (c2a5)
     * UTF-8 (binary)  11000010:10100101
     * UTF-16 (hex)    0x00A5 (00a5)
     * UTF-32 (hex)    0x000000A5 (00a5)
     */
    public static final String YEN_SIGN = "\u00a5";

    /**
     * U+1D110 MUSICAL SYMBOL FERMATA
     * <pre>
     * UTF-8 (hex)     0xF0 0x9D 0x84 0x90 (f09d8490)
     * UTF-8 (binary)  11110000:10011101:10000100:10010000
     * UTF-16 (hex)    0xD834 0xDD10 (d834dd10)
     * UTF-32 (hex)    0x0001D110 (1d110)
     * </pre>
     */
    public static final String FERMATA = "\ud834\udd10";

    public static final byte[] FERMATA_UTF8 =
    {
        (byte) 0xF0, (byte) 0x9D, (byte) 0x84, (byte) 0x90
    };

    static
    {
        if (! _Private_Utils.utf8(FERMATA_UTF8).equals(FERMATA))
        {
            throw new AssertionError("Broken encoding");
        }
    }
}
