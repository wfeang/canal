#!/bin/bash


string=$1
requstbase="http://localhost:8087/etl/rdb/gp-ngp/rdb/"

array=(${string//,/ })
for  name  in  $array;  do
echo  ${requstbase}${name}".xml"
done
