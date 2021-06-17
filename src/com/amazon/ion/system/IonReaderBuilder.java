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

import static com.amazon.ion.impl._Private_IonReaderFactory.makeIncrementalReader;
import static com.amazon.ion.impl._Private_IonReaderFactory.makeReader;

import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonTextReader;
import com.amazon.ion.IonValue;
import com.amazon.ion.impl._Private_IonConstants;
import com.amazon.ion.util.IonStreamUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Build a new {@link IonReader} from the given {@link IonCatalog} and data
 * source. A data source is required, while an IonCatalog is optional. If no
 * IonCatalog is provided, an empty {@link SimpleCatalog} will be used.
 * <p>
 * {@link IonReader}s parse incrementally, so syntax errors in the input data
 * will not be detected as side effects of any of the {@code build} methods
 * in this class.
 */
@SuppressWarnings("deprecation")
public class IonReaderBuilder
{

    private IonCatalog catalog = null;
    private boolean isIncrementalReadingEnabled = false;
    private IonBufferConfiguration bufferConfiguration = null;

    private IonReaderBuilder()
    {
    }

    private IonReaderBuilder(IonReaderBuilder that)
    {
        this.catalog = that.catalog;
        this.isIncrementalReadingEnabled = that.isIncrementalReadingEnabled;
        this.bufferConfiguration = that.bufferConfiguration;
    }

    /**
     * The standard builder of {@link IonReader}s, with all configuration
     * properties having their default values.
     *
     * @return a new, mutable builder instance.
     */
    public static IonReaderBuilder standard()
    {
        return new Mutable();
    }

    /**
     * Creates a mutable copy of this builder.
     *
     * @return a new builder with the same configuration as {@code this}.
     */
    public IonReaderBuilder copy()
    {
        return new Mutable(this);
    }

    /**
     * Returns an immutable builder configured exactly like this one.
     *
     * @return this builder instance, if immutable;
     * otherwise an immutable copy of this builder.
     */
    public IonReaderBuilder immutable()
    {
        return this;
    }

    /**
     * Returns a mutable builder configured exactly like this one.
     *
     * @return this instance, if mutable;
     * otherwise a mutable copy of this instance.
     */
    public IonReaderBuilder mutable()
    {
        return copy();
    }

    /** NOT FOR APPLICATION USE! */
    protected void mutationCheck()
    {
        throw new UnsupportedOperationException("This builder is immutable");
    }

    /**
     * Declares the catalog to use when building an {@link IonReader},
     * returning a new mutable builder the current one is immutable.
     *
     * @param catalog the catalog to use in built readers.
     *  If null, a new {@link SimpleCatalog} will be used.
     *
     * @return this builder instance, if mutable;
     * otherwise a mutable copy of this builder.
     *
     * @see #setCatalog(IonCatalog)
     * @see #withCatalog(IonCatalog)
     */
    public IonReaderBuilder withCatalog(IonCatalog catalog)
    {
        IonReaderBuilder b = mutable();
        b.setCatalog(catalog);
        return b;
    }

    /**
     * Sets the catalog to use when building an {@link IonReader}.
     *
     * @param catalog the catalog to use in built readers.
     *  If null, a new {@link SimpleCatalog} will be used.
     *
     * @see #getCatalog()
     * @see #withCatalog(IonCatalog)
     *
     * @throws UnsupportedOperationException if this builder is immutable.
     */
    public void setCatalog(IonCatalog catalog)
    {
        mutationCheck();
        this.catalog = catalog;
    }

    /**
     * Gets the catalog to use when building an {@link IonReader}, or null
     * if none has been manually set. The catalog is needed to resolve shared
     * symbol table imports.
     *
     * @see #setCatalog(IonCatalog)
     * @see #withCatalog(IonCatalog)
     */
    public IonCatalog getCatalog()
    {
        return catalog;
    }

    private IonCatalog validateCatalog()
    {
        // matches behavior in IonSystemBuilder when no catalog provided
        return catalog != null ? catalog : new SimpleCatalog();
    }

    /**
     * <p>
     * Determines whether the IonReader will allow incremental reading of binary Ion data. When enabled, if
     * {@link IonReader#next()} returns {@code null} at the top-level, it indicates that there is not enough data
     * in the stream to complete a top-level value. The user may wait for more data to become available in the stream
     * and call {@link IonReader#next()} again to continue reading. Unlike the non-incremental reader, the incremental
     * reader will never throw an exception due to unexpected EOF during {@code next()}. If, however,
     * {@link IonReader#close()} is called when an incomplete value is buffered, an {@link IonException} will be raised.
     * </p>
     * <p>
     * There is no incremental text IonReader, so for text data a non-incremental IonReader will always be returned
     * regardless of the value of this option.
     * </p>
     * </p>
     * When this option is enabled, auto-detection of GZIP data is not supported; the byte array or InputStream
     * implementation provided to {@link #build(byte[])} or {@link #build(InputStream)} must return uncompressed bytes.
     * This can be achieved by wrapping the data in a GZIPInputStream and passing it to {@link #build(InputStream)}.
     * </p>
     * <p>
     * Although the incremental binary IonReader provides performance superior to the non-incremental binary IonReader
     * for both incremental and non-incremental use cases, there is one caveat: the incremental implementation
     * must be able to buffer an entire top-level value and any preceding system values (Ion version marker(s) and
     * symbol table(s)) in memory. This will not be a problem for the vast majority of Ion streams, as it is rare for a
     * single top-level value or symbol table to exceed a few megabytes in size. However, if the size of the stream's
     * values risk exceeding the available memory, then this option must not be enabled.
     * </p>
     * @param isEnabled true if the option is enabled; otherwise, false.
     *
     * @return this builder instance, if mutable;
     * otherwise a mutable copy of this builder.
     *
     * @see #setIncrementalReadingEnabled()
     * @see #setIncrementalReadingDisabled()
     */
    public IonReaderBuilder withIncrementalReadingEnabled(boolean isEnabled) {
        IonReaderBuilder b = mutable();
        if (isEnabled) {
            b.setIncrementalReadingEnabled();
        } else {
            b.setIncrementalReadingDisabled();
        }
        return b;
    }

    /**
     * @see #withIncrementalReadingEnabled(boolean)
     */
    public void setIncrementalReadingEnabled() {
        mutationCheck();
        isIncrementalReadingEnabled = true;
    }

    /**
     * @see #withIncrementalReadingEnabled(boolean)
     */
    public void setIncrementalReadingDisabled() {
        mutationCheck();
        isIncrementalReadingEnabled = false;
    }

    /**
     * @see #withIncrementalReadingEnabled(boolean)
     * @return true if incremental reading is enabled; otherwise, false.
     */
    public boolean isIncrementalReadingEnabled() {
        return isIncrementalReadingEnabled;
    }

    /**
     * Sets the buffer configuration. This can be used, for example, to set a maximum buffer size
     * and receive notifications when values would exceed this size. Currently, this is ignored unless incremental
     * reading has been enabled via {@link #withIncrementalReadingEnabled(boolean)}) or
     * {@link #setIncrementalReadingEnabled()}. This configuration is optional. If not provided, the buffer size will
     * be limited only by the available memory.
     *
     * @param configuration the configuration.
     *
     * @return this builder instance, if mutable;
     * otherwise a mutable copy of this builder.
     * 
     * @see #setBufferConfiguration(IonBufferConfiguration)
     */
    public IonReaderBuilder withBufferConfiguration(IonBufferConfiguration configuration) {
        IonReaderBuilder b = mutable();
        b.setBufferConfiguration(configuration);
        return b;
    }

    /**
     * @see #withBufferConfiguration(IonBufferConfiguration)
     */
    public void setBufferConfiguration(IonBufferConfiguration configuration) {
        mutationCheck();
        bufferConfiguration = configuration;
    }

    /**
     * @see #withBufferConfiguration(IonBufferConfiguration)
     * @return the current configuration.
     */
    public IonBufferConfiguration getBufferConfiguration() {
        return bufferConfiguration;
    }

    /**
     * Based on the builder's configuration properties, creates a new IonReader
     * instance over the given block of Ion data, detecting whether it's text or
     * binary data.
     * <p>
     * This method will auto-detect and uncompress GZIPped Ion data.
     *
     * @param ionData the source of the Ion data, which may be either Ion binary
     * data or UTF-8 Ion text. The reader retains a reference to the array, so
     * its data must not be modified while the reader is active. Must not be
     * null.
     *
     * @return a new {@link IonReader} instance; not {@code null}.
     *
     * @see IonSystem#newReader(byte[])
     */
    public IonReader build(byte[] ionData)
    {
        return build(ionData, 0, ionData.length);
    }

    /**
     * Based on the builder's configuration properties, creates a new IonReader
     * instance over the given block of Ion data, detecting whether it's text or
     * binary data.
     * <p>
     * This method will auto-detect and uncompress GZIPped Ion data.
     *
     * @param ionData the source of the Ion data, which is used only within the
     * range of bytes starting at {@code offset} for {@code len} bytes.
     * The data in that range may be either Ion binary data or UTF-8 Ion text.
     * The reader retains a reference to the array, so its data must not be
     * modified while the reader is active. Must not be null.
     * @param offset must be non-negative and less than {@code ionData.length}.
     * @param length must be non-negative and {@code offset+length} must not
     * exceed {@code ionData.length}.
     *
     * @see IonSystem#newReader(byte[], int, int)
     */
    public IonReader build(byte[] ionData, int offset, int length)
    {
        if (isIncrementalReadingEnabled) {
            if (IonStreamUtils.isGzip(ionData, offset, length)) {
                throw new IllegalArgumentException("Automatic GZIP detection is not supported with incremental" +
                        "support enabled. Wrap the bytes with a GZIPInputStream and call build(InputStream).");
            }
            if (IonStreamUtils.isIonBinary(ionData, offset, length)) {
                return makeIncrementalReader(
                    validateCatalog(),
                    new ByteArrayInputStream(ionData, offset, length),
                    bufferConfiguration
                );
            }
        }
        return makeReader(validateCatalog(), ionData, offset, length);
    }

    /**
     * Based on the builder's configuration properties, creates a new IonReader
     * instance over the given stream of Ion data, detecting whether it's text or
     * binary data.
     * <p>
     * This method will auto-detect and uncompress GZIPped Ion data.
     * <p>
     * Because this library performs its own buffering, it's recommended that
     * users avoid adding additional buffering to the given stream.
     *
     * @param ionData the source of the Ion data, which may be either Ion binary
     * data or UTF-8 Ion text. Must not be null.
     *
     * @return a new reader instance.
     * Callers must call {@link IonReader#close()} when finished with it.
     *
     * @throws IonException if the source throws {@link IOException}.
     *
     * @see IonSystem#newReader(InputStream)
     */
    public IonReader build(InputStream ionData)
    {
        InputStream wrapper = ionData;
        if (isIncrementalReadingEnabled) {
            if (!ionData.markSupported()) {
                wrapper = new BufferedInputStream(ionData);
            }
            wrapper.mark(_Private_IonConstants.BINARY_VERSION_MARKER_SIZE);
            byte[] possibleIVM = new byte[_Private_IonConstants.BINARY_VERSION_MARKER_SIZE];
            int bytesRead;
            try {
                bytesRead = wrapper.read(possibleIVM);
                wrapper.reset();
            } catch (IOException e) {
                throw new IonException(e);
            }
            if (IonStreamUtils.isGzip(possibleIVM, 0, possibleIVM.length)) {
                throw new IllegalArgumentException("Automatic GZIP detection is not supported with incremental" +
                        "support enabled. Wrap the bytes with a GZIPInputStream and call build(InputStream).");
            }
            if (possibleIVM.length == bytesRead && IonStreamUtils.isIonBinary(possibleIVM)) {
                return makeIncrementalReader(validateCatalog(), wrapper, bufferConfiguration);
            }
        }
        return makeReader(validateCatalog(), wrapper);
    }

    /**
     * Based on the builder's configuration properties, creates a new
     * {@link IonReader} instance over Ion text data.
     * <p>
     * Applications should generally use {@link #build(InputStream)}
     * whenever possible, since this library has much faster Unicode decoding
     * than the Java IO framework.
     * <p>
     * Because this library performs its own buffering, it's recommended that
     * you avoid adding additional buffering to the given stream.
     *
     * @param ionText the source of the Ion text data. Must not be null.
     *
     * @throws IonException if the source throws {@link IOException}.
     *
     * @see IonSystem#newReader(Reader)
     */
    public IonReader build(Reader ionText)
    {
        return makeReader(validateCatalog(), ionText);
    }

    /**
     * Based on the builder's configuration properties, creates a new
     * {@link IonReader} instance over an {@link IonValue} data model. Typically
     * this is used to iterate over a collection, such as an {@link IonStruct}.
     *
     * The given value and its children, if any, must not be modified until after
     * the IonReader constructed by this method is closed. Violating this
     * constraint results in undefined behavior.
     *
     * @param value must not be null.
     *
     * @see IonSystem#newReader(IonValue)
     */
    public IonReader build(IonValue value)
    {
        return makeReader(validateCatalog(), value);
    }

    /**
     * Based on the builder's configuration properties, creates an new
     * {@link IonReader} instance over Ion text data.
     *
     * @param ionText the source of the Ion text data. Must not be null.
     *
     * @see IonSystem#newReader(String)
     */
    public IonTextReader build(String ionText)
    {
        return makeReader(validateCatalog(), ionText);
    }

    private static class Mutable extends IonReaderBuilder
    {

        private Mutable()
        {
        }

        private Mutable(IonReaderBuilder that)
        {
            super(that);
        }

        @Override
        public IonReaderBuilder immutable()
        {
            return new IonReaderBuilder(this);
        }

        @Override
        public IonReaderBuilder mutable()
        {
            return this;
        }

        @Override
        protected void mutationCheck()
        {
        }

    }

}
