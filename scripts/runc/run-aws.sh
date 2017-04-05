#!/bin/bash
TERM=xterm-color
VERSION=0.3.0
args=$@

#Cluster credentials, normally user@host, if you have SSH aliases they can be used.
CLUSTER_SSH="prometheus-master"

#The remote working directory for the application
CLUSTER_WORK_PATH="~/projects/prometheus-relation-model"

#The jar to execute
JAR_NAME="prometheus-relation-model-assembly-"$VERSION".jar"

#Userdefined args
JAR_USER_ARGS=""

#Only activate this if you know what you are doing!
SPARK_USER_CLASSPATH_FIRST="false"

#Controls the max resultsize for a collect()
SPARK_MAX_RESULTSIZE="8192m"

function test {
	"$@"
	local status=$?
	if [ $status -ne 0 ]; then
		echo "error with $1" >&2
		echo "Status code: $status"
		exit $status
	fi

	return $status
}

# Build jar
echo " == Building fat jar =="
test sbt -Dmode=cluster assembly

echo " == Synchronizing dependencies and executables =="
#test rsync -av --delete -e ssh --progress target/lib/ $CLUSTER_SSH:$CLUSTER_WORK_PATH/lib/
test scp target/scala-2.10/$JAR_NAME $CLUSTER_SSH:$CLUSTER_WORK_PATH/$JAR_NAME

#Runs python inline to construct the classpath list.
#LIBS=$(python -c "import os; print(','.join(map(lambda fname: 'lib/' + fname, os.listdir('target/lib'))))")

#This is not the fastest GC, but works well under heavy GC load.
JVMOPTS="-XX:+AggressiveOpts -XX:+PrintFlagsFinal -XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions -XX:+G1SummarizeConcMark -XX:InitiatingHeapOccupancyPercent=35"

echo " == Running command on cluster == "
test ssh $CLUSTER_SSH 'bash -s' << EOF
  mkdir -p $CLUSTER_WORK_PATH
	cd $CLUSTER_WORK_PATH
	spark-submit --conf spark.driver.maxResultSize=$SPARK_MAX_RESULTSIZE --conf spark.executor.extraJavaOptions="$JVMOPTS" $JAR_NAME $JAR_USER_ARGS $args
EOF
echo " == Done. == "
