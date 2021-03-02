#!/bin/bash

# Delete old binaries
rm -rf out/bin 2>/dev/null
mkdir -p out/bin

# Delete old dependencies
rm -f build/libs/libs/*

# Generate new binaries
./gradlew makeJar copyDependencies $@
success=$?

if [[ "${success}" -gt 0 ]]; then
    exit 1
fi

# Copy the new binaries
cp $(ls -t build/libs/*.jar | head -1) out/bin/main.jar
chmod 770 out/bin/main.jar

# Copy the dependencies
cp -R build/libs/libs out/bin/.

# Copy the explicit dependencies
cp libs/* out/bin/libs/. 2>/dev/null

rm -rf out/www 2>/dev/null
mkdir -p out/www
cp -R www/* out/www/.

rm -rf out/data 2>/dev/null
mkdir -p out/data
cp -R data/* out/data/.

echo -e '#!/bin/bash\njava -jar bin/main.jar $@' > out/run.sh
chmod 770 out/run.sh

exit 0
