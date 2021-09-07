#!/bin/bash -e
echo "Configuring dependencies before build"
cp java/registry/src/main/resources/application.yml.sample java/registry/src/main/resources/application.yml
cp java/registry/src/main/resources/frame.json.sample java/registry/src/main/resources/frame.json

if [ $# -gt 0 ] && "$1" == "true"; then
 default_schema=true
else
 default_schema=false
fi

echo "Using default schema: $default_schema"

schema_dir='java/registry/src/main/resources/public/_schemas'
schema_sample_dir='java/registry/src/main/resources/public/_schemas_sample'

if [ "$default_schema" = true ]; then
  mkdir -p "$schema_dir"
  for file in "$schema_sample_dir"/*; do
    old_file="$(basename "$file")"
    
    new_file="${old_file%.sample}"

    if [ "$new_file" != "$old_file" ]
    then
      cp $file "$schema_dir""/""$new_file"
    fi
  done
fi

echo "Configuration of dependencies completed"
