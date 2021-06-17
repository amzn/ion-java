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

import static com.amazon.ion.TestUtils.GLOBAL_SKIP_LIST;
import static com.amazon.ion.TestUtils.GOOD_IONTESTS_FILES;
import static com.amazon.ion.TestUtils.consumeCurrentValue;
import static com.amazon.ion.TestUtils.testdataFiles;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.junit.Injected.Inject;
import com.amazon.ion.junit.IonAssert;
import java.io.File;
import java.io.FileInputStream;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReaderSkippingTest
    extends IonTestCase
{
    @Inject("testFile")
    public static final File[] FILES =
        testdataFiles(GLOBAL_SKIP_LIST,
                      GOOD_IONTESTS_FILES);

    private File myTestFile;
    private IonReader myFullReader;
    private IonReader mySkipReader;

    private static long SEED = 0;
    private static int SKIP_PERCENT = 50;
    private static int ITERATIONS_PER_FILE = 10;
    private Random myRandom;


    public void setTestFile(File file)
    {
        myTestFile = file;
    }

    @BeforeClass
    public static void beforeClass()
    {
        SEED = System.currentTimeMillis();
        System.out.println(ReaderSkippingTest.class.getSimpleName() +
                           ".SEED=" + SEED + "L");
    }

    @Override
    @Before
    public void setUp()
    throws Exception
    {
        super.setUp();
        myFullReader = getStreamingMode().newIonReader(system().getCatalog(), new FileInputStream(myTestFile));
        mySkipReader = getStreamingMode().newIonReader(system().getCatalog(), new FileInputStream(myTestFile));

        myRandom = new Random(SEED);
    }

    @Override
    @After
    public void tearDown()
    throws Exception
    {
        myFullReader.close();
        mySkipReader.close();
        myFullReader = null;
        mySkipReader = null;
        myRandom = null;
        super.tearDown();
    }


    private boolean skip()
    {
        int val = myRandom.nextInt(100);
        return (val < SKIP_PERCENT);
    }


    @Test
    public void testFile()
    throws Exception
    {
        for (int i = 0; i < ITERATIONS_PER_FILE; i++)
        {
            skipThroughFile();
        }
    }


    public void skipThroughFile()
    throws Exception
    {
        FileInputStream fullStream = new FileInputStream(myTestFile);
        myFullReader = system().newReader(fullStream);
        try
        {
            FileInputStream skipStream = new FileInputStream(myTestFile);
            mySkipReader = system().newReader(skipStream);
            try
            {
                skipThroughContainer();
            }
            finally
            {
                mySkipReader.close();
                skipStream.close();
            }
        }
        finally
        {
            myFullReader.close();
            fullStream.close();
        }
    }


    public void skipThroughContainer()
    {
        while (true)
        {
            IonType fullType = myFullReader.next();
            IonType skipType = mySkipReader.next();

            assertEquals("next type", fullType, skipType);
            if (fullType == null) return;

            skipThroughValue(fullType);
        }
    }

    public void skipThroughValue(IonType fullType)
    {
        // Read all data from myFullReader,
        // but only some data from mySkipReader

        myFullReader.getTypeAnnotationSymbols();
        if (!skip())
        {
            SymbolToken[] expecteds = myFullReader.getTypeAnnotationSymbols();
            check(mySkipReader).annotations(expecteds);
        }

        // We don't test SIDs since that assumes that SIDs always get assigned
        // in the same order. We don't guarantee that for all readers.

        myFullReader.getFieldNameSymbol();
        if (!skip())
        {
            check(mySkipReader).fieldName(myFullReader.getFieldNameSymbol());
        }


        if (skip())
        {
            consumeCurrentValue(myFullReader);
        }
        else
        {
            switch (fullType)
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
                {
                    IonValue fullValue = system().newValue(myFullReader);
                    IonValue skipValue = system().newValue(mySkipReader);
                    IonAssert.assertIonEquals(fullValue, skipValue);
                    break;
                }
                case STRUCT:
                case LIST:
                case SEXP:
                {
                    myFullReader.stepIn();
                    mySkipReader.stepIn();
                    skipThroughContainer();
                    myFullReader.stepOut();
                    mySkipReader.stepOut();
                    break;
                }
                default:
                    throw new IllegalStateException("unexpected type: " + fullType);
            }
        }
    }
}
