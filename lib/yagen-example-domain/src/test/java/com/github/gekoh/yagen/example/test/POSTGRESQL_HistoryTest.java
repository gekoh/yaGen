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
package com.github.gekoh.yagen.example.test;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Mario Ivankovits
 */
public class POSTGRESQL_HistoryTest extends HistoryTest {

    private EmbeddedPostgres pg;

    @Override
    protected String getPersistenceUnitName() {
        return "example-domain-test-postgres";
    }

    protected void setupDatabase() {
        try {
            pg = EmbeddedPostgres.builder()
                    .setCleanDataDirectory(true)
                    .setPort(9002)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void shutdownDatabase() {
        try {
            if (pg != null) {
                pg.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void enableRecreateEmf() {
        emf = null;
    }

    @Override
    protected String getDbUserName() {
        return "postgres";
    }

    @Ignore("test does not work with oracle or postgresql")
    @Test
    public void testHistoryCollectionTableLimitation() {
    }
}
