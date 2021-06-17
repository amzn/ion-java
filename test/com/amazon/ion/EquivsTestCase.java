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

import static com.amazon.ion.IonType.DATAGRAM;

import com.amazon.ion.impl._Private_Utils;
import com.amazon.ion.junit.IonAssert;

import java.io.Closeable;
import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.amazon.ion.IonType.DATAGRAM;

public abstract class EquivsTestCase
    extends IonTestCase
{
    /**
     * If an IonValue (inside a sequence) is annotated with this string, it
     * denotes that the IonValue is an IonString and its
     * {@link IonString#stringValue()} is to be interpretted as an IonDatagram.
     */
    private static final String EMBEDDED_DOCUMENTS_ANNOTATION =
        "embedded_documents";

    private static final String SCHEMA_CLASS_CAST_ERROR_MSG_HEADER =
        "Test files must follow the schema: top-level sequences with each " +
        "sequence consisting of at least two ";

    private static final String SCHEMA_CLASS_CAST_ERROR_MSG =
        SCHEMA_CLASS_CAST_ERROR_MSG_HEADER + "IonValues";

    private static final String SCHEMA_CLASS_CAST_ERROR_MSG_EMBEDDED_DOCUMENT =
        SCHEMA_CLASS_CAST_ERROR_MSG_HEADER + "IonStrings with embedded Ion documents";

    /**
     * The expected return values of equivalence checks.
     */
    private boolean myExpectedEquality;

    private File myTestFile;

    private List<Closeable> resourcesToClose = new ArrayList<Closeable>(1);

    public void setTestFile(File file)
    {
        myTestFile = file;
    }

    @Before
    public void resetResources() {
        resourcesToClose.clear();
    }

    @After
    public void closeResources() throws IOException {
        for (Closeable resource : resourcesToClose) {
            resource.close();
        }
    }

    public EquivsTestCase(boolean expectedEquality)
    {
        myExpectedEquality = expectedEquality;
    }

    /**
     * Perform equivalence checks on the test file encapsulated by the
     * {@code datagram}, ensuring that equality checks between test inputs in
     * the sequence(s) are as expected.
     * <p>
     * The property of transitivity of equalities is not assumed here. In the
     * case where there are more than two test inputs in a sequence, each test
     * input will be exhaustively compared against every other test input in
     * the same sequence, symmetrically.
     *
     * @param datagram
     *                  an IonDatagram encapsulating the sequences of test input
     * @param expectedEquality
     *                  expected output of the equality check
     */
    protected void runEquivalenceChecks(final IonDatagram datagram,
                                        boolean expectedEquality)
    {
        int datagramSize = datagram.size();

        assertTrue("Test file must have at least one sequence",
                   datagramSize > 0);

        for (int dgIndex = 0; dgIndex < datagramSize; dgIndex++)
        {
            IonSequence sequence = (IonSequence) datagram.get(dgIndex);

            boolean embeddedDocuments =
                sequence.hasTypeAnnotation(EMBEDDED_DOCUMENTS_ANNOTATION);

            int sequenceSize = sequence.size();
            assertTrue("Each sequence must have at least two values",
                       sequenceSize > 1);

            try
            {
                for (int i = 0; i < sequenceSize; i++)
                {
                    IonValue thisValue = sequence.get(i);
                    if (embeddedDocuments)
                    {
                        assertFalse("Embedded input cannot be null",
                                    thisValue.isNullValue());
                        IonString thisString = (IonString) thisValue;
                        thisValue = loader().load(thisString.stringValue());
                    }

                    for (int j = i + 1; j < sequenceSize; j++)
                    {
                        IonValue otherValue = sequence.get(j);
                        if (embeddedDocuments)
                        {
                            assertFalse("Embedded input cannot be null",
                                        otherValue.isNullValue());
                            IonString otherString = (IonString) otherValue;
                            otherValue = loader().load(otherString.stringValue());
                        }

                        try
                        {
                            checkEquivalence(thisValue, otherValue, expectedEquality);
                        }
                        catch (AssertionError e)
                        {
                            String message =
                                "Error comparing children " + i + " and " + j +
                                " of equivalence sequence " + dgIndex +
                                " (zero-based), expected " +
                                (expectedEquality ? "" : "in" ) + "equality" +
                                "\nthisValue: " + thisValue +
                                "\notherValue: " + otherValue + '\n' +
                                e.getMessage();
                            fail(message);
                        }
                    }
                }
            }
            catch (ClassCastException e)
            {
                // ClassCastExceptions are throw (and caught here) when the
                // test file doesn't follow the agreed upon schema.
                if (embeddedDocuments)
                {
                    fail(SCHEMA_CLASS_CAST_ERROR_MSG_EMBEDDED_DOCUMENT);
                }
                fail(SCHEMA_CLASS_CAST_ERROR_MSG);
            }
        }
    }

    /**
     * Check strict equivalence or non-equivalence between two IonValues.
     * <p>
     * This also checks for the symmetric property of equalities,
     * i.e. X is equivalent to Y if and only if Y is equivalent to X.
     *
     * @param expectedEquality the result that {@link IonValue#equals(Object)}
     *          should return
     */
    protected void checkEquivalence(final IonValue left,
                                    final IonValue right,
                                    boolean expectedEquality)
    {
        if (expectedEquality)
        {
            IonAssert.assertIonEquals(left, right);
            IonAssert.assertIonEquals(right, left);

            // IonDatagram's hashCode() is unsupported
            if (left.getType()  != DATAGRAM &&
                right.getType() != DATAGRAM)
                assertEquals("Equal values have unequal hashes",
                             left.hashCode(), right.hashCode());
        }
        else
        {
            // Ensure the two values compared both ways return the same consistent
            // result, i.e. false. This is not related to the symmetric property
            // of equalities.
            assertFalse(left.equals(right));
            assertFalse(right.equals(left));
        }
    }

    public IonDatagram[] roundTripDatagram(IonDatagram input) throws IOException {
        IonSystem system = IonSystemBuilder.standard().build();
        IonLoader loader = system.getLoader();
        ByteArrayOutputStream textOutputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream binaryOutputStream = new ByteArrayOutputStream();
        IonReader textReader = null, binaryReader = null;
        IonWriter textWriter = null, binaryWriter = null;
        IonDatagram[] data = new IonDatagram[3];

        try {
            textReader = system.newReader(input);
            textWriter = IonTextWriterBuilder.standard().build(textOutputStream);
            textWriter.writeValues(textReader);
            binaryWriter = IonBinaryWriterBuilder.standard().build(binaryOutputStream);
            binaryReader = system.newReader(input);
            binaryWriter.writeValues(binaryReader);
        } finally {
            textWriter.close();
            binaryWriter.close();
            textReader.close();
            binaryReader.close();
        }
        data[0] = input;
        data[1] = loader.load(new ByteArrayInputStream(textOutputStream.toByteArray()));
        binaryReader = getStreamingMode().newIonReader(
            system().getCatalog(),
            new ByteArrayInputStream(binaryOutputStream.toByteArray())
        );
        resourcesToClose.add(binaryReader);
        data[2] = loader.load(binaryReader);
        return data;
    }

    protected void roundTripEquivalence(IonDatagram input, boolean myExpectedEquality) throws IOException {
        IonDatagram[] data = roundTripDatagram(input);
        for(int i = 0; i < data.length; i++){
            runEquivalenceChecks(data[i], myExpectedEquality);
            checkEquivalence(data[i], data[((i + 1) % data.length)], true);
        }
    }

    @Test
    public void testEquivsOverFile()
    throws Exception
    {
        IonReader reader = getStreamingMode().newIonReader(system().getCatalog(), new FileInputStream(myTestFile));
        IonDatagram dg = loader().load(reader);
        reader.close();
        runEquivalenceChecks(dg, myExpectedEquality);
        roundTripEquivalence(dg, myExpectedEquality);
    }

    @Test
    public void testEquivsOverString()
    throws Exception
    {
        if (myTestFile.getName().endsWith(".ion"))
        {
            String ionText = _Private_Utils.utf8FileToString(myTestFile);
            IonDatagram dg = loader().load(ionText);
            runEquivalenceChecks(dg, myExpectedEquality);
        }
    }

}
