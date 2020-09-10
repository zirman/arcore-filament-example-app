#!/bin/sh
## matc should either built from source or downloaded for your platform from here:
## https://github.com/google/filament
for path in ./app/src/main/materials/* ; do
  fullname=$(basename "$path")
  filename="${fullname%.*}"
  echo "$filename"
  ./matc -p mobile -a opengl -o "./app/src/main/assets/materials/$filename.filamat" "$path"
done
