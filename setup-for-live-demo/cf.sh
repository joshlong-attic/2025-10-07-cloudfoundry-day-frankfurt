#!/usr/bin/env bash
rm -rf target
./mvnw -DskipTests package
cf push -p target/*jar