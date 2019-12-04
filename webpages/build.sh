ng build --prod
cd dist
mkdir registry
mv *.* registry/
cd registry
mv index.html ../../dist/
echo "Moved files successfully"