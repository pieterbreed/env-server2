# env-server

A service for sane environment configuration.

## Features

 - Define things called applications, which are a specifications for configuration requirements of real (software) applications.
 - Applications have version strings computed in an idempotent/consistent way.
 - Define things called environments (dicts), that are full or partial realizations of applications.
 - Environments have consistent version numbers based on their key and data values
 - Allow environments to be based on other environments, optionally overriding some of the valuese.
 - Allow environment variables to resolve their values using references to other values, ie, referencing.
 - Allow environment variables to resolve their values using string.format() syntax.

## Design rational

 - Things are created, but never updated

## Concepts

### String.format

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
