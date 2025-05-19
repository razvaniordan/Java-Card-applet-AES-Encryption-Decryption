#!/bin/bash
#
# Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.

sample_name=${PWD##*/}

echo "Sample name: [${sample_name}]"
echo

cd ..
./build.sh ${sample_name}
cd ~-

