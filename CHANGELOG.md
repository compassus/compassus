# Changelog

## [master](https://github.com/anmonteiro/compassus/compare/1.0.0-alpha3...HEAD) (unreleased)

## [1.0.0-alpha3](https://github.com/anmonteiro/compassus/compare/1.0.0-alpha2...1.0.0-alpha3) (2017-05-14)

### Changes

- The location of owner, factory and props in wrappers should be the same whether
components implement `IQuery` or not ([#25](https://github.com/compassus/compassus/issues/25)).
- Don't wrap the return of the parser in an extra vector ([@Peeja](https://github.com/Peeja)
in [#26](https://github.com/compassus/compassus/pull/26)).

### Bug fixes

- Make the (Clojure) server-side rendering code work with Om 1.0.0-alpha48 ([#28](https://github.com/compassus/compassus/issues/28)).
The fix to ([OM-842](https://github.com/omcljs/om/issues/842)) made Compassus incompatible
with the server-side rendering code.
- Fix reads against idents and links in the join key ([@matthavener](https://github.com/matthavener)
in [#27](https://github.com/compassus/compassus/pull/27)).

## [1.0.0-alpha2](https://github.com/anmonteiro/compassus/compare/1.0.0-alpha1...1.0.0-alpha2) (2016-11-10)

### Bug fixes

- The Om Next reconciler's `merge` function should queue `:compassus.core/route-data`
for re-read if any route keys are present in the remote result ([#16](https://github.com/compassus/compassus/issues/16)).
- The factory function passed to the wrapper component should not ignore its props argument
([#19](https://github.com/compassus/compassus/issues/19)).

## [1.0.0-alpha1](https://github.com/anmonteiro/compassus/compare/0.2.1...1.0.0-alpha1) (2016-10-23)

### New features

- Add support for routes to components without queries ([#5](https://github.com/compassus/compassus/issues/5)).
- Support additional options (`:params` and `:tx`) to `set-route!` ([#8](https://github.com/compassus/compassus/issues/8))
- Add mixins support. Read more about mixins [here](https://github.com/compassus/compassus/blob/master/README.md#mixins).
- Added new built-in mixins: `will-mount`, `did-mount` and `will-unmount`, which
hook into the respective lifecycle methods of the generated root component.
- Added an invariant that checks a route's factory must exist when rendering a route ([#14](https://github.com/compassus/compassus/issues/14)).
- Added the possibility to construct a parser that doesn't dispatch on the current
route, but instead on all the keys in the query of the component associated with
the route ([#12](https://github.com/compassus/compassus/issues/12)). Read more about
it [here](https://github.com/compassus/compassus/blob/master/README.md#implementing-the-parser).

### Changes

- Drop the Cellophane dependency, Om Next has server-side rendering support now (which
means that Compassus requires Om Next 1.0.0-alpha45 or later starting this version).
- **BREAKING**: removed the `:wrapper` option from the `compassus.core/application`
configuration map. Use the `compassus.core/wrap-render` mixin instead.
- **BREAKING**: removed the `:history` option from the `compassus.core/application`
configuration map. Use the new component lifecycle mixins instead.
- **BREAKING**: removed the `compassus.core/index-route` function. Use the `:index-route`
key in the configuration map passed to `compassus.core/application` instead. Example
[here](https://github.com/compassus/compassus#declaring-routes).
- **BREAKING**: replaced the `:reconciler-opts` key in the `compassus.core/application`
configuration map with a `:reconciler` key that takes an Om Next reconciler. Note
that the parser in this reconciler must be constructed with `compassus.core/parser`,
not `om.next/parser`. ([#7](https://github.com/compassus/compassus/issues/7))

### Bug fixes

- `queue?` in `compassus.core/set-route!` should default to true in all function arities ([#15](https://github.com/compassus/compassus/issues/15)).

## [0.2.1](https://github.com/anmonteiro/compassus/compare/0.2.0...0.2.1) (2016-07-11)

### Bug fixes

- Return the correct result and `:value`s in local mutations, instead of nested results that are a consequence of calling the user parser inside Compassus's parser.
- Make re-reading keys after `om.next/transact!` calls work properly (was missing a multimethod implementation).
- Make sure `::compassus/route` remains in the app state after a tempid migration

## [0.2.0](https://github.com/anmonteiro/compassus/compare/0.1.0...0.2.0) (2016-06-27)

### New features

- Port Compassus to `.cljc` so that it's usable from [Cellophane](https://github.com/ladderlife/cellophane).
- Include the current route in the parser's `env`, under the key `:route`.

### Changes

- Return the user-parser's AST in mutations with remote targets instead of just `true`

### Bug fixes

- Remove extra argument in the `set-route!` `mutate` function.
- Don't extract the current route from mutation results in `compassus-merge`.
- Tweak `compassus-merge` to extract the current route from the query passed to `om.next/default-merge`.
- Parser should return `target` and not a hardcoded `:remote` for remote integration.

## 0.1.0 (2016-06-12)

- Initial version
