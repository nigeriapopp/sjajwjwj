#!/usr/bin/env bash
# Build Bark Reader APK without Gradle (needs Android SDK platform 34 + build-tools 35).
#   python3 src/fetch_dog_data.py && python3 src/train_cross_species_weights.py
#   android/build.sh
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-34/android.jar
OUT=build
KEY=${KEYSTORE:-$HOME/.android/barkreader.jks}
rm -rf $OUT && mkdir -p $OUT/classes $OUT/assets
cp ../models/bark_model.txt $OUT/assets/

$BT/aapt2 compile --dir res -o $OUT/res.zip
$BT/aapt2 link -o $OUT/unsigned.apk -I $JAR --manifest AndroidManifest.xml \
  -A $OUT/assets $OUT/res.zip --java $OUT/gen
javac --release 8 -classpath $JAR -d $OUT/classes \
  $OUT/gen/com/nscb/barkapp/R.java src/com/nscb/barkapp/*.java
$BT/d8 --min-api 23 --lib $JAR --output $OUT $(find $OUT/classes -name '*.class')
(cd $OUT && zip -qj unsigned.apk classes.dex)
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
if [ ! -f "$KEY" ]; then
  mkdir -p "$(dirname "$KEY")"
  keytool -genkeypair -keystore "$KEY" -storepass barkreader -keypass barkreader \
    -alias bark -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Bark Reader"
fi
$BT/apksigner sign --ks "$KEY" --ks-pass pass:barkreader --out $OUT/BarkReader.apk $OUT/aligned.apk
$BT/apksigner verify $OUT/BarkReader.apk
echo "built $(pwd)/$OUT/BarkReader.apk"
