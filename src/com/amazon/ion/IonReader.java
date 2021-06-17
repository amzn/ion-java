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

import com.amazon.ion.facet.Faceted;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Iterator;

/* One design goal is for the readers and writers to be independent of an
 * IonSystem or ValueFactory and thus independent of particular implementations
 * of the DOM.
 *
 * The issue is that one needs a ValueFactory in order to construct the tree.
 * So one either needs to pass a ValueFactory / IonSystem to the reader, or
 * pass the reader to the system.  I decided that the dependencies were better
 * the latter way.  So we have IonSystem.newValue(IonReader) instead of
 * IonReader.nextValue(IonSystem).
 */

/**
 * Provides stream-based access to Ion data independent of its underlying
 * representation (text, binary, or {@link IonValue} tree).
 * <p>
 * <b>WARNING:</b> This interface should not be implemented or extended by
 * code outside of this library.
 * We still have some work to do before this interface is stable.
 * See <a href="https://github.com/amzn/ion-java/issues/11">issue amzn/ion-java/issues/11</a>
 * <p>
 * An {@code IonReader} has a "cursor" tracking the <em>current value</em> on
 * which the reader is positioned. Generally, newly created readers are not
 * positioned on any value. To begin traversing the Ion data, one would use
 * {@link #next()} to advance the cursor onto the first value (or learn there isn't
 * one). Once positioned, the current value's data can be accessed with the
 * {@code *Value()} methods.
 * <p>
 * When the current value is a container, calling {@link #next()} moves the
 * cursor to the <em>next sibling</em> of the container, at the same depth,
 * skipping over any children the container may have.
 * To read the children, call {@link #stepIn()},
 * then {@link #next()} to position onto the first child value (or learn there
 * isn't one).  Calling {@link #stepOut()} skips over any remaining children
 * and moves the cursor just beyond the container; call {@link #next()} to
 * move the cursor to the following value.
 * <p>
 * In general, method names are intended to parallel similar methods in the
 * {@link IonValue} hierarchy.  For example, to get the text of a symbol one
 * would use {@link #stringValue()}, mirroring {@link IonSymbol#stringValue()}.
 *
 * <h2>Exception Handling</h2>
 * {@code IonReader} is a generic interface for traversing Ion data, and it's
 * not possible to fully specify the set of exceptions that could be thrown
 * from the underlying data source.  Thus all failures are thrown as instances
 * of {@link IonException}, wrapping the original cause.  If an application
 * wants to handle (say) {@link IOException}s specially, then it needs to
 * extract that from the wrappers; the documentation of {@link IonException}
 * explains how to do that.
 *
 * <h2>Reader Facets</h2>
 * Readers are {@link Faceted} and implementations may provide additional
 * functionality accessible via the {@link #asFacet(Class)} method.
 *
 * <h3>The {@link SpanProvider} Facet</h3>
 * This facet is available on all readers that directly consume an Ion source.
 * It provides access to the "{@linkplain SpanProvider#currentSpan() current
 * span}" covering the reader's current value.
 * There is <em>not</em> a current span at the start of the source, immediately
 * after a call to {@link #stepIn()} or {@link #stepOut()}, or when the prior
 * call to {@link #next()} returned null (meaning: end of container or end of
 * stream). In such states, {@link SpanProvider#currentSpan()} will fail.
 *
 * <h3>The {@link SeekableReader} Facet</h3>
 * This facet is available on all readers <em>except</em> those created from
 * an {@link java.io.InputStream InputStream}.
 * (See <a href="https://github.com/amzn/ion-java/issues/17">issue amzn/ion-java/issues/17</a>.)
 * It allows the user to reposition the reader to a {@link Span} over the
 * same reader instance or another reader with the same source.
 *
 * <h2>Span Facets</h2>
 * Readers that support the {@link SpanProvider} facet vend {@link Span}s that
 * are also faceted.
 *
 * <h3>The {@link OffsetSpan} Facet</h3>
 * This facet is support by all readers of Ion binary and text data.
 *
 * <h3>The {@link TextSpan} Facet</h3>
 * This facet is supported by all readers of Ion text data.
 */
public interface IonReader
    extends Closeable, Faceted
{

    /**
     * Determines whether there is another value at the current depth;
     * in other words whether there is a sibling value that may be reached
     * using {@link #next()}.
     * This method may be
     * called multiple times, which does not move the current position.
     * <p>
     * <b>WARNING:</b> this method alters the internal state of the reader such
     * that you cannot reliably get values from the "current" element. The only
     * thing you should call after {@code hasNext()} is {@link #next()}!
     *
     * @deprecated Applications should detect the end of the current level by
     * checking for a {@code null} response from {@link #next()}.
     */
    @Deprecated
    public boolean hasNext();

    /**
     * Positions this reader on the next sibling after the current value,
     * returning the type of that value.  Once so positioned the contents of
     * this value can be accessed with the {@code *Value()} methods.
     * <p>
     * A sequence of {@code next()} calls traverses the data at a constant
     * depth, within the same container.
     * Use {@link #stepIn()} to traverse down into any containers, and
     * {@link #stepOut()} to traverse up to the parent container.
     *
     * @return the type of the next Ion value (never {@link IonType#DATAGRAM}),
     * or {@code null} when there are no more elements at the current depth in
     * the same container.
     */
    public IonType next();


    /**
     * Positions the reader just before the contents of the current value,
     * which must be a container (list, sexp, or struct).
     * There's no current value immediately after stepping in, so the next
     * thing you'll want to do is call {@link #next()} to move onto the first
     * child value (or learn that there's not one).
     * <p>
     * Stepping into a null container ({@code null.list}, {@code null.sexp},
     * or {@code null.struct}) behaves as if the container were empty
     * ({@code []}, {@code ()}, or <code>{}</code>).
     * <p>
     * At any time {@link #stepOut()} may be called to move the cursor back to
     * (just after) the parent value, even if there's more children remaining.
     *
     * @throws IllegalStateException if the current value isn't an Ion container.
     */
    public void stepIn();

    /**
     * Positions the iterator after the current parent's value, moving up one
     * level in the data hierarchy.
     * There's no current value immediately after stepping out, so the next
     * thing you'll want to do is call {@link #next()} to move onto the
     * following value.
     *
     * @throws IllegalStateException if the current value wasn't stepped into.
     */
    public void stepOut();

    /**
     * Returns the depth into the Ion value that this reader has traversed.
     * At top level the depth is 0, and it increases by one on each call to
     * {@link #stepIn()}.
     */
    public int getDepth();


    /**
     * Returns the symbol table that is applicable to the current value.
     * This may be either a system or local symbol table.
     */
    public SymbolTable getSymbolTable();


    /**
     * Returns the type of the current value, or null if there is no
     * current value.
     */
    public IonType getType();

    /**
     * Returns an {@link IntegerSize} representing the smallest-possible
     * Java type of the Ion {@code int} at the current value.
     *
     * If the current value is {@code null.int} or is not an Ion
     * {@code int}, or if there is no current value, {@code null} will
     * be returned.
     *
     * @see IonInt#getIntegerSize()
     */
    public IntegerSize getIntegerSize();

    /**
     * Return the annotations of the current value as an array of strings.
     *
     * @return the (ordered) annotations on the current value, or an empty
     * array (not {@code null}) if there are none.
     *
     * @throws UnknownSymbolException if any annotation has unknown text.
     */
    public String[] getTypeAnnotations();

    /**
     * Gets the current value's annotations as symbol tokens (text + ID).
     *
     * @return the (ordered) annotations on the current value, or an empty
     * array (not {@code null}) if there are none.
     *
     */
    public SymbolToken[] getTypeAnnotationSymbols();

    /**
     * Return the annotations on the curent value as an iterator.  The
     * iterator is empty (hasNext() returns false on the first call) if
     * there are no annotations on the current value.
     *
     * Implementations *may* throw {@link UnknownSymbolException} from
     * this method if any annotation contains unknown text. Alternatively,
     * implementations may provide an Iterator that throws
     * {@link UnknownSymbolException} only when the user navigates the
     * iterator to an annotation with unknown text.
     *
     * @return not null.
     */
    public Iterator<String> iterateTypeAnnotations();

    /**

    /**
     * Gets the symbol ID of the field name attached to the current value.
     * <p>
     * <b>This is an "expert method": correct use requires deep understanding
     * of the Ion binary format. You almost certainly don't want to use it.</b>
     *
     * @return the symbol ID of the field name, if the current value is a
     * field within a struct.
     * If the current value is not a field, or if the symbol ID cannot be
     * determined, this method returns a value <em>less than one</em>.
     *
     * @deprecated Use {@link #getFieldNameSymbol()} instead.
     */
    @Deprecated
    public int getFieldId();

    /**
     * Return the field name of the current value. Or null if there is no valid
     * current value or if the current value is not a field of a struct.
     *
     * @throws UnknownSymbolException if the field name has unknown text.
     */
    public String getFieldName();

    /**
     * Gets the current value's field name as a symbol token (text + ID).
     * If the text of the token isn't known, the result's
     * {@link SymbolToken#getText()} will be null.
     * If the symbol ID of the token isn't known, the result's
     * {@link SymbolToken#getSid()} will be
     * {@link SymbolTable#UNKNOWN_SYMBOL_ID}.
     * At least one of the two fields will be defined.
     *
     * @return null if there is no current value or if the current value is
     *  not a field of a struct.
     *

     */
    public SymbolToken getFieldNameSymbol();


    /**
     * Determines whether the current value is a null Ion value of any type
     * (for example, <code>null</code> or <code>null.int</code>).
     * It should be called before
     * calling getters that return value types (int, long, boolean,
     * double).
     */
    public boolean isNullValue();

    /**
     * Determines whether this reader is currently traversing the fields of an
     * Ion struct. It returns false if the iteration
     * is in a list, a sexp, or a datagram.
     */
    public boolean isInStruct();


    //=========================================================================
    // Value reading

    /**
     * Returns the current value as an boolean.
     * This is only valid when {@link #getType()} returns {@link IonType#BOOL}.
     */
    public boolean booleanValue();

    /**
     * Returns the current value as an int.  This is only valid if there is
     * an underlying value and the value is of a numeric type (int, float, or
     * decimal).
     */
    public int intValue();

    /**
     * Returns the current value as a long.  This is only valid if there is
     * an underlying value and the value is of a numeric type (int, float, or
     * decimal).
     */
    public long longValue();

    /**
     * Returns the current value as a {@link BigInteger}.  This is only valid if there
     * is an underlying value and the value is of a numeric type (int, float, or
     * decimal).
     */
    public BigInteger bigIntegerValue();

    /**
     * Returns the current value as a double.  This is only valid if there is
     * an underlying value and the value is either float, or decimal.
     */
    public double doubleValue();

    /**
     * Returns the current value as a {@link BigDecimal}.
     * This method should not return a {@link Decimal}, so it lacks support for
     * negative zeros.
     * <p>
     * This method is only valid when {@link #getType()} returns
     * {@link IonType#DECIMAL}.
     *
     * @return the current value as a {@link BigDecimal},
     * or {@code null} if the current value is {@code null.decimal}.
     */
    public BigDecimal bigDecimalValue();

    /**
     * Returns the current value as a {@link Decimal}, which extends
     * {@link BigDecimal} with support for negative zeros.
     * This is only valid when {@link #getType()} returns
     * {@link IonType#DECIMAL}.
     *
     * @return the current value as a {@link Decimal},
     * or {@code null} if the current value is {@code null.decimal}.
     */
    public Decimal decimalValue();


    /**
     * Returns the current value as a {@link java.util.Date}.
     * This is only valid when {@link #getType()} returns
     * {@link IonType#TIMESTAMP}.
     *
     * @return the current value as a {@link Date},
     * or {@code null} if the current value is {@code null.timestamp}.
     */
    public Date dateValue();

    /**
     * Returns the current value as a {@link Timestamp}.
     * This is only valid when {@link #getType()} returns
     * {@link IonType#TIMESTAMP}.
     *
     * @return the current value as a {@link Timestamp},
     * or {@code null} if the current value is {@code null.timestamp}.
     */
    public Timestamp timestampValue();

    /**
     * Returns the current value as a Java String.
     * This is only valid when {@link #getType()} returns
     * {@link IonType#STRING} or {@link IonType#SYMBOL}.
     *
     * @throws UnknownSymbolException if the current value is a symbol
     * with unknown text.
     *
     * @see #symbolValue()
     */
    public String stringValue();


    /**
     * Returns the current value as a symbol token (text + ID).
     * This is only valid when {@link #getType()} returns
     * {@link IonType#SYMBOL}.
     *
     * @return null if {@link #isNullValue()}
     *

     */
    public SymbolToken symbolValue();


    /**
     * Gets the size in bytes of the current lob value.
     * This is only valid when {@link #getType()} returns {@link IonType#BLOB}
     * or {@link IonType#CLOB}.
     *
     * @return the lob's size in bytes.
     */
    public int byteSize();

    /**
     * Returns the current value as a newly-allocated byte array.
     * This is only valid when {@link #getType()} returns {@link IonType#BLOB}
     * or {@link IonType#CLOB}.
     */
    public byte[] newBytes();

    /**
     * Copies the current value into the passed in a byte array.
     * This is only valid when {@link #getType()} returns {@link IonType#BLOB}
     * or {@link IonType#CLOB}.
     *
     * @param buffer destination to copy the value into, this must not be null.
     * @param offset the first position to copy into, this must be non null and
     *  less than the length of buffer.
     * @param len the number of bytes available in the buffer to copy into,
     *  this must be long enough to hold the whole value and not extend outside
     *  of buffer.
     */
    public int getBytes(byte[] buffer, int offset, int len);

    /**
     * Returns the current value as a String using the Ion toString() serialization
     * format.  This is only valid if there is an underlying value.  This is
     * logically equivalent to getIonValue().toString() but may be more efficient
     * and does not require an IonSystem context to operate.
     */
    // 2008-10-30 Disabled this because semantics are cloudy.
    // In particular, does this move the cursor beyond the current value?
    // Also, this could be problematic to use since other value-extraction
    // methods are read-once, so one can't look at the value before calling this.
//    public String valueToString();

}
