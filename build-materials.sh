#!/bin/sh

for path in ./app/src/main/materials/* ; do
  fullname=$(basename "$path")
  filename="${fullname%.*}"
  echo "$filename"
  ./matc -p mobile -a opengl -o "./app/src/main/assets/materials/$filename.filamat" "$path"
done
