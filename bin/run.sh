#! /bin/bash
path=$(cd `dirname $0`; pwd)  #此处是获取当前目录
#echo "we are now at:  $path"

files=$(ls $path | grep *.jar)
for file in $files
do
 java -jar $file
 break #跳出循环
done