# env-server

A service for organizing environment configuration.

## Status: Slightly usefull

There is an in-memory implementation of a "database" so no persistence
accross restarts. This is usefull for testing the concepts and the
idea and dogfooding. Not a whole lot more though.

## What is the problem?

I work in an enterprise environment where a few different teams work
independently on different software systems but everything is deployed
together into environments and have to cooperate.

One of the hardest problems we have is identifying the requirements
(config) for each app and then fulfilling those requirements in a
reliable manner.

Most often, these requirements are identifiers for things like:

 - Databases
 - Fileservers
 - Web services
 - etc

Other things that can go into this list could possibly be:

 - Credentials
 - Per deploy values

([These are the things these guys at 12factor.net talk about](http://12factor.net/config))

## Facts

 - Written in clojure
 - Has a REST API with some content negotiation
 - Does not implement authentication and authorization (that's the job
   of something like `nginx`)

## Similar solutions?

[This project (escservesconfig)](https://code.google.com/p/escservesconfig/) by some Thoughtworks guys was the original inspiration
for this project.

<blockquote class="twitter-tweet" lang="en"><p><a href="https://twitter.com/pwab">@pwab</a> it's a thought experiment that's not getting any love theses days. First thing I would look at instead are etcd and zookeeper...</p>&mdash; Chris Read (@cread) <a href="https://twitter.com/cread/statuses/414909181763002368">December 23, 2013</a></blockquote>
<script async src="//platform.twitter.com/widgets.js"
charset="utf-8"></script>

<blockquote class="twitter-tweet" lang="en"><p><a href="https://twitter.com/pwab">@pwab</a> I wouldn't use esc in anger. It's not been updated for a very long time. Use as an example of the configurationProvider patter.</p>&mdash; Tom Sulston (@tomsulston) <a href="https://twitter.com/tomsulston/statuses/414291164431261697">December 21, 2013</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

## How it works

 - Define things called `applications`, which are a *specifications* for configuration requirements of real (software) applications.
 - Applications *have consistente version* strings computed in an idempotent/consistent way.
 - Define things called environments (dicts), that *key-value pairs*.
 - Environments have *consistent version* strings based on their key and data values
 - Allow environments to be based on other environments, optionally overriding some of the values.

 - REST interface

### Referencing (not done yet)

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
