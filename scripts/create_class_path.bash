#!/bin/bash

createClassPath() {
    basedir=$1
    jar=$2

    classpath=$basedir/$jar

    if [ -d $basedir/external ]
    then
	for i in $basedir/external/*.jar
	do
	    classpath+=":$i"
	done
    fi
    echo $classpath
}
