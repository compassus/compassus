# Changelog

## master (unreleased)

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
