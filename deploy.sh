#!/usr/bin/env bash
ls -la pom.xml || echo "no pom.xml found" && exit 1
./mvnw -DskipTests clean package
ls -al target || echo "no target directory found" && exit 1
cf push adoptions -p target/adoptions-0.0.1-SNAPSHOT.jar
cf logs adoptions