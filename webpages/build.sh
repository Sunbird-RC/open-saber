ng build --prod
cd dist
mkdir registry
mv *.* registry/
cd registry
mv index.html ../../dist/
cd ../../
cp -R apidoc dist/registry/
echo "Moved files successfully"