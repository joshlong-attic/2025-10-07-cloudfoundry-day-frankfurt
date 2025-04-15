#!/usr/bin/env bash
function oops(){
  echo "no $1 found" && exit 1
}
ls -la pom.xml || oops "pom.xml"
./mvnw -DskipTests clean package
ls -al target || oops "target"
cf push adoptions -p target/adoptions-0.0.1-SNAPSHOT.jar
cf logs adoptions