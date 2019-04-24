name := "jishop"
enablePlugins(PlayScala, DebianPlugin, JDebPackaging, SystemdPlugin)

version := "1.0"

maintainer in Linux := "Louis Vialar <louis.vialar@japan-impact.ch>"

packageSummary in Linux := "Scala Backend for Japan Impact Shop"
packageDescription := "Scala Backend for Japan Impact Shop"
debianPackageDependencies := Seq("java8-runtime-headless")


lazy val `jishop` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += Resolver.jcenterRepo

scalaVersion := "2.12.2"

libraryDependencies ++= Seq( ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "4.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.0",
  "mysql" % "mysql-connector-java" % "5.1.34",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.pauldijou" %% "jwt-play" % "2.1.0",
  "com.hhandoko" %% "play27-scala-pdf" % "4.1.0",
  "net.codingwell" %% "scala-guice" % "4.1.0"

)

libraryDependencies += "com.typesafe.play" %% "play-mailer" % "6.0.1"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "6.0.1"
libraryDependencies += "net.sf.barcode4j" % "barcode4j" % "2.1"
libraryDependencies += "ch.japanimpact" %% "jiauthframework" % "0.1-SNAPSHOT"


unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

      