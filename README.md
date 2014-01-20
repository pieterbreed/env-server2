# env-server

A service for sane environment configuration.

## Features

 - Define things called applications, which are a specifications for configuration requirements of real (software) applications.
 - Define things called environments, that are full or partial realizations of applications.
 - Allow environments to be based on other environments, optionally overriding some of the valuese.
 - Allow environment variables to resolve their values using references to other values, ie, interpolation.
 - Allow environment variables to resolve their values using string.format() syntax.

## Design rational

 - Things are created, but never updated

## License

Copyright © 2014 Pieter Breed

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
