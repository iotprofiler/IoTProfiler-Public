#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Please provide apk directory and folder id."
    exit 1
fi

INPUT_DIR=$1
FOLDER_ID=$2
APKEXT=.apk
NUM_INTERVALS=180
SLEEP_INTERVAL=10
TIMEOUT=$(( $NUM_INTERVALS*$SLEEP_INTERVAL ))
SOOTCLASS="$PWD/soot/gson-2.8.5.jar:$PWD/soot/fuzzywuzzy-1.2.0.jar:$PWD/soot/soot-infoflow-cmd-jar-with-dependencies.jar:$PWD/soot/trove-3.0.3.jar:$PWD/soot/commons-lang3-3.10.jar:$PWD/soot/opencsv-5.1.jar"
BIN_DIR="$PWD/bin"
OUT_DIR="$PWD/output/"
JIMPLE_OUT_DIR="$PWD/jimple_output/"
ROOT_DIR="$PWD"
TEMP_PID="$PWD/tempPid_$FOLDER_ID"

start_soot_for_app() {
    #APK_FILE_PATH=`readlink -f -- $INPUT_DIR/$file$APKEXT`
    APK_FILE_PATH=$(realpath $INPUT_DIR/$file$APKEXT)
    rm -rf $TEMP_PID
    cd $BIN_DIR
    pyPath=$(which python3)
    java -Xmx4g -cp $SOOTCLASS:. -DpyPath=$pyPath dpcollector.Main $APK_FILE_PATH $TIMEOUT 2>&1 >> $output_file &
    pid=$!
    echo $pid > $TEMP_PID
}

run_app() {
    start_soot_for_app

    cd $ROOT_DIR
    read javaPid < $TEMP_PID

    COUNTER=0
    # add 50 seconds of grace period for java to terminate
    COUNTER_END=$((NUM_INTERVALS + 5))
    while [  $COUNTER -lt $COUNTER_END ];
    do
        let COUNTER=COUNTER+1

        sleep $SLEEP_INTERVAL
        result=$(ps -ef | grep $javaPid | grep java)

        if [[ -n "$result" ]] ;
        then
            printf "."
        else
            echo "### Quit"
            echo $COUNTER
            break
        fi
    done
    
    result=$(ps -ef | grep $javaPid | grep java)
    if [[ -n "$result" ]]
    then
        echo "###killing $javaPid"
        kill -9 $javaPid
        #pkill -9 -f mySoot.AnalyzerMain
        echo "=======================================================" >> $output_file
        echo "#### Timeout. Terminating the process" >> $output_file
        echo "=======================================================" $output_file
    fi
}

echo "Begin analyzing all the APK files in $INPUT_DIR"

if [ ! -d "$OUT_DIR" ]; then
    mkdir $OUT_DIR
fi

if [ ! -d "$JIMPLE_OUT_DIR" ]; then
    mkdir $JIMPLE_OUT_DIR
fi

for file in $INPUT_DIR/*$APKEXT
do
    echo "analyzing $file"
    file=$(basename $file $APKEXT)

    output_file="$OUT_DIR/$file.output"
    if [ -f "$output_file" ]
    then
        echo "$output_file already exists, skip this app..."
        continue
    fi

    log_file="$OUT_DIR/$file.log"
    if [ -f "$log_file" ]
    then
        echo "$log_file already exists, skip this app..."
        continue
    fi

    STARTTIME=$(date +%s)
    run_app
    ENDTIME=$(date +%s)

    echo ""
    echo "It takes $(($ENDTIME - $STARTTIME)) seconds to complete $file"
    echo ""

    rm -rf $JIMPLE_OUT_DIR/$file*
done