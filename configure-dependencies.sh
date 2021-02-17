#!/bin/bash -e
echo "Configuring dependencies before build"
cp java/registry/src/main/resources/application.yml.sample java/registry/src/main/resources/application.yml
cp java/registry/src/main/resources/frame.json.sample java/registry/src/main/resources/frame.json

default_schema=true
schema_dir='java/registry/src/main/resources/public/_schemas'

for file in "$schema_dir"/*; do
   new_file=$(basename "$file")
   new_file=${new_file%.sample}
   cp $file "$schema_dir""/""$new_file"
done

echo "Configuration of dependencies completed"
