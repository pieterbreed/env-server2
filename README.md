# env-server

A service for sane environment configuration.

## Features

 - Define things called applications, which are a specifications for configuration requirements of real (software) applications.
 - Applications have version strings computed in an idempotent/consistent way.
 - Define things called environments (dicts), that are full or partial realizations of applications.
 - Environments have consistent version numbers based on their key and data values
 - Allow environments to be based on other environments, optionally overriding some of the valuese.

 - REST interface (un-implemented)
 - Allow environment variables to resolve their values using references to other values, ie, referencing. (un-implemented)
 - Allow environment variables to resolve their values using string interpolation syntax. (un-implemented)

## Concepts

### String Interpolation

([Wikipedia article on string interpolation here](http://en.wikipedia.org/wiki/String_interpolation))

The basic idea is that given this application:

```
UsefulConnectionString
```

and given this environment:

```
username=John
pass=P@ssw0rd
sqlDb=localhost
ConnectionStringFormat="user:$$user, password:$$password, database:$$database"
UsefulConnectionString="$ConnectionStringFormat$$$user=$username$$password=$pass$$database=$sqlDb"
```

that the application will be realized like this:

```
UsefulConnectionString="user:John, password:P@ssw0rd, database:localhost"
```



This is sometimes

### Referencing

Given the following application:

```
key2
key3
```

and given the following environment:

```
key1=value1
key2=22$key122
key3=333$key2333
```

*referencing* means that this application realized in this environment will get the following configuration:

```
key2=22value122
key3=333value1333
```

## License

Copyright © 2014 Pieter Breed

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
