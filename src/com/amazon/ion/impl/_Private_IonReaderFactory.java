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

import static com.amazon.ion.impl.UnifiedInputStreamX.makeStream;
import static com.amazon.ion.impl._Private_IonConstants.BINARY_VERSION_MARKER_SIZE;
import static com.amazon.ion.util.IonStreamUtils.isIonBinary;

import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonTextReader;
import com.amazon.ion.IonValue;
import com.amazon.ion.util.IonStreamUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.zip.GZIPInputStream;

/**
 * NOT FOR APPLICATION USE!
 */
@SuppressWarnings("deprecation")
public final class _Private_IonReaderFactory
{

    public static final IonReader makeReader(IonCatalog catalog,
                                             byte[] bytes)
    {
        return makeReader(catalog, bytes, 0, bytes.length);
    }

    public static final IonReader makeReader(IonCatalog catalog,
                                             byte[] bytes,
                                             _Private_LocalSymbolTableFactory lstFactory)
    {
        return makeReader(catalog, bytes, 0, bytes.length, lstFactory);
    }

    public static IonReader makeSystemReader(byte[] bytes)
    {
        return makeSystemReader(bytes, 0, bytes.length);
    }


    public static final IonReader makeReader(IonCatalog catalog,
                                             byte[] bytes,
                                             int offset,
                                             int length)
    {
        try
        {
            UnifiedInputStreamX uis = makeUnifiedStream(bytes, offset, length);
            return makeReader(catalog, uis, offset, LocalSymbolTable.DEFAULT_LST_FACTORY);
        }
        catch (IOException e)
        {
            throw new IonException(e);
        }
    }

    public static final IonReader makeReader(IonCatalog catalog,
                                             byte[] bytes,
                                             int offset,
                                             int length,
                                             _Private_LocalSymbolTableFactory lstFactory)
    {
        try
        {
            UnifiedInputStreamX uis = makeUnifiedStream(bytes, offset, length);
            return makeReader(catalog, uis, offset, lstFactory);
        }
        catch (IOException e)
        {
            throw new IonException(e);
        }
    }

    public static IonReader makeSystemReader(byte[] bytes,
                                             int offset,
                                             int length)
    {
        try
        {
            UnifiedInputStreamX uis = makeUnifiedStream(bytes, offset, length);
            return makeSystemReader(uis);
        }
        catch (IOException e)
        {
            throw new IonException(e);
        }
    }


    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 char[] chars)
    {
        return makeReader(catalog, chars, 0, chars.length);
    }

    public static final IonReader makeSystemReader(char[] chars)
    {
        UnifiedInputStreamX in = makeStream(chars);
        return new IonReaderTextSystemX(in);
    }


    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 char[] chars,
                                                 int offset,
                                                 int length)
    {
        UnifiedInputStreamX in = makeStream(chars, offset, length);
        return new IonReaderTextUserX(catalog, LocalSymbolTable.DEFAULT_LST_FACTORY, in, offset);
    }

    public static final IonReader makeSystemReader(char[] chars,
                                                   int offset,
                                                   int length)
    {
        UnifiedInputStreamX in = makeStream(chars, offset, length);
        return new IonReaderTextSystemX(in);
    }


    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 CharSequence chars)
    {
        return makeReader(catalog, chars, LocalSymbolTable.DEFAULT_LST_FACTORY);
    }

    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 CharSequence chars,
                                                 _Private_LocalSymbolTableFactory lstFactory)
    {
        UnifiedInputStreamX in = makeStream(chars);
        return new IonReaderTextUserX(catalog, lstFactory, in);
    }

    public static final IonReader makeSystemReader(CharSequence chars)
    {
        UnifiedInputStreamX in = makeStream(chars);
        return new IonReaderTextSystemX(in);
    }


    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 CharSequence chars,
                                                 int offset,
                                                 int length)
    {
        UnifiedInputStreamX in = makeStream(chars, offset, length);
        return new IonReaderTextUserX(catalog, LocalSymbolTable.DEFAULT_LST_FACTORY, in, offset);
    }

    public static final IonReader makeSystemReader(CharSequence chars,
                                                   int offset,
                                                   int length)
    {
        UnifiedInputStreamX in = makeStream(chars, offset, length);
        return new IonReaderTextSystemX(in);
    }


    public static final IonReader makeReader(IonCatalog catalog,
                                             InputStream is)
    {
        return makeReader(catalog, is, LocalSymbolTable.DEFAULT_LST_FACTORY);
    }

    public static final IonReader makeReader(IonCatalog catalog,
                                             InputStream is,
                                             _Private_LocalSymbolTableFactory lstFactory)
    {
        try {
            UnifiedInputStreamX uis = makeUnifiedStream(is);
            return makeReader(catalog, uis, 0, lstFactory);
        }
        catch (IOException e) {
            throw new IonException(e);
        }
    }

    public static IonReader makeSystemReader(InputStream is)
    {
        try {
            UnifiedInputStreamX uis = makeUnifiedStream(is);
            return makeSystemReader(uis);
        }
        catch (IOException e) {
            throw new IonException(e);
        }
    }


    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 Reader chars)
    {
        return makeReader(catalog, chars, LocalSymbolTable.DEFAULT_LST_FACTORY);
    }

    public static final IonTextReader makeReader(IonCatalog catalog,
                                                 Reader chars,
                                                 _Private_LocalSymbolTableFactory lstFactory)
    {
        try {
            UnifiedInputStreamX in = makeStream(chars);
            return new IonReaderTextUserX(catalog, lstFactory, in);
        }
        catch (IOException e) {
            throw new IonException(e);
        }
    }

    public static final IonReader makeSystemReader(Reader chars)
    {
        try {
            UnifiedInputStreamX in = makeStream(chars);
            return new IonReaderTextSystemX(in);
        }
        catch (IOException e) {
            throw new IonException(e);
        }
    }

    public static final IonReader makeReader(IonCatalog catalog,
                                             IonValue value)
    {
        return new IonReaderTreeUserX(value, catalog, LocalSymbolTable.DEFAULT_LST_FACTORY);
    }

    public static final IonReader makeSystemReader(IonSystem system,
                                                   IonValue value)
    {
        if (system != null && system != value.getSystem()) {
            throw new IonException("you can't mix values from different systems");
        }
        return new IonReaderTreeSystem(value);
    }

    public static final IonReader makeIncrementalReader(IonCatalog catalog,
                                                        InputStream is,
                                                        IonBufferConfiguration configuration)
    {
        return new IonReaderBinaryIncremental(is, catalog, configuration);
    }


    //=========================================================================



    private static IonReader makeReader(IonCatalog catalog,
                                        UnifiedInputStreamX uis,
                                        int offset,
                                        _Private_LocalSymbolTableFactory lstFactory)
        throws IOException
    {
        IonReader r;
        if (has_binary_cookie(uis)) {
            r = new IonReaderBinaryUserX(catalog, lstFactory, uis, offset);
        }
        else {
            r = new IonReaderTextUserX(catalog, lstFactory, uis, offset);
        }
        return r;
    }

    private static IonReader makeSystemReader(UnifiedInputStreamX uis)
        throws IOException
    {
        IonReader r;
        if (has_binary_cookie(uis)) {
            // TODO pass offset, or spans will be incorrect
            r = new IonReaderBinarySystemX(uis);
        }
        else {
            // TODO pass offset, or spans will be incorrect
            r = new IonReaderTextSystemX(uis);
        }
        return r;
    }

    //
    //  helper functions
    //

    private static UnifiedInputStreamX makeUnifiedStream(byte[] bytes,
                                                         int offset,
                                                         int length)
        throws IOException
    {
        UnifiedInputStreamX uis;
        if (IonStreamUtils.isGzip(bytes, offset, length))
        {
            ByteArrayInputStream baos =
                new ByteArrayInputStream(bytes, offset, length);
            GZIPInputStream gzip = new GZIPInputStream(baos);
            uis = UnifiedInputStreamX.makeStream(gzip);
        }
        else
        {
            uis = UnifiedInputStreamX.makeStream(bytes, offset, length);
        }
        return uis;
    }

    private static UnifiedInputStreamX makeUnifiedStream(InputStream in)
        throws IOException
    {
        in.getClass(); // Force NPE

        // TODO avoid multiple wrapping streams, use the UIS for the pushback
        in = IonStreamUtils.unGzip(in);
        UnifiedInputStreamX uis = UnifiedInputStreamX.makeStream(in);
        return uis;
    }

    private static final boolean has_binary_cookie(UnifiedInputStreamX uis)
        throws IOException
    {
        byte[] bytes = new byte[BINARY_VERSION_MARKER_SIZE];

        // try to read the first 4 bytes and unread them (we want
        // the data stream undisturbed by our peeking ahead)
        int len;
        for (len = 0; len < BINARY_VERSION_MARKER_SIZE; len++) {
            int c = uis.read();
            if (c == UnifiedInputStreamX.EOF) {
                break;
            }
            bytes[len] = (byte)c;
        }
        for (int ii=len; ii>0; ) {
            ii--;
            uis.unread(bytes[ii] & 0xff);
        }
        boolean is_cookie = isIonBinary(bytes, 0, len);
        return is_cookie;
    }
}
