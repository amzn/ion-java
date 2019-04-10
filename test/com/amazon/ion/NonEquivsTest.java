/*
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.amazon.ion;

import static com.amazon.ion.TestUtils.GLOBAL_SKIP_LIST;
import static com.amazon.ion.TestUtils.NON_EQUIVS_IONTESTS_FILEs;

import com.amazon.ion.junit.Injected.Inject;
import java.io.File;

public class NonEquivsTest
    extends EquivsTestCase
{
    @Inject("testFile")
    public static final File[] FILES =
        TestUtils.testdataFiles(GLOBAL_SKIP_LIST, NON_EQUIVS_IONTESTS_FILEs);

    public NonEquivsTest()
    {
        super(false);
    }
}
