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

import static com.amazon.ion.TestUtils.BAD_IONTESTS_FILES;
import static com.amazon.ion.TestUtils.GLOBAL_SKIP_LIST;
import static com.amazon.ion.TestUtils.testdataFiles;

import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.TestUtils;
import com.amazon.ion.impl._Private_Utils;
import com.amazon.ion.junit.Injected.Inject;
import java.io.File;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;

public class BadIonStreamingTest
extends IonTestCase
{
    private static final boolean _debug_output_errors = false;


    @Inject("testFile")
    public static final File[] FILES = testdataFiles(GLOBAL_SKIP_LIST,
                                                     BAD_IONTESTS_FILES);

    private File myTestFile;

    public void setTestFile(File file)
    {
        myTestFile = file;
    }

    @Test(expected = IonException.class)
    public void testReadingScalars()
    throws Exception
    {
        try
        {
            byte[] buf = _Private_Utils.loadFileBytes(myTestFile);
            IonReader it = getStreamingMode().newIonReader(system().getCatalog(), buf);
            TestUtils.deepRead(it, true);
            it.close();
        }
        catch (IonException e)
        {
            /* good - we're expecting an error, there are testing bad input */
            if (_debug_output_errors) {
                System.out.print(this.myTestFile.getName());
                System.out.print(": ");
                System.out.println(e.getMessage());
            }
            throw e;
        }
    }
}
