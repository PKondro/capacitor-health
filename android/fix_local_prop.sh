echo "sdk.dir=/home/pkondro/Android/Sdk" > local.properties
./gradlew compileDebugKotlin --stacktrace > build.log 2>&1
cat build.log
