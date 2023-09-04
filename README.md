~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# yaGen

yet another Generator writing enhanced schema DDL script

About
-
This is no stand-alone DDL generator but uses Hibernate hbm2ddl generation tool
and just extends functionality and enhances DDL output.
Since Hibernate did not support interceptors or plugins or whatsoever for being able to
alter the generated DDL it is required to patch the class files before they are
loaded by the class loader. Bytecode modifications are done using javassist library.

Since yaGen is facilitating Hibernate's hbm2ddl functionality we get all the enhancements 
in dumped DDL as well as with initialized in-memory-DBs. 

So basically yaGen only kicks in when 
* some Hibernate classes have been patched before loading via class loader and 
* Hibernate is executing hbm2ddl (class `org.hibernate.tool.hbm2ddl.SchemaExport`)

Agent
-
The easiest way of accomplishing class patching e.g. in a working setup is to use
the yaGen java agent when starting the JVM (like `-javaagent:${project.build.directory}/agents/yagen-agent.jar`, 
see `lib/yagen-example-domain/pom.xml`).

Static Init
-
This is only possible if Hibernate classes not have been loaded yet until initializing class
is referenced. See `com.github.gekoh.yagen.example.test.TestBase` which is used for JUnit tests.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    static {
        try {
            YagenInit.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

DDL Dump
-
If only DDL output is required this can be done via executing java class
`com.github.gekoh.yagen.ddl.CoreDDLGenerator`
e.g. with maven plugin (see profile `ddl-gen` within `lib/yagen-example-domain/pom.xml`).
Note that you need a working JPA configuration using hibernate in classpath.

DDL Dump with comments
-
YaGen also can generate table and column comments (for Oracle RDBMS) which are extracted
from javadoc source on class and field level. In this case we need to call the javadoc
Doclet functionality which might not be available with specific JVM versions.
See usage of `com.github.gekoh.yagen.ddl.comment.CommentsDDLGenerator` with profile
`ddl-gen-with-tabNcol-comments` in `lib/yagen-example-domain/pom.xml`.