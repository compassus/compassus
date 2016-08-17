#!/bin/bash
set -e
set -o xtrace

DIR=dist
MASTER_DIR=checkout

GIT_REPO_HEAD_SHA=$(git rev-parse --short HEAD)
GIT_REPO_REMOTE_URL=$(git config --get remote.origin.url)
GIT_MASTER_BRANCH=master
GIT_DEPLOY_BRANCH=gh-pages
GIT_SUBTREE_OPTS="--git-dir=$DIR/.git --work-tree=$DIR"


rm -rf $DIR

git clone -b $GIT_MASTER_BRANCH --single-branch $GIT_REPO_REMOTE_URL $MASTER_DIR
pushd $MASTER_DIR

boot release-gh-pages

mkdir -p $DIR
git clone -b $GIT_DEPLOY_BRANCH --single-branch $GIT_REPO_REMOTE_URL $DIR

rsync -av --exclude='/devcards/js/devcards.out/' --exclude='/devcards/js/devcards.cljs.edn' target/ $DIR

git $GIT_SUBTREE_OPTS add -A .
git $GIT_SUBTREE_OPTS commit -am "update to ${GIT_REPO_HEAD_SHA}"
git $GIT_SUBTREE_OPTS push origin $GIT_DEPLOY_BRANCH

popd

# Cleanup
rm -rf $MASTER_DIR
