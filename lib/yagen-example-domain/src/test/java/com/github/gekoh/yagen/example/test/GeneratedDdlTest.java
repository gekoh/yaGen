package com.github.gekoh.yagen.example.test;

import com.github.gekoh.yagen.ddl.ObjectType;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hanspeter Duennenberger
 */
public class GeneratedDdlTest extends TestBase {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GeneratedDdlTest.class);

    private ByteArrayOutputStream out;
    private PrintStream originalOut;

    @Override
    protected String getPersistenceUnitName() {
        interceptSystemOut();
        return "example-domain-test-sysout";
    }

    @Override
    protected String getDbUserName() {
        return "SA";
    }

    private void interceptSystemOut() {
        originalOut = System.out;
        out = new ByteArrayOutputStream(16*1024);
        System.setOut(new PrintStream(out));
    }

    @Before
    public void resetSystemOut() {
        if (originalOut != null) {
            System.setOut(originalOut);
            originalOut = null;
            System.out.println(out.toString());
        }
    }

    @Test
    public void testGeneratedDdl() throws Exception {
        // assert operating_resources_htU contains "is null" conditions to update invalidated_at
        assertTriggerContains(ddlMap, "operating_resources_htU", ".*set invalidated_at([^;]+);.*",
         "board_book_uuid=old.board_book_uuid and",
         "((added_operating_resources is null and old.added_operating_resources is null) or added_operating_resources=old.added_operating_resources) and");

        // assert board_book_entry_htU does not contain "is null" conditions to update invalidated_at
        assertTriggerContains(ddlMap, "board_book_entry_htU", ".*(set invalidated_at[^;]+;).*",
         "operation <> 'd' and",
          "uuid=old.uuid and");

        assertGeneratedDdlContainsAll(out,
         "function get_audit_user(",
         "table HST_CURRENT_TRANSACTION (",
         "table HST_MODIFIED_ROW (",
         "procedure set_transaction_timestamp("
        );

        //testing if dynamically created DDL by com.github.gekoh.yagen.example.ddl.ExampleProfileProvider is considered
        em.createNativeQuery("select import_timestamp from AIRCRAFT_HST").getResultList();
    }

    private void assertGeneratedDdlContainsAll(ByteArrayOutputStream interceptedOut, String ... containedTexts) {
        String generatedDdlLc = interceptedOut.toString().toLowerCase();
        for (String containedText : containedTexts) {
            Assert.assertTrue("Generated DDL should contain: " + containedText, generatedDdlLc.contains(containedText.toLowerCase()));
        }
    }

    private void assertTriggerContains(Map<ObjectType, Map<String, String>> ddlMap, String name, String patternMatchOneGroup, String... contents) {
        String ddlLc = ddlMap.get(ObjectType.TRIGGER).get(name).toLowerCase();
        Matcher m = Pattern.compile(patternMatchOneGroup, Pattern.DOTALL).matcher(ddlLc);
        Assert.assertTrue(m.matches());
        String group1 = m.group(1);
        for (String content : contents) {
            Assert.assertTrue(group1 + "\n==> should contain: " + content, group1.contains(content));
        }
    }

}