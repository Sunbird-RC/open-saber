#!/bin/bash -e
echo "Configuring dependencies before build"
cp java/registry/src/main/resources/application.yml.sample java/registry/src/main/resources/application.yml
cp java/registry/src/main/resources/frame.json.sample java/registry/src/main/resources/frame.json

if "$1" == "true":
then
 default_schema=true
else
 default_schema=false
fi
schema_dir='java/registry/src/main/resources/public/_schemas'

if $defaul_schema
then
  for file in "$schema_dir"/*; do
    old_file="$(basename "$file")"
    
    new_file="${old_file%.sample}"

    if [ "$new_file" != "$old_file" ]
    then
      cp $file "$schema_dir""/""$new_file"
    fi
  done
fi

echo "Configuration of dependencies completed"
