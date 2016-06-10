#!/bin/bash

set -o xtrace
#set +o verbose

# Build the sources

boot release-gh-pages

# Create temporary directory

tmpdir=$(mktemp -d)

# Delete unnecessary files

rm -rf dist/js/devcards.out dist/js/devcards.cljs.edn

# Copy the latest build to it

cp -R dist/doc dist/js dist/*.html "$tmpdir"

# Switch to the gh-pages branch
git checkout gh-pages

# Remove the old sources
git rm -rf doc js *.html

# Copy the build into it
cp -R "$tmpdir"/* .
rm -rf "$tmpdir"

# Determine the latest commit in master
commit=$(git log -n1 --format="%H" master)

# Create a new commit from the new sources
git add doc js *.html
git commit -a -m "Update to $commit"

# Push gh-pages to GitHub
git push origin gh-pages:gh-pages

# Switch back to master
git checkout master

# Delete dist/ folder

rm -rf dist
