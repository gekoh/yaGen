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

Since yaGen is facilitating Hibernate's hbm2ddl functionality we get all the enhancements 
in dumped DDL as well as with initialized in-memory-DBs. 

So basically yaGen only kicks in when
* Hibernate is executing hbm2ddl (class `org.hibernate.tool.hbm2ddl.SchemaExport`)

## Initialization

### Hibernate Schema Management Tool
Yagen works by extending the standard `HibernateSchemaManagementTool` provided by Hibernate.
You need to put 
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    <property name="hibernate.schema_management_tool" value="com.github.gekoh.yagen.hibernate.schema.SchemaManagementToolWrapper"/>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
as one of the `<persistence-unit>/<properties>` in your `persistence.xml`

## Configuration
### Persistence Unit properties

* `yagen.generator.profile.providerClass` name of subclass from `com.github.gekoh.yagen.ddl.ProfileProvider` which will
  be called for creating a generation profile as config container for DDL creation where e.g. additional DDLs can be 
  added. This is optional if no further DDL snippets are required or any other tweak is needed. 
* `yagen.generator.bypass.implement` when set to `true` yaGen will generate trigger bypass functionality into the
  trigger sources to be able to disable particular (or all) triggers at runtime (see static
  helper method `com.github.gekoh.yagen.util.DBHelper.setBypass`)
* `hibernate.schema_management_tool` must be set to `com.github.gekoh.yagen.hibernate.schema.SchemaManagementToolWrapper` 
  in order for Yagen to do its magic.


### System properties

* `yagen.bypass` only for HSQLDB: if set (any value) all triggers (audit, history, i18n-view, cascade-null) will be
  bypassed, this is mainly for junit tests. Note that bypass functionality needs to be enabled, see
  PU property `yagen.generator.bypass.implement`

### Static configuration
* `com.github.gekoh.yagen.util.DBHelper.setBypass` specify regular expression matching trigger names (audit, history,
  i18n-view, cascade-null) which will be bypassed. Note that bypass functionality needs to be enabled, see
  Persistence Unit property `yagen.generator.bypass.implement`

## Create history entity classes
yaGen is able to generate entity classes mapped to history tables which in turn are automatically created along with the
DDL if any entity class has been annotated with `@com.github.gekoh.yagen.api.TemporalEntity`.
In that case we can use the maven plugin exec:java to create these entities transparently during maven build.  
See generator class `com.github.gekoh.yagen.hst.CreateEntities` used in maven module `lib/yagen-example-domain/pom.xml`.  
Also orm.xml mapping file will be created to be able to use these history entity classes within JPA queries 
(e.g. `com.github.gekoh.yagen.example.test.HistoryTest.testHistory`).

## DDL output

### Plain dump
If only DDL output is required this can be done via executing java class
`com.github.gekoh.yagen.ddl.CoreDDLGenerator`
e.g. with maven plugin (see profile `ddl-gen` within `lib/yagen-example-domain/pom.xml`).
Note that you need a working JPA configuration using hibernate in classpath.
