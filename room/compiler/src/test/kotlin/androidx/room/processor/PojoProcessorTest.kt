/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.processor

import COMMON
import androidx.room.Embedded
import androidx.room.parser.SQLTypeAffinity
import androidx.room.processor.ProcessorErrors.CANNOT_FIND_GETTER_FOR_FIELD
import androidx.room.processor.ProcessorErrors.CANNOT_FIND_TYPE
import androidx.room.processor.ProcessorErrors.POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME
import androidx.room.processor.ProcessorErrors.junctionColumnWithoutIndex
import androidx.room.processor.ProcessorErrors.relationCannotFindEntityField
import androidx.room.processor.ProcessorErrors.relationCannotFindJunctionEntityField
import androidx.room.processor.ProcessorErrors.relationCannotFindJunctionParentField
import androidx.room.processor.ProcessorErrors.relationCannotFindParentEntityField
import androidx.room.testing.TestInvocation
import androidx.room.vo.CallType
import androidx.room.vo.Constructor
import androidx.room.vo.EmbeddedField
import androidx.room.vo.Field
import androidx.room.vo.Pojo
import androidx.room.vo.RelationCollector
import com.google.testing.compile.CompileTester
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import simpleRun
import toJFO
import javax.lang.model.element.Element
import javax.tools.JavaFileObject

/**
 * Some of the functionality is tested via TableEntityProcessor.
 */
@RunWith(JUnit4::class)
class PojoProcessorTest {

    companion object {
        val MY_POJO: ClassName = ClassName.get("foo.bar", "MyPojo")
        val HEADER = """
            package foo.bar;
            import androidx.room.*;
            import java.util.*;
            public class MyPojo {
            """
        val FOOTER = "\n}"
    }

    @Test
    fun inheritedPrivate() {
        val parent = """
            package foo.bar.x;
            import androidx.room.*;
            public class BaseClass {
                private String baseField;
                public String getBaseField(){ return baseField; }
                public void setBaseField(String baseField){ }
            }
        """
        simpleRun(
                """
                package foo.bar;
                import androidx.room.*;
                public class ${MY_POJO.simpleName()} extends foo.bar.x.BaseClass {
                    public String myField;
                }
                """.toJFO(MY_POJO.toString()),
                parent.toJFO("foo.bar.x.BaseClass")) { invocation ->
            val pojo = PojoProcessor.createFor(context = invocation.context,
                    element = invocation.typeElement(MY_POJO.toString()),
                    bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                    parent = null).process()
            assertThat(pojo.fields.find { it.name == "myField" }, notNullValue())
            assertThat(pojo.fields.find { it.name == "baseField" }, notNullValue())
        }.compilesWithoutError()
    }

    @Test
    fun transient_ignore() {
        singleRun("""
            transient int foo;
            int bar;
        """) { pojo ->
            assertThat(pojo.fields.size, `is`(1))
            assertThat(pojo.fields[0].name, `is`("bar"))
        }.compilesWithoutError()
    }

    @Test
    fun transient_withColumnInfo() {
        singleRun("""
            @ColumnInfo
            transient int foo;
            int bar;
        """) { pojo ->
            assertThat(pojo.fields.map { it.name }.toSet(), `is`(setOf("bar", "foo")))
        }.compilesWithoutError()
    }

    @Test
    fun transient_embedded() {
        singleRun("""
            @Embedded
            transient Foo foo;
            int bar;
            static class Foo {
                int x;
            }
        """) { pojo ->
            assertThat(pojo.fields.map { it.name }.toSet(), `is`(setOf("x", "bar")))
        }.compilesWithoutError()
    }

    @Test
    fun transient_insideEmbedded() {
        singleRun("""
            @Embedded
            Foo foo;
            int bar;
            static class Foo {
                transient int x;
                int y;
            }
        """) { pojo ->
            assertThat(pojo.fields.map { it.name }.toSet(), `is`(setOf("bar", "y")))
        }.compilesWithoutError()
    }

    @Test
    fun transient_relation() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public transient List<User> user;
                """, COMMON.USER
        ) { pojo ->
            assertThat(pojo.relations.size, `is`(1))
            assertThat(pojo.relations.first().entityField.name, `is`("uid"))
            assertThat(pojo.relations.first().parentField.name, `is`("id"))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun embedded() {
        singleRun(
                """
                int id;
                @Embedded
                Point myPoint;
                static class Point {
                    int x;
                    int y;
                }
                """
        ) { pojo ->
            assertThat(pojo.fields.size, `is`(3))
            assertThat(pojo.fields[1].name, `is`("x"))
            assertThat(pojo.fields[2].name, `is`("y"))
            assertThat(pojo.fields[0].parent, nullValue())
            assertThat(pojo.fields[1].parent, notNullValue())
            assertThat(pojo.fields[2].parent, notNullValue())
            val parent = pojo.fields[2].parent!!
            assertThat(parent.prefix, `is`(""))
            assertThat(parent.field.name, `is`("myPoint"))
            assertThat(parent.pojo.typeName,
                    `is`(ClassName.get("foo.bar.MyPojo", "Point") as TypeName))
        }.compilesWithoutError()
    }

    @Test
    fun embeddedWithPrefix() {
        singleRun(
                """
                int id;
                @Embedded(prefix = "foo")
                Point myPoint;
                static class Point {
                    int x;
                    @ColumnInfo(name = "y2")
                    int y;
                }
                """
        ) { pojo ->
            assertThat(pojo.fields.size, `is`(3))
            assertThat(pojo.fields[1].name, `is`("x"))
            assertThat(pojo.fields[2].name, `is`("y"))
            assertThat(pojo.fields[1].columnName, `is`("foox"))
            assertThat(pojo.fields[2].columnName, `is`("fooy2"))
            val parent = pojo.fields[2].parent!!
            assertThat(parent.prefix, `is`("foo"))
        }.compilesWithoutError()
    }

    @Test
    fun nestedEmbedded() {
        singleRun(
                """
                int id;
                @Embedded(prefix = "foo")
                Point myPoint;
                static class Point {
                    int x;
                    @ColumnInfo(name = "y2")
                    int y;
                    @Embedded(prefix = "bar")
                    Coordinate coordinate;
                }
                static class Coordinate {
                    double lat;
                    double lng;
                    @Ignore
                    String ignored;
                }
                """
        ) { pojo ->
            assertThat(pojo.fields.size, `is`(5))
            assertThat(pojo.fields.map { it.columnName }, `is`(
                    listOf("id", "foox", "fooy2", "foobarlat", "foobarlng")))
        }.compilesWithoutError()
    }

    @Test
    fun embedded_generic() {
        val point = """
            package foo.bar;
            public class Point {
                public int x;
                public int y;
            }
            """.toJFO("foo.bar.Point")
        val base = """
            package foo.bar;
            import ${Embedded::class.java.canonicalName};
            public class BaseClass<T> {
                @Embedded
                public T genericField;
            }
            """.toJFO("foo.bar.BaseClass")
        singleRunFullClass(
            """
                package foo.bar;
                public class MyPojo extends BaseClass<Point> {
                    public int normalField;
                }
                """,
            point, base
        ) { pojo, _ ->
            assertThat(pojo.fields.size, `is`(3))
            assertThat(pojo.fields.map { it.columnName }.toSet(), `is`(
                setOf("x", "y", "normalField")))
            val pointField = pojo.embeddedFields.first { it.field.name == "genericField" }
            assertThat(pointField.pojo.typeName,
                `is`(ClassName.get("foo.bar", "Point") as TypeName))
        }.compilesWithoutError()
    }

    @Test
    fun duplicateColumnNames() {
        singleRun(
                """
                int id;
                @ColumnInfo(name = "id")
                int another;
                """
        ) { _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.pojoDuplicateFieldNames("id", listOf("id", "another"))
        ).and().withErrorContaining(
                POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME
        ).and().withErrorCount(3)
    }

    @Test
    fun duplicateColumnNamesFromEmbedded() {
        singleRun(
                """
                int id;
                @Embedded
                Foo foo;
                static class Foo {
                    @ColumnInfo(name = "id")
                    int x;
                }
                """
        ) { _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.pojoDuplicateFieldNames("id", listOf("id", "foo > x"))
        ).and().withErrorContaining(
                POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME
        ).and().withErrorCount(3)
    }

    @Test
    fun dropSubPrimaryKeyNoWarningForPojo() {
        singleRun(
                """
                @PrimaryKey
                int id;
                @Embedded
                Point myPoint;
                static class Point {
                    @PrimaryKey
                    int x;
                    int y;
                }
                """
        ) { _ ->
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_view() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<UserSummary> user;
                """, COMMON.USER_SUMMARY
        ) { _ ->
        }.compilesWithoutError()
    }

    @Test
    fun relation_notCollection() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public User user;
                """, COMMON.USER
        ) { _ ->
        }.compilesWithoutError()
    }

    @Test
    fun relation_columnInfo() {
        singleRun(
                """
                int id;
                @ColumnInfo
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<User> user;
                """, COMMON.USER
        ) { _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.CANNOT_USE_MORE_THAN_ONE_POJO_FIELD_ANNOTATION)
    }

    @Test
    fun relation_notEntity() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<NotAnEntity> user;
                """, COMMON.NOT_AN_ENTITY
        ) { _ ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.NOT_ENTITY_OR_VIEW)
    }

    @Test
    fun relation_missingParent() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "idk", entityColumn = "uid")
                public List<User> user;
                """, COMMON.USER
        ) { _ ->
        }.failsToCompile().withErrorContaining(
                relationCannotFindParentEntityField("foo.bar.MyPojo", "idk", listOf("id"))
        )
    }

    @Test
    fun relation_missingEntityField() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "idk")
                public List<User> user;
                """, COMMON.USER
        ) { _ ->
        }.failsToCompile().withErrorContaining(
                relationCannotFindEntityField("foo.bar.User", "idk",
                        listOf("uid", "name", "lastName", "age"))
        )
    }

    @Test
    fun relation_missingType() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<User> user;
                """
        ) { _ ->
        }.failsToCompile().withErrorContaining(CANNOT_FIND_TYPE)
    }

    @Test
    fun relation_nestedField() {
        singleRun(
                """
                static class Nested {
                    @ColumnInfo(name = "foo")
                    public int id;
                }
                @Embedded
                Nested nested;
                @Relation(parentColumn = "foo", entityColumn = "uid")
                public List<User> user;
                """, COMMON.USER
        ) { pojo ->
            assertThat(pojo.relations.first().parentField.columnName, `is`("foo"))
        }.compilesWithoutError()
    }

    @Test
    fun relation_nestedRelation() {
        singleRun(
                """
                static class UserWithNested {
                    @Embedded
                    public User user;
                    @Relation(parentColumn = "uid", entityColumn = "uid")
                    public List<User> selfs;
                }
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid", entity = User.class)
                public List<UserWithNested> user;
                """, COMMON.USER
        ) { pojo, _ ->
            assertThat(pojo.relations.first().parentField.name, `is`("id"))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_affinityMismatch() {
        singleRun(
                """
                String id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<User> user;
                """, COMMON.USER
        ) { pojo, invocation ->
            // trigger assignment evaluation
            RelationCollector.createCollectors(invocation.context, pojo.relations)
            assertThat(pojo.relations.size, `is`(1))
            assertThat(pojo.relations.first().entityField.name, `is`("uid"))
            assertThat(pojo.relations.first().parentField.name, `is`("id"))
        }.compilesWithoutError().withWarningContaining(
                ProcessorErrors.relationAffinityMismatch(
                        parentAffinity = SQLTypeAffinity.TEXT,
                        childAffinity = SQLTypeAffinity.INTEGER,
                        parentColumn = "id",
                        childColumn = "uid")
        )
    }

    @Test
    fun relation_simple() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<User> user;
                """, COMMON.USER
        ) { pojo ->
            assertThat(pojo.relations.size, `is`(1))
            assertThat(pojo.relations.first().entityField.name, `is`("uid"))
            assertThat(pojo.relations.first().parentField.name, `is`("id"))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_badProjection() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid", projection={"i_dont_exist"})
                public List<User> user;
                """, COMMON.USER
        ) { _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.relationBadProject("foo.bar.User", listOf("i_dont_exist"),
                        listOf("uid", "name", "lastName", "ageColumn"))
        )
    }

    @Test
    fun relation_badReturnTypeInGetter() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                private List<User> user;
                public void setUser(List<User> user){ this.user = user;}
                public User getUser(){return null;}
                """, COMMON.USER
        ) { _ ->
        }.failsToCompile().withErrorContaining(CANNOT_FIND_GETTER_FOR_FIELD)
    }

    @Test
    fun relation_primitiveList() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid",  projection={"uid"},
                        entity = User.class)
                public List<Integer> userIds;
                """, COMMON.USER
        ) { pojo ->
            assertThat(pojo.relations.size, `is`(1))
            val rel = pojo.relations.first()
            assertThat(rel.projection, `is`(listOf("uid")))
            assertThat(rel.entity.typeName, `is`(COMMON.USER_TYPE_NAME as TypeName))
        }.compilesWithoutError()
    }

    @Test
    fun relation_stringList() {
        singleRun(
                """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid",  projection={"name"},
                        entity = User.class)
                public List<String> userNames;
                """, COMMON.USER
        ) { pojo ->
            assertThat(pojo.relations.size, `is`(1))
            val rel = pojo.relations.first()
            assertThat(rel.projection, `is`(listOf("name")))
            assertThat(rel.entity.typeName, `is`(COMMON.USER_TYPE_NAME as TypeName))
        }.compilesWithoutError()
    }

    @Test
    fun relation_extendsBounds() {
        singleRun(
            """
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                public List<? extends User> user;
                """, COMMON.USER
        ) { pojo ->
            assertThat(pojo.relations.size, `is`(1))
            assertThat(pojo.relations.first().entityField.name, `is`("uid"))
            assertThat(pojo.relations.first().parentField.name, `is`("id"))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_associateBy() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity(
                primaryKeys = {"uid","friendId"},
                foreignKeys = {
                    @ForeignKey(
                            entity = User.class,
                            parentColumns = "uid",
                            childColumns = "uid",
                            onDelete = ForeignKey.CASCADE),
                    @ForeignKey(
                            entity = User.class,
                            parentColumns = "uid",
                            childColumns = "friendId",
                            onDelete = ForeignKey.CASCADE),
                },
                indices = { @Index("uid"), @Index("friendId") }
            )
            public class UserFriendsXRef {
                public int uid;
                public int friendId;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int id;
                @Relation(
                    parentColumn = "id", entityColumn = "uid",
                    associateBy = @Junction(
                        value = UserFriendsXRef.class,
                        parentColumn = "uid", entityColumn = "friendId")
                )
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { pojo ->
            assertThat(pojo.relations.size, `is`(1))
            assertThat(pojo.relations.first().junction, notNullValue())
            assertThat(pojo.relations.first().junction!!.parentField.columnName,
                `is`("uid"))
            assertThat(pojo.relations.first().junction!!.entityField.columnName,
                `is`("friendId"))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_associateBy_withView() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @DatabaseView("SELECT 1, 2, FROM User")
            public class UserFriendsXRefView {
                public int uid;
                public int friendId;
            }
        """.toJFO("foo.bar.UserFriendsXRefView")
        singleRun(
            """
                int id;
                @Relation(
                    parentColumn = "id", entityColumn = "uid",
                    associateBy = @Junction(
                        value = UserFriendsXRefView.class,
                        parentColumn = "uid", entityColumn = "friendId")
                )
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_associateBy_defaultColumns() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity(
                primaryKeys = {"uid","friendId"},
                foreignKeys = {
                    @ForeignKey(
                            entity = User.class,
                            parentColumns = "uid",
                            childColumns = "uid",
                            onDelete = ForeignKey.CASCADE),
                    @ForeignKey(
                            entity = User.class,
                            parentColumns = "uid",
                            childColumns = "friendId",
                            onDelete = ForeignKey.CASCADE),
                },
                indices = { @Index("uid"), @Index("friendId") }
            )
            public class UserFriendsXRef {
                public int uid;
                public int friendId;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int friendId;
                @Relation(
                    parentColumn = "friendId", entityColumn = "uid",
                    associateBy = @Junction(UserFriendsXRef.class))
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun relation_associateBy_missingParentColumn() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity(primaryKeys = {"friendFrom","uid"})
            public class UserFriendsXRef {
                public int friendFrom;
                public int uid;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int id;
                @Relation(
                    parentColumn = "id", entityColumn = "uid",
                    associateBy = @Junction(UserFriendsXRef.class)
                )
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.failsToCompile().withErrorContaining(relationCannotFindJunctionParentField(
            "foo.bar.UserFriendsXRef", "id", listOf("friendFrom", "uid")))
    }

    @Test
    fun relation_associateBy_missingEntityColumn() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity(primaryKeys = {"friendA","friendB"})
            public class UserFriendsXRef {
                public int friendA;
                public int friendB;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int friendA;
                @Relation(
                    parentColumn = "friendA", entityColumn = "uid",
                    associateBy = @Junction(UserFriendsXRef.class)
                )
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.failsToCompile().withErrorContaining(relationCannotFindJunctionEntityField(
            "foo.bar.UserFriendsXRef", "uid", listOf("friendA", "friendB"))
        )
    }

    @Test
    fun relation_associateBy_missingSpecifiedParentColumn() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity(primaryKeys = {"friendA","friendB"})
            public class UserFriendsXRef {
                public int friendA;
                public int friendB;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int friendA;
                @Relation(
                    parentColumn = "friendA", entityColumn = "uid",
                    associateBy = @Junction(
                        value = UserFriendsXRef.class,
                        parentColumn = "bad_col")
                )
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.failsToCompile().withErrorContaining(relationCannotFindJunctionParentField(
            "foo.bar.UserFriendsXRef", "bad_col", listOf("friendA", "friendB"))
        )
    }

    @Test
    fun relation_associateBy_missingSpecifiedEntityColumn() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity(primaryKeys = {"friendA","friendB"})
            public class UserFriendsXRef {
                public int friendA;
                public int friendB;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int friendA;
                @Relation(
                    parentColumn = "friendA", entityColumn = "uid",
                    associateBy = @Junction(
                        value = UserFriendsXRef.class,
                        entityColumn = "bad_col")
                )
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.failsToCompile().withErrorContaining(relationCannotFindJunctionEntityField(
            "foo.bar.UserFriendsXRef", "bad_col", listOf("friendA", "friendB"))
        )
    }

    @Test
    fun relation_associateBy_warnIndexOnJunctionColumn() {
        val junctionEntity = """
            package foo.bar;

            import androidx.room.*;

            @Entity
            public class UserFriendsXRef {
                @PrimaryKey(autoGenerate = true)
                public long rowid;
                public int uid;
                public int friendId;
            }
        """.toJFO("foo.bar.UserFriendsXRef")
        singleRun(
            """
                int friendId;
                @Relation(
                    parentColumn = "friendId", entityColumn = "uid",
                    associateBy = @Junction(UserFriendsXRef.class))
                public List<User> user;
                """, COMMON.USER, junctionEntity
        ) { _ ->
        }.compilesWithoutError().withWarningCount(2).withWarningContaining(
                junctionColumnWithoutIndex("foo.bar.UserFriendsXRef", "uid"))
    }

    @Test
    fun cache() {
        val pojo = """
            $HEADER
            int id;
            $FOOTER
            """.toJFO(MY_POJO.toString())
        simpleRun(pojo) { invocation ->
            val element = invocation.typeElement(MY_POJO.toString())
            val pojo1 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.BIND_TO_STMT, null).process()
            assertThat(pojo1, notNullValue())
            val pojo2 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.BIND_TO_STMT, null).process()
            assertThat(pojo2, sameInstance(pojo1))

            val pojo3 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.READ_FROM_CURSOR, null).process()
            assertThat(pojo3, notNullValue())
            assertThat(pojo3, not(sameInstance(pojo1)))

            val pojo4 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.TWO_WAY, null).process()
            assertThat(pojo4, notNullValue())
            assertThat(pojo4, not(sameInstance(pojo1)))
            assertThat(pojo4, not(sameInstance(pojo3)))

            val pojo5 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.TWO_WAY, null).process()
            assertThat(pojo5, sameInstance(pojo4))

            val type = invocation.context.COMMON_TYPES.STRING
            val mockElement = mock(Element::class.java)
            doReturn(type).`when`(mockElement).asType()
            val fakeField = Field(
                    element = mockElement,
                    name = "foo",
                    type = type,
                    affinity = SQLTypeAffinity.TEXT,
                    columnName = "foo",
                    parent = null,
                    indexed = false
            )
            val fakeEmbedded = EmbeddedField(fakeField, "", null)

            val pojo6 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.TWO_WAY, fakeEmbedded).process()
            assertThat(pojo6, notNullValue())
            assertThat(pojo6, not(sameInstance(pojo1)))
            assertThat(pojo6, not(sameInstance(pojo3)))
            assertThat(pojo6, not(sameInstance(pojo4)))

            val pojo7 = PojoProcessor.createFor(invocation.context, element,
                    FieldProcessor.BindingScope.TWO_WAY, fakeEmbedded).process()
            assertThat(pojo7, sameInstance(pojo6))
        }.compilesWithoutError()
    }

    @Test
    fun constructor_empty() {
        val pojoCode = """
            public String mName;
            """
        singleRun(pojoCode) { pojo ->
            assertThat(pojo.constructor, notNullValue())
            assertThat(pojo.constructor?.params, `is`(emptyList()))
        }.compilesWithoutError()
    }

    @Test
    fun constructor_ambiguous_twoFieldsExactMatch() {
        val pojoCode = """
            public String mName;
            public String _name;
            public MyPojo(String mName) {
            }
            """
        singleRun(pojoCode) { pojo ->
            val param = pojo.constructor?.params?.first()
            assertThat(param, instanceOf(Constructor.Param.FieldParam::class.java))
            assertThat((param as Constructor.Param.FieldParam).field.name, `is`("mName"))
            assertThat(pojo.fields.find { it.name == "mName" }?.setter?.callType,
                    `is`(CallType.CONSTRUCTOR))
        }.compilesWithoutError()
    }

    @Test
    fun constructor_ambiguous_oneTypeMatches() {
        val pojoCode = """
            public String mName;
            public int _name;
            public MyPojo(String name) {
            }
            """
        singleRun(pojoCode) { pojo ->
            val param = pojo.constructor?.params?.first()
            assertThat(param, instanceOf(Constructor.Param.FieldParam::class.java))
            assertThat((param as Constructor.Param.FieldParam).field.name, `is`("mName"))
            assertThat(pojo.fields.find { it.name == "mName" }?.setter?.callType,
                    `is`(CallType.CONSTRUCTOR))
        }.compilesWithoutError()
    }

    @Test
    fun constructor_ambiguous_twoFields() {
        val pojo = """
            String mName;
            String _name;
            public MyPojo(String name) {
            }
            """
        singleRun(pojo) { _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.ambigiousConstructor(MY_POJO.toString(),
                        "name", listOf("mName", "_name"))
        )
    }

    @Test
    fun constructor_noMatchBadType() {
        singleRun("""
            int foo;
            public MyPojo(String foo) {
            }
        """) { _ ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.MISSING_POJO_CONSTRUCTOR)
    }

    @Test
    fun constructor_noMatch() {
        singleRun("""
            String mName;
            String _name;
            public MyPojo(String foo) {
            }
        """) { _ ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.MISSING_POJO_CONSTRUCTOR)
    }

    @Test
    fun constructor_noMatchMultiArg() {
        singleRun("""
            String mName;
            int bar;
            public MyPojo(String foo, String name) {
            }
        """) { _ ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.MISSING_POJO_CONSTRUCTOR)
    }

    @Test
    fun constructor_multipleMatching() {
        singleRun("""
            String mName;
            String mLastName;
            public MyPojo(String name) {
            }
            public MyPojo(String name, String lastName) {
            }
        """) { _ ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.TOO_MANY_POJO_CONSTRUCTORS)
    }

    @Test
    fun constructor_multipleMatchingWithIgnored() {
        singleRun("""
            String mName;
            String mLastName;
            @Ignore
            public MyPojo(String name) {
            }
            public MyPojo(String name, String lastName) {
            }
        """) { pojo ->
            assertThat(pojo.constructor, notNullValue())
            assertThat(pojo.constructor?.params?.size, `is`(2))
            assertThat(pojo.fields.find { it.name == "mName" }?.setter?.callType,
                    `is`(CallType.CONSTRUCTOR))
            assertThat(pojo.fields.find { it.name == "mLastName" }?.setter?.callType,
                    `is`(CallType.CONSTRUCTOR))
        }.compilesWithoutError()
    }

    @Test
    fun constructor_dontTryForBindToScope() {
        singleRun("""
            String mName;
            String mLastName;
        """) { _, invocation ->
            val process2 = PojoProcessor.createFor(context = invocation.context,
                    element = invocation.typeElement(MY_POJO.toString()),
                    bindingScope = FieldProcessor.BindingScope.BIND_TO_STMT,
                    parent = null).process()
            assertThat(process2.constructor, nullValue())
        }.compilesWithoutError()
    }

    @Test
    fun constructor_bindForTwoWay() {
        singleRun("""
            String mName;
            String mLastName;
        """) { _, invocation ->
            val process2 = PojoProcessor.createFor(context = invocation.context,
                    element = invocation.typeElement(MY_POJO.toString()),
                    bindingScope = FieldProcessor.BindingScope.TWO_WAY,
                    parent = null).process()
            assertThat(process2.constructor, notNullValue())
        }.compilesWithoutError()
    }

    @Test
    fun constructor_multipleMatching_withNoArg() {
        singleRun("""
            String mName;
            String mLastName;
            public MyPojo() {
            }
            public MyPojo(String name, String lastName) {
            }
        """) { pojo ->
            assertThat(pojo.constructor?.params?.size ?: -1, `is`(0))
        }.compilesWithoutError().withWarningContaining(
                ProcessorErrors.TOO_MANY_POJO_CONSTRUCTORS_CHOOSING_NO_ARG)
    }

    @Test // added for b/69562125
    fun constructor_withNullabilityAnnotation() {
        singleRun("""
            String mName;
            public MyPojo(@androidx.annotation.NonNull String name) {}
            """) { pojo ->
            val constructor = pojo.constructor
            assertThat(constructor, notNullValue())
            assertThat(constructor!!.params.size, `is`(1))
        }.compilesWithoutError()
    }

    @Test
    fun constructor_relationParameter() {
        singleRun("""
            @Relation(entity = foo.bar.User.class, parentColumn = "uid", entityColumn="uid",
            projection = "name")
            public List<String> items;
            public String uid;
            public MyPojo(String uid, List<String> items) {
            }
            """, COMMON.USER) { _ ->
        }.compilesWithoutError()
    }

    @Test
    fun recursion_1Level_embedded() {
        singleRun(
                """
                @Embedded
                MyPojo myPojo;
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_1Level_relation() {
        singleRun(
                """
                long id;
                long parentId;
                @Relation(parentColumn = "id", entityColumn = "parentId")
                Set<MyPojo> children;
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_1Level_relation_specifyEntity() {
        singleRun(
                """
                @Embedded
                A a;

                static class A {
                    long id;
                    long parentId;
                    @Relation(entity = A.class, parentColumn = "id", entityColumn = "parentId")
                    Set<AWithB> children;
                }

                static class B {
                   long id;
                }

                static class AWithB {
                    @Embedded
                    A a;
                    @Embedded
                    B b;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo.A -> foo.bar.MyPojo.A"))
    }

    @Test
    fun recursion_2Levels_relationToEmbed() {
        singleRun(
                """
                int pojoId;

                @Relation(parentColumn = "pojoId", entityColumn = "entityId")
                List<MyEntity> myEntity;

                @Entity
                static class MyEntity {
                    int entityId;

                    @Embedded
                    MyPojo myPojo;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo.MyEntity -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_2Levels_onlyEmbeds_pojoToEntity() {
        singleRun(
                """
                @Embedded
                A a;

                @Entity
                static class A {
                    @Embedded
                    MyPojo myPojo;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo.A -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_2Levels_onlyEmbeds_onlyPojos() {
        singleRun(
                """
                @Embedded
                A a;
                static class A {
                    @Embedded
                    MyPojo myPojo;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo.A -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_2Level_relationToEmbed() {
        singleRun(
                """
                @Embedded
                A a;

                static class A {
                    long id;
                    long parentId;
                    @Relation(parentColumn = "id", entityColumn = "parentId")
                    Set<AWithB> children;
                }

                static class B {
                   long id;
                }

                static class AWithB {
                    @Embedded
                    A a;
                    @Embedded
                    B b;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo.A -> foo.bar.MyPojo.AWithB -> foo.bar.MyPojo.A"))
    }

    @Test
    fun recursion_3Levels() {
        singleRun(
                """
                @Embedded
                A a;
                public static class A {
                    @Embedded
                    B b;
                }
                public static class B {
                    @Embedded
                    MyPojo myPojo;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo.A -> foo.bar.MyPojo.B -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_1Level_1LevelDown() {
        singleRun(
                """
                @Embedded
                A a;
                static class A {
                    @Embedded
                    B b;
                }
                static class B {
                    @Embedded
                    A a;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo.A -> foo.bar.MyPojo.B -> foo.bar.MyPojo.A"))
    }

    @Test
    fun recursion_branchAtLevel0_afterBackTrack() {
        singleRun(
                """
                @PrimaryKey
                int id;
                @Embedded
                A a;
                @Embedded
                C c;
                static class A {
                    @Embedded
                    B b;
                }
                static class B {
                }
                static class C {
                    @Embedded
                    MyPojo myPojo;
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo.C -> foo.bar.MyPojo"))
    }

    @Test
    fun recursion_branchAtLevel1_afterBackTrack() {
        singleRun(
                """
                @PrimaryKey
                int id;
                @Embedded
                A a;
                static class A {
                    @Embedded
                    B b;
                    @Embedded
                    MyPojo myPojo;
                }
                static class B {
                    @Embedded
                    C c;
                }
                static class C {
                }
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.RECURSIVE_REFERENCE_DETECTED.format(
                        "foo.bar.MyPojo -> foo.bar.MyPojo.A -> foo.bar.MyPojo"))
    }

    @Test
    fun dataClass_primaryConstructor() {
        listOf(
                TestData.AllDefaultVals::class.java.canonicalName,
                TestData.AllDefaultVars::class.java.canonicalName,
                TestData.SomeDefaultVals::class.java.canonicalName,
                TestData.SomeDefaultVars::class.java.canonicalName,
                TestData.WithJvmOverloads::class.java.canonicalName
        ).forEach {
            simpleRun { invocation ->
                PojoProcessor.createFor(
                        context = invocation.context,
                        element = invocation.typeElement(it),
                        bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                        parent = null
                ).process()
            }.compilesWithoutError().withWarningCount(0)
        }
    }

    @Test
    fun dataClass_withJvmOverloads_primaryConstructor() {
        simpleRun { invocation ->
            PojoProcessor.createFor(
                    context = invocation.context,
                    element = invocation.typeElement(
                            TestData.WithJvmOverloads::class.java.canonicalName
                    ),
                    bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                    parent = null
            ).process()
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun ignoredColumns() {
        simpleRun(
                """
                package foo.bar;
                import androidx.room.*;
                public class ${MY_POJO.simpleName()} {
                    public String foo;
                    public String bar;
                }
                """.toJFO(MY_POJO.toString())) { invocation ->
            val pojo = PojoProcessor.createFor(context = invocation.context,
                    element = invocation.typeElement(MY_POJO.toString()),
                    bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                    ignoredColumns = setOf("bar"),
                    parent = null).process()
            assertThat(pojo.fields.find { it.name == "foo" }, notNullValue())
            assertThat(pojo.fields.find { it.name == "bar" }, nullValue())
        }.compilesWithoutError()
    }

    @Test
    fun ignoredColumns_noConstructor() {
        simpleRun(
            """
                package foo.bar;
                import androidx.room.*;
                public class ${MY_POJO.simpleName()} {
                    private final String foo;
                    private final String bar;

                    public ${MY_POJO.simpleName()}(String foo) {
                      this.foo = foo;
                      this.bar = null;
                    }

                    public String getFoo() {
                      return this.foo;
                    }
                }
                """.toJFO(MY_POJO.toString())) { invocation ->
            val pojo = PojoProcessor.createFor(context = invocation.context,
                element = invocation.typeElement(MY_POJO.toString()),
                bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                ignoredColumns = setOf("bar"),
                parent = null).process()
            assertThat(pojo.fields.find { it.name == "foo" }, notNullValue())
            assertThat(pojo.fields.find { it.name == "bar" }, nullValue())
        }.compilesWithoutError()
    }

    @Test
    fun ignoredColumns_noSetterGetter() {
        simpleRun(
            """
                package foo.bar;
                import androidx.room.*;
                public class ${MY_POJO.simpleName()} {
                    private String foo;
                    private String bar;

                    public String getFoo() {
                      return this.foo;
                    }

                    public void setFoo(String foo) {
                      this.foo = foo;
                    }
                }
                """.toJFO(MY_POJO.toString())) { invocation ->
            val pojo = PojoProcessor.createFor(context = invocation.context,
                element = invocation.typeElement(MY_POJO.toString()),
                bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                ignoredColumns = setOf("bar"),
                parent = null).process()
            assertThat(pojo.fields.find { it.name == "foo" }, notNullValue())
            assertThat(pojo.fields.find { it.name == "bar" }, nullValue())
        }.compilesWithoutError()
    }

    @Test
    fun ignoredColumns_columnInfo() {
        simpleRun(
                """
                package foo.bar;
                import androidx.room.*;
                public class ${MY_POJO.simpleName()} {
                    public String foo;
                    @ColumnInfo(name = "my_bar")
                    public String bar;
                }
                """.toJFO(MY_POJO.toString())) { invocation ->
            val pojo = PojoProcessor.createFor(context = invocation.context,
                    element = invocation.typeElement(MY_POJO.toString()),
                    bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                    ignoredColumns = setOf("my_bar"),
                    parent = null).process()
            assertThat(pojo.fields.find { it.name == "foo" }, notNullValue())
            assertThat(pojo.fields.find { it.name == "bar" }, nullValue())
        }.compilesWithoutError()
    }

    @Test
    fun ignoredColumns_missing() {
        simpleRun(
                """
                package foo.bar;
                import androidx.room.*;
                public class ${MY_POJO.simpleName()} {
                    public String foo;
                    public String bar;
                }
                """.toJFO(MY_POJO.toString())) { invocation ->
            val pojo = PojoProcessor.createFor(context = invocation.context,
                    element = invocation.typeElement(MY_POJO.toString()),
                    bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                    ignoredColumns = setOf("no_such_column"),
                    parent = null).process()
            assertThat(pojo.fields.find { it.name == "foo" }, notNullValue())
            assertThat(pojo.fields.find { it.name == "bar" }, notNullValue())
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.missingIgnoredColumns(listOf("no_such_column")))
    }

    private fun singleRun(
        code: String,
        vararg jfos: JavaFileObject,
        handler: (Pojo) -> Unit
    ): CompileTester {
        return singleRun(code, *jfos) { pojo, _ ->
            handler(pojo)
        }
    }

    private fun singleRun(
        code: String,
        vararg jfos: JavaFileObject,
        classLoader: ClassLoader = javaClass.classLoader,
        handler: (Pojo, TestInvocation) -> Unit
    ): CompileTester {
        val pojoCode = """
                $HEADER
                $code
                $FOOTER
                """
        return singleRunFullClass(
            code = pojoCode,
            jfos = *jfos,
            classLoader = classLoader,
            handler = handler
        )
    }

    private fun singleRunFullClass(
        code: String,
        vararg jfos: JavaFileObject,
        classLoader: ClassLoader = javaClass.classLoader,
        handler: (Pojo, TestInvocation) -> Unit
    ): CompileTester {
        val pojoJFO = code.toJFO(MY_POJO.toString())
        val all = (jfos.toList() + pojoJFO).toTypedArray()
        return simpleRun(*all, classLoader = classLoader) { invocation ->
            handler.invoke(
                PojoProcessor.createFor(context = invocation.context,
                        element = invocation.typeElement(MY_POJO.toString()),
                        bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                        parent = null).process(),
                invocation
            )
        }
    }

    // Kotlin data classes to verify the PojoProcessor.
    private class TestData {
        data class AllDefaultVals(
            val name: String = "",
            val number: Int = 0,
            val bit: Boolean = false
        )

        data class AllDefaultVars(
            var name: String = "",
            var number: Int = 0,
            var bit: Boolean = false
        )

        data class SomeDefaultVals(
            val name: String,
            val number: Int = 0,
            val bit: Boolean
        )

        data class SomeDefaultVars(
            var name: String,
            var number: Int = 0,
            var bit: Boolean
        )

        data class WithJvmOverloads @JvmOverloads constructor(
            val name: String,
            val lastName: String = "",
            var number: Int = 0,
            var bit: Boolean
        )
    }
}
