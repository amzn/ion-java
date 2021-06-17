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

import com.amazon.ion.SymbolToken;
import com.amazon.ion.UnknownSymbolException;


final class SymbolTokenImpl
    implements _Private_SymbolToken
{
    private final String myText;
    private final int mySid;

    SymbolTokenImpl(String text, int sid)
    {
        assert text != null || sid >= 0 : "Neither text nor sid is defined";

        myText = text;
        mySid = sid;
    }

    SymbolTokenImpl(int sid)
    {
        assert sid >= 0 : "sid is undefined";

        myText = null;
        mySid = sid;
    }


    public String getText()
    {
        return myText;
    }

    public String assumeText()
    {
        if (myText == null) throw new UnknownSymbolException(mySid);
        return myText;
    }

    public int getSid()
    {
        return mySid;
    }

    @Override
    public String toString()
    {
        return "SymbolToken::{text:" + myText + ",id:" + mySid + "}";
    }

    /*
     * TODO amzn/ion-java#126
     *Equals and hashCode must be symmetric.
     *Two symboltokens are only equal when text1 equals text2 (including null == null)
     *This is an incomplete solution, needs to be updated as symboltokens are fleshed out.
     */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof SymbolToken)) return false;

        SymbolToken other = (SymbolToken) o;
        if(getText() == null || other.getText() == null){
            return getText() == other.getText();
        }
        return getText().equals(other.getText());
    }

    @Override
    public int hashCode() {
        if(getText() != null) return getText().hashCode();
        return 0;
    }
}
