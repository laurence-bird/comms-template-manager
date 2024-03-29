addSbtPlugin("com.typesafe.play" % "sbt-plugin"         % "2.6.7")
addSbtPlugin("com.tapad"         % "sbt-docker-compose" % "1.0.34")
resolvers += Resolver.bintrayIvyRepo("ovotech", "sbt-plugins")
addSbtPlugin("com.ovoenergy" % "sbt-comms-packaging" % "0.0.14")
addSbtPlugin("com.localytics" % "sbt-dynamodb" % "1.5.3")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.6")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.5.6")
