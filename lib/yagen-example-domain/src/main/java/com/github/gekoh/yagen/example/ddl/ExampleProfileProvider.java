package com.github.gekoh.yagen.example.ddl;

import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.ddl.Duplexer;
import com.github.gekoh.yagen.ddl.ObjectType;
import com.github.gekoh.yagen.ddl.ProfileProvider;
import org.hibernate.dialect.Dialect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.gekoh.yagen.hibernate.PatchGlue.STATEMENT_SEPARATOR;

public class ExampleProfileProvider implements ProfileProvider {

    @Override
    public DDLGenerator.Profile getProfile(String profileName) {
        Profile profile = new Profile(profileName);
        final List<String> historyTables = new ArrayList<>();

        if ("addImportTimestampProfile".equals(profileName)) {
            profile.addDuplexer(new Duplexer() {
                @Override
                public void handleDdl(ObjectType objectType, String objectName, String ddl) {
                    if (objectType == ObjectType.TABLE && objectName.toLowerCase().endsWith("_hst")) {
                        historyTables.add(objectName);
                    }
                }
            });
        }

        profile.addDdl(new DDLGenerator.AddTemplateDDLEntry((String) null) {
            @Override
            public String getDdlText(Dialect dialect) {
                if (!historyTables.isEmpty()) {
                    this.ddlText = getAdditionalDdl(historyTables);
                    return super.getDdlText(dialect);
                }
                return "";
            }
        });

        return profile;
    }

    private String getAdditionalDdl(List<String> historyTables) {
        StringBuilder sb = new StringBuilder();

        for (String historyTable : historyTables) {
            sb.append(STATEMENT_SEPARATOR);
            sb.append("alter table ").append(historyTable).append(" add import_timestamp timestamp default current_timestamp");
        }

        return sb.toString();
    }

    public static class AddImportTimestampProvider extends ExampleProfileProvider {
        @Override
        public DDLGenerator.Profile getProfile(String profileName) {
            return super.getProfile("addImportTimestampProfile");
        }
    }

    public static class Profile extends DDLGenerator.Profile {

        private final Map<ObjectType, Map<String, String>> ddlMap = new HashMap<ObjectType, Map<String, String>>();

        public Profile(String name) {
            super(name);
            addDuplexer(new Duplexer() {
                public void handleDdl(ObjectType objectType, String objectName, String ddl) {
                    Map<String, String> ddlSubMap = ddlMap.computeIfAbsent(objectType, k -> new HashMap<>());
                    ddlSubMap.put(objectName, ddl);
                }
            });
        }

        public Map<ObjectType, Map<String, String>> getRecordedDdl() {
            return ddlMap;
        }
    }
}
