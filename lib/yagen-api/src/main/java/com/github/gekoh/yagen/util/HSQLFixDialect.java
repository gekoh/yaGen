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
package com.github.gekoh.yagen.util;

import org.hibernate.HibernateException;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.StandardBasicTypes;

import java.sql.Types;

/**
 * @author Georg Kohlweiss
 */
public class HSQLFixDialect extends org.hibernate.dialect.HSQLDialect {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HSQLDialect.class);


    public HSQLFixDialect() {
        registerColumnType( Types.BLOB, "blob" );
        registerColumnType( Types.CLOB, "clob" );

        this.registerFunction("to_char", new StandardSQLFunction("to_char", StandardBasicTypes.STRING));
    }

    public boolean supportsUniqueConstraintInCreateAlterTable() {
        return false;
    }

    @Override
    public String getDropTableString( String tableName ) {
        // Append CASCADE to formatted DROP TABLE string
        final String superDrop = super.getDropTableString( tableName );
        return superDrop + " cascade";
    }

    @Override
    public boolean dropConstraints() {
        // Do not explicitly drop constraints, use DROP TABLE ... CASCADE
        return false;
    }

    public static class Timestamp9 extends HSQLFixDialect {

        @Override
        public String getTypeName(int code, long length, int precision, int scale) throws HibernateException {
            if (code == Types.TIMESTAMP && length == 0) {
                return super.getTypeName(code) + "(9)";
            }
            return super.getTypeName(code, length, precision, scale);
        }
    }
}