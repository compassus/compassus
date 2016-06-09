# Compassus [![CircleCI](https://circleci.com/gh/anmonteiro/compassus.svg?style=svg&circle-token=de2d254556b53778560cfff5f354fffee6100501)](https://circleci.com/gh/anmonteiro/compassus)

A routing library for Om Next.

## Contents

- [Installation](#installation)
- [Guide](#guide)
  - [Declaring routes](#declaring-routes)
- [Copyright & License](#copyright--license)

## Installation

Leiningen dependency information:

```clojure
[compassus "0.1.0"]
```

Maven dependency information:

```xml
<dependency>
  <groupId>compassus</groupId>
  <artifactId>compassus</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Guide

To get started, require Compassus somewhere in your project.

```clojure
(ns my-app.core
  (:require [om.next :as om :refer-macros [defui]]
            [compassus.core :as c))
```

### Declaring routes

Your application's routes are represented by a map which keys are keywords (identifying the routes in your application) and the values are the respective Om Next component classes. The following example shows the routes for a simple application that has 2 routes, `:index` and `:about`:

```clojure
(defui Index
  ...)

(defui About
  ...)

(def routes
  {:index Index
   :about About})
```

## Copyright & License

Copyright © 2016 António Nuno Monteiro

Distributed under the Eclipse Public License (see [LICENSE](./LICENSE)).
