#!/usr/bin/env bash
# Печатает версию приложения из app/build.gradle (def appVersion = "X.Y.Z").
sed -nE 's/^def appVersion = "([0-9]+\.[0-9]+\.[0-9]+)".*/\1/p' "$(dirname "$0")/../app/build.gradle"
