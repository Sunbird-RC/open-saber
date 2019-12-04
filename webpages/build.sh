ng build --prod
cd dist
mkdir registry
mv *.* registry/
cd registry
mv index.html ../../dist/
cd ../../
cp -R apidoc dist/registry/
cd dist
zip -r dist.zip .
echo "Moved files successfully"