# env-server

A service for sane environment configuration.

## Features

 - Define things called applications, which are a specifications for configuration requirements of real (software) applications.
 - Applications have version strings computed in an idempotent/consistent way.
 - Define things called environments (dicts), that are full or partial realizations of applications.
 - Environments have consistent version numbers based on their data values
 - Allow environments to be based on other environments, optionally overriding some of the valuese.
 - Allow environment variables to resolve their values using references to other values, ie, interpolation.
 - Allow environment variables to resolve their values using string.format() syntax.

## Design rational

 - Things are created, but never updated

## License

Copyright © 2014 Pieter Breed

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
