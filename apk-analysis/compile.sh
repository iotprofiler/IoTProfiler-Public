#!/bin/sh
set -e

SOOTCLASS="$PWD/soot/gson-2.8.5.jar:$PWD/soot/fuzzywuzzy-1.2.0.jar:$PWD/soot/soot-infoflow-cmd-jar-with-dependencies.jar:$PWD/soot/trove-3.0.3.jar:$PWD/soot/commons-lang3-3.10.jar:$PWD/soot/opencsv-5.1.jar"

cd src
echo "Compiling..."
javac -g -cp $SOOTCLASS:. analysisutils/*.java
javac -g -cp $SOOTCLASS:. dpcollector/*.java
javac -g -cp $SOOTCLASS:. dpcollector/manager/*.java
javac -g -cp $SOOTCLASS:. dpcollector/transformer/*.java
cd ..
echo "Installing..."
mkdir -p bin/analysisutils
mkdir -p bin/dpcollector/manager
mkdir -p bin/dpcollector/transformer

mv src/analysisutils/*.class bin/analysisutils/
mv src/dpcollector/*.class bin/dpcollector/
mv src/dpcollector/manager/*.class bin/dpcollector/manager/
mv src/dpcollector/transformer/*.class bin/dpcollector/transformer/