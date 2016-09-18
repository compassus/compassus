# Changelog

## [master](https://github.com/anmonteiro/compassus/compare/0.2.1...HEAD) (unreleased)

### New features

- Add support for routes to components without queries ([#5](https://github.com/compassus/compassus/issues/5)).
- Support additional options (`:params` and `:tx`) to `set-route!` ([#8](https://github.com/compassus/compassus/issues/8))
- Add mixins support. Read more about mixins [here](https://github.com/compassus/compassus/blob/master/README.md#mixins).
- **BREAKING**: removed the `:wrapper` option from the `compassus.core/application`
configuration map. Use the `compassus.core/wrap-render` mixin instead.

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
