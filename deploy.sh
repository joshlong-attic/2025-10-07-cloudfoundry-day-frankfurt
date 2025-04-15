#!/usr/bin/env bash
./mvnw -DskipTests clean package
cf push adoptions -p target/adoptions-0.0.1-SNAPSHOT.jar
cf logs adoptions