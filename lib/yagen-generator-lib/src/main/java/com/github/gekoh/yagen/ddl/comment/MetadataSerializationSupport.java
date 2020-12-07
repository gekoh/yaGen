/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.github.gekoh.yagen.ddl.comment;

import com.thoughtworks.xstream.XStream;

/**
 * @author Georg Kohlweiss
 */
public class MetadataSerializationSupport {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MetadataSerializationSupport.class);

    private static final String REFERENCE_ATTRIBUTE_NAME = "reference";
    private static final String IDREF_ATTRIBUTE_NAME = "idref";

    private static final XStream XSTREAM;
    static {
        XSTREAM = new XStream();
        XSTREAM.aliasPackage("comment", TableMetadata.class.getPackage().getName());
        XSTREAM.setMode(XStream.XPATH_ABSOLUTE_REFERENCES);
        XSTREAM.aliasSystemAttribute(IDREF_ATTRIBUTE_NAME, REFERENCE_ATTRIBUTE_NAME); //replace all reference attributes with idref
        XSTREAM.autodetectAnnotations(false); // donot change to true -> xstream will not be thread safe!
    }

    public static String toXML (Metadata metadata) {
        return XSTREAM.toXML(metadata);
    }
}
