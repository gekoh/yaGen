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
package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import org.hibernate.dialect.Dialect;

/**
 * @author Georg Kohlweiss 
 */
public class Oracle10gDialect extends org.hibernate.dialect.Oracle10gDialect implements DDLEnhancer {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Oracle10gDialect.class);

    private CreateDDL ddlEnhancer;

    public void initDDLEnhancer(DDLGenerator.Profile profile, Dialect dialect) {
        ddlEnhancer = new CreateDDL(profile, dialect);
    }

    public CreateDDL getDDLEnhancer() {
        return ddlEnhancer;
    }
}