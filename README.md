# DBeaver Common
[![CI](https://github.com/dbeaver/dbeaver-common/actions/workflows/push-pr-devel.yml/badge.svg)](https://github.com/dbeaver/dbeaver-jdbc-libsql/actions/workflows/push-pr-devel.yml)
[![Apache 2.0](https://img.shields.io/github/license/cronn-de/jira-sync.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Common utils and dependencies for all [DBeaver](https://github.com/dbeaver) projects

This repository is a requirement for the following products:
- [DBeaver Community](https://github.com/dbeaver/dbeaver)
- [CloudBeaver Community](https://github.com/dbeaver/cloudbeaver)

It must be checked out in the same directory where the main repository is located.

What is inside:
- root: root Maven module for all projects. Defines base properties and dependencies (such as Tycho)
- modules: base Java utilities

## Modules:

### [org.jkiss.utils](modules%2Forg.jkiss.utils)

Various utilities (similar to Apache Commons and Google Guava).  
#### Maven:
```xml
    <dependency>
      <groupId>com.dbeaver.common</groupId>
      <artifactId>org.jkiss.utils</artifactId>
      <version>2.2.0</version>
    </dependency>
```

### [com.dbeaver.jdbc.api](modules%2Fcom.dbeaver.jdbc.api)

Base module for custom JDBC drivers development.  
Contains utility classes and classes implementing JDBC interfaces for 
driver, connection, statements, result sets and metadata read. 

#### Maven:
```xml
    <dependency>
      <groupId>com.dbeaver.common</groupId>
      <artifactId>com.dbeaver.jdbc.api</artifactId>
      <version>2.2.0</version>
    </dependency>
```

### [com.dbeaver.rpc](modules%2Fcom.dbeaver.rpc)

Simple RPC client-server implementation.

#### Maven:
```xml
    <dependency>
      <groupId>com.dbeaver.common</groupId>
      <artifactId>com.dbeaver.rpc</artifactId>
      <version>2.2.0</version>
    </dependency>
```
