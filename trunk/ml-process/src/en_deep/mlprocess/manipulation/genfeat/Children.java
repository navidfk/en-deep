/*
 *  Copyright (c) 2010 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess.manipulation.genfeat;

import en_deep.mlprocess.manipulation.DataReader;
import en_deep.mlprocess.manipulation.DataReader.FeatType;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This feature lists all the PsOS, lemmas and tags of the children of the given
 * word.
 * @author Ondrej Dusek
 */
public class Children extends ParametrizedFeature {

    public Children(DataReader reader){
        super(reader, FeatType.SYNT);
    }

    @Override
    public String getHeader() {
        return this.getParametrizedHeader("Children",  DataReader.STRING);
    }

    @Override
    public String generate(int wordNo, int predNo) {


        int [] children = this.reader.getChildren(wordNo);
        String [] values = new String [this.attrPos.length];

        for (int i = 0; i < children.length; ++i){
            
            String [] fields = this.getFields(children[i]);

            for (int j = 0; j < fields.length; ++j){
                values[j] = (values[j] == null ? "" : values[j] + SEP) + fields[j];
            }
        }

        for (int i = 0; i < values.length; ++i){
            if (values[i] == null){
                values[i] = "";
            }
        }

        return StringUtils.join(values, ",", true);
    }


}
