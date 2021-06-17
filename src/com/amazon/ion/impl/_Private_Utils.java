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
import static com.amazon.ion.SystemSymbols.IMPORTS;
import static com.amazon.ion.SystemSymbols.IMPORTS_SID;
import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE;
import static com.amazon.ion.SystemSymbols.MAX_ID;
import static com.amazon.ion.SystemSymbols.MAX_ID_SID;
import static com.amazon.ion.SystemSymbols.NAME;
import static com.amazon.ion.SystemSymbols.NAME_SID;
import static com.amazon.ion.SystemSymbols.SYMBOLS;
import static com.amazon.ion.SystemSymbols.SYMBOLS_SID;
import static com.amazon.ion.SystemSymbols.VERSION;
import static com.amazon.ion.SystemSymbols.VERSION_SID;
import static com.amazon.ion.util.IonStreamUtils.isIonBinary;

import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.SubstituteSymbolTableException;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.UnknownSymbolException;
import com.amazon.ion.ValueFactory;
import com.amazon.ion.impl.IonBinary.BufferManager;
import com.amazon.ion.impl.IonBinary.Reader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.TimeZone;

/**
 * NOT FOR APPLICATION USE!
 */
public final class _Private_Utils
{
    /**
     * Marker for code points relevant to removal of IonReader.hasNext().
     */
    public static final boolean READER_HASNEXT_REMOVED = true;


    /** Just a zero-length byte array, used to avoid allocation. */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /** Just a zero-length String array, used to avoid allocation. */
    public final static String[] EMPTY_STRING_ARRAY = new String[0];

    /** Just a zero-length int array, used to avoid allocation. */
    public final static int[] EMPTY_INT_ARRAY = new int[0];

    /**
     * (null.timestamp) requires 11 ASCII chars to distinguish from
     * (null.timestamps) aka (null '.' 'timestamps')
     *
     * @see IonCharacterReader#DEFAULT_BUFFER_SIZE
     * @see IonCharacterReader#BUFFER_PADDING
     */
    public static final int MAX_LOOKAHEAD_UTF16 = 11;



    public static final String ASCII_CHARSET_NAME = "US-ASCII";

    public static final Charset ASCII_CHARSET =
        Charset.forName(ASCII_CHARSET_NAME);

    /** The string {@code "UTF-8"}. */
    public static final String UTF8_CHARSET_NAME = "UTF-8";

    public static final Charset UTF8_CHARSET =
        Charset.forName(UTF8_CHARSET_NAME);


    /**
     * The UTC {@link TimeZone}.
     *
     * TODO determine if this is well-defined.
     */
    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");



    public static final ListIterator<?> EMPTY_ITERATOR = new ListIterator() {
        public boolean hasNext()     { return false; }
        public boolean hasPrevious() { return false; }

        public Object  next()     { throw new NoSuchElementException(); }
        public Object  previous() { throw new NoSuchElementException(); }
        public void    remove()   { throw new IllegalStateException(); }

        public int nextIndex()     { return  0; }
        public int previousIndex() { return -1; }

        public void add(Object o) { throw new UnsupportedOperationException(); }
        public void set(Object o) { throw new UnsupportedOperationException(); }
    };

    @SuppressWarnings("unchecked")
    public static final <T> ListIterator<T> emptyIterator()
    {
        return (ListIterator<T>) EMPTY_ITERATOR;
    }

    public static boolean safeEquals(Object a, Object b)
    {
        // Written for the common case where they are not the same instance
        return (a != null ? a.equals(b) : b == null);
    }

    /**
     * Replacement for Java6 {@link Arrays#copyOf(byte[], int)}.
     */
    public static byte[] copyOf(byte[] original, int newLength)
    {
        byte[] result = new byte[newLength];
        System.arraycopy(original, 0, result, 0,
                         Math.min(newLength, original.length));
        return result;
    }

    public static String[] copyOf(String[] original, int newLength)
    {
        String[] result = new String[newLength];
        System.arraycopy(original, 0, result, 0,
                         Math.min(newLength, original.length));
        return result;
    }

    public static <T> void addAll(Collection<T> dest, Iterator<T> src)
    {
        if (src != null)
        {
            while (src.hasNext())
            {
                T value = src.next();
                dest.add(value);
            }
        }
    }

    public static <T> void addAllNonNull(Collection<T> dest, Iterator<T> src)
    {
        if (src != null)
        {
            while (src.hasNext())
            {
                T value = src.next();
                if (value != null)
                {
                    dest.add(value);
                }
            }
        }
    }

    /**
     * @return not null
     */
    public static SymbolTokenImpl newSymbolToken(String text, int sid)
    {
        return new SymbolTokenImpl(text, sid);
    }

    /**
     * @return not null
     */
    public static SymbolTokenImpl newSymbolToken(int sid)
    {
        return new SymbolTokenImpl(sid);
    }

    /**
     * Checks symbol content.
     * @return not null
     */
    public static SymbolToken newSymbolToken(SymbolTable symtab,
                                             String text)
    {
        // TODO amzn/ion-java/issues/21 symtab should not be null
        text.getClass(); // quick null check

        SymbolToken tok = (symtab == null ? null : symtab.find(text));
        if (tok == null)
        {
            tok = new SymbolTokenImpl(text, UNKNOWN_SYMBOL_ID);
        }
        return tok;
    }

    /**
     * @return not null
     */
    public static SymbolToken newSymbolToken(SymbolTable symtab,
                                             int sid)
    {
        if (sid < 1) throw new IllegalArgumentException();

        // TODO amzn/ion-java/issues/21 symtab should not be null
        String text = (symtab == null ? null : symtab.findKnownSymbol(sid));
        return new SymbolTokenImpl(text, sid);
    }

    /**
     * Validates each text element.
     * @param text may be null or empty.
     * @return not null.
     */
    public static SymbolToken[] newSymbolTokens(SymbolTable symtab,
                                                String... text)
    {
        if (text != null)
        {
            int count = text.length;
            if (count != 0)
            {
                SymbolToken[] result = new SymbolToken[count];
                for (int i = 0; i < count; i++)
                {
                    String s = text[i];
                    result[i] = newSymbolToken(symtab, s);
                }
                return result;
            }
        }
        return SymbolToken.EMPTY_ARRAY;
    }

    /**
     * @param syms may be null or empty.
     * @return not null.
     */
    public static SymbolToken[] newSymbolTokens(SymbolTable symtab,
                                                int... syms)
    {
        if (syms != null)
        {
            int count = syms.length;
            if (syms.length != 0)
            {
                SymbolToken[] result = new SymbolToken[count];
                for (int i = 0; i < count; i++)
                {
                    int s = syms[i];
                    result[i] = newSymbolToken(symtab, s);
                }
                return result;
            }
        }
        return SymbolToken.EMPTY_ARRAY;
    }


    public static SymbolToken localize(SymbolTable symtab,
                                       SymbolToken sym)
    {
        String text = sym.getText();
        int sid = sym.getSid();

        if (symtab != null)  // TODO amzn/ion-java/issues/21 require symtab
        {
            if (text == null)
            {
                text = symtab.findKnownSymbol(sid);
                if (text != null)
                {
                    sym = new SymbolTokenImpl(text, sid);
                }
            }
            else
            {
                SymbolToken newSym = symtab.find(text);
                if (newSym != null)
                {
                    sym = newSym;
                }
                else if (sid >= 0)
                {
                    // We can't trust the sid, discard it.
                    sym = new SymbolTokenImpl(text, UNKNOWN_SYMBOL_ID);
                }
            }
        }
        else if (text != null && sid >= 0)
        {
            // We can't trust the sid, discard it.
            sym = new SymbolTokenImpl(text, UNKNOWN_SYMBOL_ID);
        }
        return sym;
    }


    /**
    *
    * @param syms may be mutated, replacing entries with localized updates!
    */
    public static void localize(SymbolTable symtab,
                                SymbolToken[] syms,
                                int count)
    {
        for (int i = 0; i < count; i++)
        {
            SymbolToken sym = syms[i];
            SymbolToken updated = localize(symtab, sym);
            if (updated != sym) syms[i] = updated;
        }
    }

    /**
     *
     * @param syms may be mutated, replacing entries with localized updates!
     */
    public static void localize(SymbolTable symtab,
                                SymbolToken[] syms)
    {
        localize(symtab, syms, syms.length);
    }


    /**
     * Extracts the non-null text from a list of symbol tokens.
     *
     * @return not null.
     *
     * @throws UnknownSymbolException if any token is missing text.
     */
    public static String[] toStrings(SymbolToken[] symbols, int count)
    {
        if (count == 0) return _Private_Utils.EMPTY_STRING_ARRAY;

        String[] annotations = new String[count];
        for (int i = 0; i < count; i++)
        {
            SymbolToken tok = symbols[i];
            String text = tok.getText();
            if (text == null)
            {
                throw new UnknownSymbolException(tok.getSid());
            }
            annotations[i] = text;
        }
        return annotations;
    }

    public static int[] toSids(SymbolToken[] symbols, int count)
    {
        if (count == 0) return _Private_Utils.EMPTY_INT_ARRAY;

        int[] sids = new int[count];
        for (int i = 0; i < count; i++)
        {
            sids[i] = symbols[i].getSid();
        }
        return sids;
    }

    //========================================================================

    /**
     * Encodes a String into bytes of a given encoding.
     * <p>
     * This method is preferred to {@link Charset#encode(String)} and
     * {@link String#getBytes(String)} (<em>etc.</em>)
     * since those methods will replace or ignore bad input, and here we throw
     * an exception.
     *
     * @param s the string to encode.
     *
     * @return the encoded string, not null.
     *
     * @throws IonException if there's a {@link CharacterCodingException}.
     */
    public static byte[] encode(String s, Charset charset)
    {
        CharsetEncoder encoder = charset.newEncoder();
        try
        {
            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(s));
            byte[] bytes = buffer.array();

            // Make another copy iff there's garbage after the limit.
            int limit = buffer.limit();
            if (limit < bytes.length)
            {
                bytes = copyOf(bytes, limit);
            }

            return bytes;
        }
        catch (CharacterCodingException e)
        {
            throw new IonException("Invalid string data", e);
        }
    }


    /**
     * Decodes a byte sequence into a string, given a {@link Charset}.
     * <p>
     * This method is preferred to {@link Charset#decode(ByteBuffer)} and
     * {@link String#String(byte[], Charset)} (<em>etc.</em>)
     * since those methods will replace or ignore bad input, and here we throw
     * an exception.
     *
     * @param bytes the data to decode.
     *
     * @return the decoded string, not null.
     *
     * @throws IonException if there's a {@link CharacterCodingException}.
     */
    public static String decode(byte[] bytes, Charset charset)
    {
        CharsetDecoder decoder = charset.newDecoder();
        try
        {
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            return buffer.toString();
        }
        catch (CharacterCodingException e)
        {
            String message =
                "Input is not valid " + charset.displayName() + " data";
            throw new IonException(message, e);
        }
    }


    /**
     * Encodes a String into UTF-8 bytes.
     * <p>
     * This method is preferred to {@link Charset#encode(String)} and
     * {@link String#getBytes(String)} (<em>etc.</em>)
     * since those methods will replace or ignore bad input, and here we throw
     * an exception.
     *
     * @param s the string to encode.
     *
     * @return the encoded UTF-8 bytes, not null.
     *
     * @throws IonException if there's a {@link CharacterCodingException}.
     */
    public static byte[] utf8(String s)
    {
        return encode(s, UTF8_CHARSET);
    }

    /**
     * Decodes a UTF-8 byte sequence to a String.
     * <p>
     * This method is preferred to {@link Charset#decode(ByteBuffer)} and
     * {@link String#String(byte[], Charset)} (<em>etc.</em>)
     * since those methods will replace or ignore bad input, and here we throw
     * an exception.
     *
     * @param bytes the data to decode.
     *
     * @return the decoded string, not null.
     *
     * @throws IonException if there's a {@link CharacterCodingException}.
     */
    public static String utf8(byte[] bytes)
    {
        return decode(bytes, UTF8_CHARSET);
    }


    /**
     * This differs from {@link #utf8(String)} by using our custem encoder.
     * Not sure which is better.
     * TODO benchmark the two approaches
     */
    public static byte[] convertUtf16UnitsToUtf8(String text)
    {
        byte[] data = new byte[4*text.length()];
        int limit = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            limit += IonUTF8.convertToUTF8Bytes(c, data, limit,
                                                data.length - limit);
        }

        byte[] result = new byte[limit];
        System.arraycopy(data, 0, result, 0, limit);
        return result;
    }


    //========================================================================

    /**
     * Calls {@link InputStream#read(byte[], int, int)} until the buffer is
     * filled or EOF is encountered.
     * This method will block until the request is satisfied.
     *
     * @param in        The stream to read from.
     * @param buf       The buffer to read to.
     *
     * @return the number of bytes read from the stream.  May be less than
     *  {@code buf.length} if EOF is encountered before reading that far.
     *
     * @see #readFully(InputStream, byte[], int, int)
     */
    public static int readFully(InputStream in, byte[] buf)
    throws IOException
    {
        return readFully(in, buf, 0, buf.length);
    }


    /**
     * Calls {@link InputStream#read(byte[], int, int)} until the requested
     * length is read or EOF is encountered.
     * This method will block until the request is satisfied.
     *
     * @param in        The stream to read from.
     * @param buf       The buffer to read to.
     * @param offset    The offset of the buffer to read from.
     * @param length    The length of the data to read.
     *
     * @return the number of bytes read from the stream.  May be less than
     *  {@code length} if EOF is encountered before reading that far.
     *
     * @see #readFully(InputStream, byte[])
     */
    public static int readFully(InputStream in, byte[] buf,
                                int offset, int length)
    throws IOException
    {
        int readBytes = 0;
        while (readBytes < length)
        {
            int amount = in.read(buf, offset, length - readBytes);
            if (amount < 0)
            {
                // EOF
                return readBytes;
            }
            readBytes += amount;
            offset += amount;
        }
        return readBytes;
    }


    public static byte[] loadFileBytes(File file)
        throws IOException
    {
        long len = file.length();
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File too long: " + file);
        }

        byte[] buf = new byte[(int) len];

        FileInputStream in = new FileInputStream(file);
        try {
            int readBytesCount = in.read(buf);
            if (readBytesCount != len || in.read() != -1)
            {
                throw new IOException("Read the wrong number of bytes from "
                                       + file);
            }
        }
        finally {
            in.close();
        }

        return buf;
    }

    public static String utf8FileToString(File file)
        throws IonException, IOException
    {
        byte[] utf8Bytes = _Private_Utils.loadFileBytes(file);
        String s = utf8(utf8Bytes);
        return s;
    }

    public static byte[] loadStreamBytes(InputStream in)
        throws IOException
    {
        BufferManager buffer = new BufferManager(in);
        Reader bufReader = buffer.reader();
        bufReader.sync();
        bufReader.setPosition(0);
        byte[] bytes = bufReader.getBytes();
        return bytes;
    }


    public static String loadReader(java.io.Reader in)
        throws IOException
    {
        StringBuilder buf = new StringBuilder(2048);

        char[] chars = new char[2048];

        int len;
        while ((len = in.read(chars)) != -1)
        {
            buf.append(chars, 0, len);
        }

        return buf.toString();
    }


    public static boolean streamIsIonBinary(PushbackInputStream pushback)
        throws IonException, IOException
    {
        boolean isBinary = false;
        byte[] cookie = new byte[_Private_IonConstants.BINARY_VERSION_MARKER_SIZE];

        int len = readFully(pushback, cookie);
        if (len == _Private_IonConstants.BINARY_VERSION_MARKER_SIZE) {
            isBinary = isIonBinary(cookie);
        }
        if (len > 0) {
            pushback.unread(cookie, 0, len);
        }
        return isBinary;
    }


    /**
     * Create a value iterator from a reader.
     * Primarily a trampoline for access permission.
     */
    public static Iterator<IonValue> iterate(ValueFactory valueFactory,
                                             IonReader input)
     {
        return new IonIteratorImpl(valueFactory, input);
     }

    //========================================================================
    // Symbol Table helpers

    /**
     * Checks the passed in value and returns whether or not
     * the value could be a local symbol table.  It does this
     * by checking the type and annotations.
     *
     * @return boolean true if v can be a local symbol table otherwise false
     */
    public static boolean valueIsLocalSymbolTable(_Private_IonValue v)
    {
        return (v instanceof IonStruct
                && v.findTypeAnnotation(ION_SYMBOL_TABLE) == 0);
    }


    /** Indicates whether a table is shared but not a system table. */
    public static final boolean symtabIsSharedNotSystem(SymbolTable symtab)
    {
        return (symtab != null
                && symtab.isSharedTable()
                && ! symtab.isSystemTable());
    }


    public static boolean symtabIsLocalAndNonTrivial(SymbolTable symtab)
    {
        if (symtab == null) return false;
        if (!symtab.isLocalTable()) return false;

        // If symtab has imports we must retain it.
        // Note that I chose to retain imports even in the degenerate case
        // where the imports have no symbols.
        if (symtab.getImportedTables().length > 0) {
            return true;
        }

        if (symtab.getImportedMaxId() < symtab.getMaxId()) {
            return true;
        }

        return false;
    }


    /**
     * Is the table null, system, or local without imported symbols?
     */
    public static boolean isTrivialTable(SymbolTable table)
    {
        if (table == null)         return true;
        if (table.isSystemTable()) return true;
        if (table.isLocalTable()) {
            // this is only true when there are no local
            // symbols defined
            // and there are no imports with any symbols
            if (table.getMaxId() == table.getSystemSymbolTable().getMaxId()) {
                return true;
            }
        }
        return false;
    }


    public static SymbolTable systemSymtab(int version)
    {
        return SharedSymbolTable.getSystemSymbolTable(version);
    }


    public static SymbolTable newSharedSymtab(IonStruct ionRep)
    {
        return SharedSymbolTable.newSharedSymbolTable(ionRep);
    }


    public static SymbolTable newSharedSymtab(IonReader reader,
                                              boolean alreadyInStruct)
    {
        return SharedSymbolTable.newSharedSymbolTable(reader, alreadyInStruct);
    }


    /**
     * As per {@link IonSystem#newSharedSymbolTable(String, int, Iterator, SymbolTable...)},
     * any duplicate or null symbol texts are skipped.
     * Therefore, <b>THIS METHOD IS NOT SUITABLE WHEN READING SERIALIZED
     * SHARED SYMBOL TABLES</b> since that scenario must preserve all sids.
     *
     * @param priorSymtab may be null.
     */
    public static SymbolTable newSharedSymtab(String name,
                                              int version,
                                              SymbolTable priorSymtab,
                                              Iterator<String> symbols)
    {
        return SharedSymbolTable.newSharedSymbolTable(name,
                                                      version,
                                                      priorSymtab,
                                                      symbols);
    }


    /**
     * Creates a mutable copy of this local symbol table. The cloned table
     * will be created in the context of the same {@link ValueFactory}.
     * <p>
     * Note that the resulting symbol table holds a distinct, deep copy of the
     * given table, adding symbols on either instances will not modify the
     * other.
     *
     * @param symtab
     *
     * @return a new mutable {@link SymbolTable} instance; not null
     *
     * @throws IllegalArgumentException
     *          if the given table is not a local symbol table
     * @throws SubstituteSymbolTableException
     *          if any imported table by the given local symbol table is a
     *          substituted table (whereby no exact match was found in its
     *          catalog)
     */
    // TODO We need to think about providing a suitable recovery process
    //      or configuration for users to properly handle the case when the
    //      local symtab has substituted symtabs for imports.
    public static SymbolTable copyLocalSymbolTable(SymbolTable symtab)
        throws SubstituteSymbolTableException
    {
        if (! symtab.isLocalTable())
        {
            String message = "symtab should be a local symtab";
            throw new IllegalArgumentException(message);
        }

        SymbolTable[] imports =
            ((LocalSymbolTable) symtab).getImportedTablesNoCopy();

        // Iterate over each import, we assume that the list of imports
        // rarely exceeds 5.
        for (int i = 0; i < imports.length; i++)
        {
            if (imports[i].isSubstitute())
            {
                String message =
                    "local symtabs with substituted symtabs for imports " +
                    "(indicating no exact match within the catalog) cannot " +
                    "be copied";
                throw new SubstituteSymbolTableException(message);
            }
        }

        return ((LocalSymbolTable) symtab).makeCopy();
    }

    /**
     * Trampoline to {@link LocalSymbolTableAsStruct.Factory#Factory(ValueFactory)}
     * @param imageFactory
     *          the ValueFactory from which to construct the IonStruct representation of the LST
     * @return a new {@link LocalSymbolTableAsStruct.Factory}
     * @deprecated due to DOM entanglement. Streaming applications should use
     *             {@value LocalSymbolTable#DEFAULT_LST_FACTORY}.
     */
    @Deprecated
    public static _Private_LocalSymbolTableFactory newLocalSymbolTableAsStructFactory(ValueFactory imageFactory)
    {
        return new LocalSymbolTableAsStruct.Factory(imageFactory);
    }

    /**
     * Returns a minimal symtab that, either system or local depending on the
     * given values, that supports representation as an IonStruct. If the
     * imports are empty, the default system symtab is returned.
     *
     * @param lstFactory
     *          the factory to use to build the local symbol table, never null
     * @param defaultSystemSymtab
     *          the default system symtab, which will be used if the first
     *          import in {@code imports} isn't a system symtab, never null
     * @param imports
     * the set of shared symbol tables to import; may be null or empty.
     * The first (and only the first) may be a system table, in which case the
     * {@code defaultSystemSymtab} is ignored.
     */
    public static SymbolTable initialSymtab(_Private_LocalSymbolTableFactory lstFactory,
                                            SymbolTable defaultSystemSymtab,
                                            SymbolTable... imports)
    {
        if (imports == null || imports.length == 0)
        {
            return defaultSystemSymtab;
        }

        if (imports.length == 1 && imports[0].isSystemTable())
        {
            return imports[0];
        }

        return lstFactory.newLocalSymtab(defaultSystemSymtab, imports);
    }


    @SuppressWarnings("deprecation")
    /**
     * Trampoline to
     * {@link LocalSymbolTableAsStruct#getIonRepresentation()};
     */
    public static IonStruct symtabTree(SymbolTable symtab, ValueFactory valueFactory)
    {
        LocalSymbolTableAsStruct localSymbolTableAsStruct;
        if (symtab instanceof  LocalSymbolTableAsStruct) {
            localSymbolTableAsStruct = (LocalSymbolTableAsStruct) symtab;
        } else {
            localSymbolTableAsStruct = (LocalSymbolTableAsStruct) new LocalSymbolTableAsStruct.Factory(valueFactory)
                    .newLocalSymtab(symtab.getSystemSymbolTable(), symtab.getImportedTables());
        }
        return localSymbolTableAsStruct.getIonRepresentation();
    }

    /**
     * Determines, for two local symbol tables, whether the passed-in {@code superset} symtab is an extension
     * of {@code subset}.  This works independent of implementation details--particularly in cases
     * where {@link LocalSymbolTable#symtabExtends(SymbolTable)} cannot be used.
     *
     * @see #symtabExtends(SymbolTable, SymbolTable)
     */
    private static boolean localSymtabExtends(SymbolTable superset, SymbolTable subset)
    {
        if (subset.getMaxId() > superset.getMaxId())
        {
            // the subset has more symbols
            return false;
        }

        // NB this API almost certainly requires cloning--symbol table's API doesn't give us a way to polymorphically
        //    get this without materializing an array
        final SymbolTable[] supersetImports     = superset.getImportedTables();
        final SymbolTable[] subsetImports       = subset.getImportedTables();

        // TODO this is over-strict, but not as strict as LocalSymbolTable.symtabExtends()
        if (supersetImports.length != subsetImports.length)
        {
            return false;
        }
        // NB we cannot trust Arrays.equals--we don't know how an implementation will implement it...
        for (int i = 0; i < supersetImports.length; i++)
        {
            final SymbolTable supersetImport = supersetImports[i];
            final SymbolTable subsetImport = subsetImports[i];
            if (!supersetImport.getName().equals(subsetImport.getName())
                 || supersetImport.getVersion() != subsetImport.getVersion())
            {
                // bad match on import
                return false;
            }
        }

        // all the imports lined up, lets make sure the locals line up too
        final Iterator<String> supersetIter     = superset.iterateDeclaredSymbolNames();
        final Iterator<String> subsetIter       = subset.iterateDeclaredSymbolNames();
        while (subsetIter.hasNext())
        {
            final String nextSubsetSymbol       = subsetIter.next();
            final String nextSupersetSymbol     = supersetIter.next();
            if (!nextSubsetSymbol.equals(nextSupersetSymbol))
            {
                // local symbol mismatch
                return false;
            }
        }

        // we made it this far--superset is really a superset of subset
        return true;
    }

    /**
     * Determines whether the passed-in {@code superset} symtab is an extension
     * of {@code subset}.
     * <p>
     * If both are LSTs, their imported tables and locally declared symbols are
     * exhaustively checked, which can be expensive. Callers of this method
     * should cache the results of these comparisons.
     *
     * @param superset
     *                  either a system or local symbol table
     * @param subset
     *                  either a system or local symbol table
     *
     * @return true if {@code superset} extends {@code subset}, false if not
     */
    public static boolean symtabExtends(SymbolTable superset, SymbolTable subset)
    {
        assert superset.isSystemTable() || superset.isLocalTable();
        assert subset.isSystemTable() || subset.isLocalTable();

        // NB: system symtab 1.0 is a singleton, hence if both symtabs
        //     are one this will be true.
        if (superset == subset) return true;

        // If the subset's symtab is a system symtab, the superset's is always
        // an extension of the subset's as system symtab-ness is irrelevant to
        // the conditions for copy opt. to be safe.
        // TODO amzn/ion-java/issues/24 System symtab-ness ARE relevant if there's multiple
        //      versions.
        if (subset.isSystemTable()) return true;

        // From here on, subset is a LST because isSystemTable() is false.

        if (superset.isLocalTable())
        {
            if (superset instanceof LocalSymbolTable && subset instanceof LocalSymbolTable)
            {
                // use the internal comparison
                return ((LocalSymbolTable) superset).symtabExtends(subset);
            }
            // TODO reason about symbol tables that don't extend LocalSymbolTable but are still local
            return localSymtabExtends(superset, subset);
        }

        // From here on, superset is a system symtab.

        // If LST subset has no local symbols or imports, and it's system
        // symbols are same as those of system symtab superset's, then
        // superset extends subset
        return subset.getMaxId() == superset.getMaxId();
    }


    /**
     * Determines whether the passed-in data type is a scalar and not a symbol.
     */
    public static boolean isNonSymbolScalar(IonType type)
    {
        return ! IonType.isContainer(type) && ! type.equals(IonType.SYMBOL);
    }


    /**
     * Returns the symbol ID matching a system symbol text of a
     * local or shared symtab field.
     */
    public static final int getSidForSymbolTableField(String text)
    {
        final int shortestFieldNameLength = 4; // 'name'

        if (text != null && text.length() >= shortestFieldNameLength)
        {
            int c = text.charAt(0);
            switch (c)
            {
                case 'v':
                    if (VERSION.equals(text))
                    {
                        return VERSION_SID;
                    }
                    break;
                case 'n':
                    if (NAME.equals(text))
                    {
                        return NAME_SID;
                    }
                    break;
                case 's':
                    if (SYMBOLS.equals(text))
                    {
                        return  SYMBOLS_SID;
                    }
                    break;

                case 'i':
                    if (IMPORTS.equals(text))
                    {
                        return IMPORTS_SID;
                    }
                    break;
                case 'm':
                    if (MAX_ID.equals(text))
                    {
                        return MAX_ID_SID;
                    }
                    break;
                default:
                    break;
            }
        }
        return UNKNOWN_SYMBOL_ID;
    }


    //========================================================================


    /**
     * Private to route clients through the static methods, which can
     * optimize the empty-list case.
     */
    private static final class StringIterator implements Iterator<String>
    {
        private final String[] _values;
        private int            _pos;
        private final int      _len;

        StringIterator(String[] values, int len) {
            _values = values;
            _len = len;
        }
        public boolean hasNext() {
            return (_pos < _len);
        }
        public String next() {
            if (!hasNext()) throw new NoSuchElementException();
            return _values[_pos++];
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static final Iterator<String> stringIterator(String... values)
    {
        if (values == null || values.length == 0)
        {
            return _Private_Utils.<String>emptyIterator();
        }
        return new StringIterator(values, values.length);
    }

    public static final Iterator<String> stringIterator(String[] values, int len)
    {
        if (values == null || values.length == 0 || len == 0)
        {
            return _Private_Utils.<String>emptyIterator();
        }
        return new StringIterator(values, len);
    }

    /**
     * Private to route clients through the static methods, which can
     * optimize the empty-list case.
     */
    private static final class IntIterator implements Iterator<Integer>
    {
        private final int []  _values;
        private int           _pos;
        private final int     _len;

        IntIterator(int[] values) {
            this(values, 0, values.length);
        }
        IntIterator(int[] values, int off, int len) {
            _values = values;
            _len = len;
            _pos = off;
        }
        public boolean hasNext() {
            return (_pos < _len);
        }
        public Integer next() {
            if (!hasNext()) throw new NoSuchElementException();
            int value = _values[_pos++];
            return value;
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static final Iterator<Integer> intIterator(int... values)
    {
        if (values == null || values.length == 0)
        {
            return _Private_Utils.<Integer>emptyIterator();
        }
        return new IntIterator(values);
    }

    public static final Iterator<Integer> intIterator(int[] values, int len)
    {
        if (values == null || values.length == 0 || len == 0)
        {
            return _Private_Utils.<Integer>emptyIterator();
        }
        return new IntIterator(values, 0, len);
    }

    public static void writeAsBase64(InputStream byteStream, Appendable out)
        throws IOException
    {
        Base64Encoder.TextStream ts = new Base64Encoder.TextStream(byteStream);

        for (;;) {
            int c = ts.read();
            if (c == -1) break;
            out.append((char) c);
        }
    }

    public static SymbolTable newSubstituteSymtab(SymbolTable original,
                                                  int version,
                                                  int maxId)
    {
        return new SubstituteSymbolTable(original, version, maxId);
    }

}
