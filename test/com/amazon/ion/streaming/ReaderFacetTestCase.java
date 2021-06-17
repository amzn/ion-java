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

import static com.amazon.ion.facet.Facets.assumeFacet;
import static com.amazon.ion.util.Spans.currentSpan;

import com.amazon.ion.OffsetSpan;
import com.amazon.ion.ReaderMaker;
import com.amazon.ion.SeekableReader;
import com.amazon.ion.Span;
import com.amazon.ion.SpanProvider;
import com.amazon.ion.TextSpan;
import com.amazon.ion.facet.Facets;
import org.junit.After;


public abstract class ReaderFacetTestCase
    extends ReaderTestCase
{
    /**
     * These readers don't support the {@link SpanProvider} facet.
     *
     * @see NonSpanReaderTest
     */
    public static final ReaderMaker[] NON_SPAN_READERS =
    {
        ReaderMaker.FROM_INPUT_STREAM_BINARY_INCREMENTAL
    };

    /**
     * These are the readers that don't support {@link OffsetSpan}s.
     */
    public static final ReaderMaker[] NON_OFFSET_SPAN_READERS;
    static {
        ReaderMaker[] domReaders = ReaderMaker.valuesWith(ReaderMaker.Feature.DOM);
        NON_OFFSET_SPAN_READERS = new ReaderMaker[domReaders.length + 1];
        System.arraycopy(domReaders, 0, NON_OFFSET_SPAN_READERS, 0, domReaders.length);
        NON_OFFSET_SPAN_READERS[domReaders.length] = ReaderMaker.FROM_INPUT_STREAM_BINARY_INCREMENTAL;
    }

    /**
     * These are the readers that don't support {@link TextSpan}s.
     */
    public static final ReaderMaker[] NON_TEXT_SPAN_READERS =
        ReaderMaker.valuesWithout(ReaderMaker.Feature.TEXT);

    /**
     * These readers don't support the {@link SeekableReader} facet.
     *
     * @see NonSeekableReaderTest
     */
    public static final ReaderMaker[] NON_SEEKABLE_READERS =  // TODO amzn/ion-java/issues/17
    {
        ReaderMaker.FROM_INPUT_STREAM_BINARY,
        ReaderMaker.FROM_INPUT_STREAM_TEXT,
        ReaderMaker.FROM_READER,
        ReaderMaker.FROM_INPUT_STREAM_BINARY_INCREMENTAL
    };



    protected SpanProvider sp;
    protected boolean mySpanProviderRequired = true;
    protected SeekableReader sr;
    protected boolean mySeekableReaderRequired = true;


    @After @Override
    public void tearDown() throws Exception
    {
        sp = null;
        sr = null;
        super.tearDown();
    }

    protected void initFacets()
    {
        sp = in.asFacet(SpanProvider.class);
        if (mySpanProviderRequired)
        {
            assertNotNull("SpanProvider not available", sp);
        }

        sr = in.asFacet(SeekableReader.class);
        if (mySeekableReaderRequired)
        {
            assertNotNull("SeekableReader not available", sr);
        }
    }

    @Override
    protected final void read(byte[] ionData)
    {
        super.read(ionData);
        initFacets();
    }

    @Override
    protected final void read(String ionText)
    {
        super.read(ionText);
        initFacets();
    }


    protected void expectNoFacet(Class<?> facetType, Object subject)
    {
        Object facet = Facets.asFacet(facetType, subject);
        if (facet != null)
        {
            fail("Didn't expect facet " + facetType.getSimpleName()
                 + " on " + facet);
        }

        if (facetType.isInstance(subject))
        {
            fail(subject.getClass().getSimpleName()
                 + " shouldn't be instanceof unexpected facet "
                 + facetType.getSimpleName());
        }
    }

    protected void hoist(Span s)
    {
        sr.hoist(s);
        expectTopLevel();
        expectNoCurrentValue();
    }


    protected void checkSpan(int startLine, int startColumn, TextSpan ts)
    {
        assertEquals("startLine",   startLine,   ts.getStartLine());
        assertEquals("startColumn", startColumn, ts.getStartColumn());
        assertEquals("finishLine",   -1, ts.getFinishLine());
        assertEquals("finishColumn", -1, ts.getFinishColumn());
    }


    private void checkSpan(long start, long finish, OffsetSpan offsets)
    {
        assertEquals("startOffset",  start,  offsets.getStartOffset());
        assertEquals("finishOffset", finish, offsets.getFinishOffset());
    }


    protected void checkCurrentSpan(long start, long finish)
    {
        Span span = sp.currentSpan();

        OffsetSpan offsets = assumeFacet(OffsetSpan.class, span);
        checkSpan(start, finish, offsets);

        offsets = currentSpan(OffsetSpan.class, in);
        checkSpan(start, finish, offsets);
    }
}
