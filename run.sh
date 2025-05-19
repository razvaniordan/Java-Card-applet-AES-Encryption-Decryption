#!/bin/bash
#
# Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.

sample_name=${PWD##*/}
samples_dir=$(dirname ${PWD})

echo "Sample name: [${sample_name}]"
echo

client_name=ism.ase.ro.AMSProjectAESClient

cap=${samples_dir}/${sample_name}/applet/deliverables/ProjectAES/ism/ase/ro/javacard/ro.cap
props=${JC_HOME_SIMULATOR}/samples/client.config.properties

client_args="-cap=${cap} -props=${props}"

cd ..
./run.sh ${sample_name} ${client_name} "${client_args}"
cd ~-

