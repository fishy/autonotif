scalaVersion := "2.11.8"

enablePlugins(AndroidApp)
android.useSupportVectors

versionCode := Some(11)
version := "0.2.1-beta"

instrumentTestRunner :=
  "android.support.test.runner.AndroidJUnitRunner"

platformTarget := "android-26"

minSdkVersion := "23"

javacOptions in Compile ++= "-source" :: "1.7" :: "-target" :: "1.7" :: Nil

libraryDependencies ++=
  "com.android.support" % "appcompat-v7" % "25.3.1" ::
  "com.android.support" % "cardview-v7" % "25.3.1" ::
  "com.android.support" % "recyclerview-v7" % "25.3.1" ::
  "com.android.support" % "support-v4" % "25.3.1" ::
  "com.android.support.test" % "runner" % "0.5" % "androidTest" ::
  "com.android.support.test.espresso" % "espresso-core" % "2.2.2" % "androidTest" ::
  Nil
