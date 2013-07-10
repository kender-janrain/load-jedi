name := "load-jedi"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % "2.2.0-RC1",
	"io.spray" % "spray-client" % "1.2-M8",
	"io.spray" % "spray-httpx" % "1.2-M8"
)
