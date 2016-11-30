# MyBatis Query by Example

[![Build Status](https://travis-ci.org/jeffgbutler/mybatis-qbe.svg?branch=master)](https://travis-ci.org/jeffgbutler/mybatis-qbe)
[![Coverage Status](https://coveralls.io/repos/github/jeffgbutler/mybatis-qbe/badge.svg?branch=master)](https://coveralls.io/github/jeffgbutler/mybatis-qbe?branch=master)

## What Is This?
This library is a framework for creating dynamic clauses for SQL statements.

- In support of DELETE statements, the library will generate a WHERE clause
- In support of INSERT statements, the library will generate a field list and matching VALUES clause
- In support of SELECT statements, the library will generate a WHERE clause and supports "distinct" and
  "order by" attributes
- In support of UPDATE statements, the library will generate SET and WHERE clauses

The primary goals of the library are:

1. Typesafe - to the extent possible, the library will ensure that parameter types match
   the database field types
2. Expressive - clauses are built in a way that clearly communicates their meaning
   (thanks to Hamcrest for some inspiration)
3. Flexible - where clauses can be built using any combination of and, or, and nested conditions
4. Extensible - the library will render clauses for MyBatis3 or plain JDBC.  It can be extended to
   generate clauses for other frameworks as well.  Custom where conditions can be added easily
   if none of the built in conditions are sufficient for your needs. 
5. Small - the library is a very small dependency to add.  It has no transitive dependencies.
   
This library grew out of a desire to create a utility that could be used to improve the code
generated by MyBatis generator, but the library can be used on it's own with very little setup required.

## Requirements

The library has no dependencies.  Java 8 is required.

## Show Me an Example
One capability is that very expressive dynamic queries can be generated.  Here's an example of what's possible:

```java
    @Test
    public void testComplexCondition() {
        SqlSession sqlSession = sqlSessionFactory.openSession();
        try {
            AnimalDataMapper mapper = sqlSession.getMapper(AnimalDataMapper.class);
            
            SelectSupport selectSupport = selectSupport()
                    .where(id, isIn(1, 5, 7))
                    .or(id, isIn(2, 6, 8), and(animalName, isLike("%bat")))
                    .or(id, isGreaterThan(60))
                    .and(bodyWeight, isBetween(1.0).and(3.0))
                    .build();

            List<AnimalData> animals = mapper.selectByExample(selectSupport);
            assertThat(animals.size(), is(4));
        } finally {
            sqlSession.close();
        }
    }
```

## How Do I Use It?
The following discussion will walk through an example of using the library to generate a dynamic
where clause for a SELECT or DELETE statement.  The full source code
for this example is in ```src/test/java/examples/simple``` in this repo.

The database table used in the example is defined as follows:

```sql
create table SimpleTable (
   id int not null,
   first_name varchar(30) not null,
   last_name varchar(30) not null,
   birth_date date not null, 
   occupation varchar(30) null,
   primary key(id)
);
```
 
### First - Define database fields
The class ```org.mybatis.qbe.mybatis3.MyBatis3Field``` is used to define fields for use in the library.
Typically these should be defined as public static variables in a class or interface.  A field definition includes:

1. The Java type
2. The field name
3. The JDBC type
4. (optional) An alias if used in a query that aliases the table
5. (optional) The name of a type handler to use in MyBatis if the default type handler is not desired

For example:

```java
package examples.simple;

import java.sql.JDBCType;
import java.util.Date;

import org.mybatis.qbe.mybatis3.MyBatis3Field;

public interface SimpleTableFields {
    MyBatis3Field<Integer> id = MyBatis3Field.of("id", JDBCType.INTEGER).withAlias("a");
    MyBatis3Field<String> firstName = MyBatis3Field.of("first_name", JDBCType.VARCHAR).withAlias("a");
    MyBatis3Field<String> lastName = MyBatis3Field.of("last_name", JDBCType.VARCHAR).withAlias("a");
    MyBatis3Field<Date> birthDate = MyBatis3Field.of("birth_date", JDBCType.DATE).withAlias("a");
    MyBatis3Field<String> occupation = MyBatis3Field.of("occupation", JDBCType.VARCHAR).withAlias("a");
}
```

### Second - Write XML or annotated mappers that will use the generated where clause
The library will create support classes that will be used as input to an annotated or XML mapper.  These classes includes the generated where clause, as well as a parameter set that will match the generated clause.  Both are required by MyBatis3.  It is intended that these objects be the one and only parameter to a MyBatis method.

For example, an annotated mapper might look like this:

```java
package examples.simple;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.mybatis.qbe.sql.delete.DeleteSupport;
import org.mybatis.qbe.sql.select.SelectSupport;

public class SimpleTableAnnotatedMapper {
    
    @Select({
        "select ${distinct} a.id, a.first_name, a.last_name, a.birth_date, a.occupation",
        "from simpletable a",
        "${whereClause}",
        "${orderByClause}"
    })
    @ResultMap("SimpleTableResult")
    List<SimpleTableRecord> selectByExample(SelectSupport selectSupport);

    @Delete({
        "delete from simpletable",
        "${whereClause}"
    })
    int deleteByExample(DeleteSupport deleteSupport);
}
```
An XML mapper might look like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="examples.simple.SimpleTableXmlMapper">

  <resultMap id="SimpleTableResult" type="examples.simple.SimpleTableRecord">
    <id column="id" jdbcType="INTEGER" property="id" />
    <result column="first_name" jdbcType="VARCHAR" property="firstName" />
    <result column="last_name" jdbcType="VARCHAR" property="lastName" />
    <result column="birth_date" jdbcType="DATE" property="birthDate" />
    <result column="occupation" jdbcType="VARCHAR" property="occupation" />
  </resultMap>

  <select id="selectByExample" resultMap="SimpleTableResult">
    select ${distinct} a.id, a.first_name, a.last_name, a.birth_date, a.occupation
    from SimpleTable a
    ${whereClause}
    ${orderByClause}
  </select>

  <delete id="deleteByExample">
    delete from SimpleTable
    ${whereClause}
  </delete>  
</mapper>
```

Notice in both examples that the select uses a table alias and the delete does not.  If you specify an alias
for fields, it will only be used for select statements.

### Third - Create where clauses for your queries
Where clauses are created by combining your field definition (from the first step above) with a condition for the field.  This library includes a large number of type safe conditions.
All conditions can be accessed through expressive static methods in the ```org.mybatis.qbe.sql.SqlConditions``` interface.

For example, a very simple condition can be defined like this:

```java
        SelectSupport selectSupport = selectSupport()
                .where(id, isEqualTo(3))
                .build();
```

Or this:

```java
        SelectSupport selectSupport = selectSupport()
                .where(id, isNull())
                .build();
```

The "between" condition is also expressive:

```java
        SelectSupport selectSupport = selectSupport()
                .where(id, isBetween(1).and(4))
                .build();
```

More complex expressions can be built using the "and" and "or" conditions as follows:

```java
        SelectSupport selectSupport = selectSupport()
                .where(id, isGreaterThan(2))
                .or(occupation, isNull(), and(id, isLessThan(6)))
                .build();
```

All of these statements rely on a set of expressive static methods.  It is typical to import the following:

```java
// import all field definitions for your table
import static examples.simple.SimpleTableFields.*;

// import all conditions and the where support builder
import static org.mybatis.qbe.sql.SqlConditions.*;
import static org.mybatis.qbe.sql.select.SelectSupportBuilder.selectSupport;
```

### Fourth - Use your where clauses
In a DAO or service class, you can use the generated where clause as input to your mapper methods.  Here's
an example from ```examples.simple.SimpleTableXmlMapperTest```:

```java
    @Test
    public void testSelectByExample() {
        SqlSession session = sqlSessionFactory.openSession();
        try {
            SimpleTableXmlMapper mapper = session.getMapper(SimpleTableXmlMapper.class);
            
            SelectSupport selectSupport = selectSupport()
                    .where(id, isEqualTo(1))
                    .or(occupation, isNull())
                    .build();
            
            List<SimpleTableRecord> rows = mapper.selectByExample(selectSupport);
            
            assertThat(rows.size(), is(3));
        } finally {
            session.close();
        }
    }
```
The code in the folder ```src/test/java/examples/simple``` shows how to use the library for INSERT and
UPDATE statements in addition to the examples shown here.  It shows a suggested usage of the library
to enable a complete range of CRUD operations on a database table.  Lastly, it is an example of the code that
could be created by a future version of MyBatis Generator.
