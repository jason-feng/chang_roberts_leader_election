#!/bin/sh

if [ $# -eq 0 ]
    then
        echo "No arguments supplied"
        exit 1
fi

javac Node.java
javac Network.java

java Network $1
