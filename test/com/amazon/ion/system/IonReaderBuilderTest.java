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

package com.amazon.ion.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.impl._Private_IonBinaryWriterBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import com.amazon.ion.impl._Private_IonConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Note: because the IonReaderBuilder is used by IonSystem.newReader(...),
 * its build() methods are well-exercised elsewhere. See: ReaderMaker.
 */
public class IonReaderBuilderTest
{

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testMutable()
    {
        IonCatalog catalog = new SimpleCatalog();
        IonReaderBuilder mutable = IonReaderBuilder.standard().withCatalog(catalog);
        assertSame(catalog, mutable.getCatalog());
        IonReaderBuilder mutableSame = mutable.withCatalog(new SimpleCatalog());
        assertNotSame(catalog, mutable.getCatalog());
        assertSame(mutable, mutableSame);
    }

    @Test
    public void testImmutable()
    {
        IonCatalog catalog = new SimpleCatalog();
        IonReaderBuilder mutable = IonReaderBuilder.standard().withCatalog(catalog);
        IonReaderBuilder immutable = mutable.immutable();
        mutable.withCatalog(new SimpleCatalog());
        assertNotSame(catalog, mutable.getCatalog());
        assertSame(catalog, immutable.getCatalog());
    }

    @Test
    public void testMutatingImmutableFails()
    {
        IonReaderBuilder immutable = IonReaderBuilder.standard().immutable();
        thrown.expect(UnsupportedOperationException.class);
        immutable.setCatalog(new SimpleCatalog());
    }

    @Test
    public void testMutateCopiedImmutable()
    {
        IonCatalog catalog = new SimpleCatalog();
        IonReaderBuilder immutable = IonReaderBuilder.standard().withCatalog(catalog).immutable();
        IonReaderBuilder mutableCopy = immutable.copy();
        assertSame(immutable, immutable.immutable());
        assertNotSame(immutable, mutableCopy);
        assertSame(catalog, mutableCopy.getCatalog());
        mutableCopy.withCatalog(new SimpleCatalog());
        assertNotSame(catalog, mutableCopy.getCatalog());
    }

    @Test
    public void testMutateCopiedMutable()
    {
        IonCatalog catalog = new SimpleCatalog();
        IonReaderBuilder mutable = IonReaderBuilder.standard().withCatalog(catalog);
        IonReaderBuilder mutableCopy = mutable.copy();
        assertNotSame(mutable, mutable.immutable());
        assertNotSame(mutable, mutableCopy);
        assertSame(mutable, mutable.mutable());
        assertSame(catalog, mutableCopy.getCatalog());
        IonReaderBuilder mutableSame = mutableCopy.withCatalog(new SimpleCatalog());
        assertNotSame(catalog, mutableCopy.getCatalog());
        assertSame(mutableCopy, mutableSame);
    }

    @Test
    public void testSystemFreeRoundtrip() throws IOException
    {
        // No IonSystem in sight.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = _Private_IonBinaryWriterBuilder.standard().build(out);
        writer.writeInt(42);
        writer.finish();
        IonReader reader = IonReaderBuilder.standard().build(out.toByteArray());
        assertEquals(IonType.INT, reader.next());
        assertEquals(42, reader.intValue());
    }

    @Test
    public void testEnableIncrementalReading() throws IOException
    {
        IonReaderBuilder builder = IonReaderBuilder.standard();
        assertFalse(builder.isIncrementalReadingEnabled());
        builder.withIncrementalReadingEnabled(true);
        assertTrue(builder.isIncrementalReadingEnabled());
        builder.setIncrementalReadingDisabled();
        assertFalse(builder.isIncrementalReadingEnabled());
        builder.setIncrementalReadingEnabled();
        assertTrue(builder.isIncrementalReadingEnabled());
        builder.withIncrementalReadingEnabled(false);
        assertFalse(builder.isIncrementalReadingEnabled());
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        data.write(0xE5); // 5-byte annotation wrapper (incomplete).
        IonReader reader1 = builder.build(data.toByteArray());
        try {
            reader1.next();
            fail();
        } catch (IonException e) {
            // Expected; this is a non-incremental reader, but a complete value was not available.
        }
        builder.withIncrementalReadingEnabled(true);
        IonReader reader2 = builder.build(data.toByteArray());
        assertNull(reader2.next());
        IonReader reader3 = builder.build(new ByteArrayInputStream(data.toByteArray()));
        assertNull(reader3.next());
    }

    @Test
    public void testBufferConfiguration()
    {
        IonBufferConfiguration configuration1 = IonBufferConfiguration.Builder.standard().build();
        IonBufferConfiguration configuration2 = IonBufferConfiguration.Builder.standard().build();
        IonReaderBuilder builder = IonReaderBuilder.standard();
        assertNull(builder.getBufferConfiguration());
        builder.withBufferConfiguration(configuration1);
        assertSame(configuration1, builder.getBufferConfiguration());
        builder.setBufferConfiguration(configuration2);
        assertSame(configuration2, builder.getBufferConfiguration());
        builder.withBufferConfiguration(null);
        assertNull(builder.getBufferConfiguration());
    }

    @Test
    public void testIncrementalReadingDoesNotSupportAutoGzip() throws IOException
    {
        IonReaderBuilder builder = IonReaderBuilder.standard();
        builder.withIncrementalReadingEnabled(true);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(data);
        gzip.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        gzip.write(0x20); // int 0.
        gzip.close();
        try {
            builder.build(data.toByteArray());
            fail();
        } catch (IllegalArgumentException e) {
            // Expected; non-incremental readers do not support auto-deflate of GZIP.
        }
        try {
            builder.build(new ByteArrayInputStream(data.toByteArray()));
            fail();
        } catch (IllegalArgumentException e) {
            // Expected; non-incremental readers do not support auto-deflate of GZIP.
        }
    }

}
