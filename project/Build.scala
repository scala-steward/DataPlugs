/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

import sbt.Keys._
import sbt._

object Build extends Build {

  lazy val dataplug = Project(
    id = "dataplug",
    base = file("dataplug")
  )

  lazy val dataplugTwitter = Project(
    id = "dataplug-twitter",
    base = file("dataplug-twitter"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test"),
    aggregate = Seq(dataplug)
  )

  lazy val dataplugFitbit = Project(
    id = "dataplug-fitbit",
    base = file("dataplug-fitbit"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test"
    ),
    aggregate = Seq(dataplug)
  )

  val root = Project(
    id = "dataplug-project",
    base = file("."),
    aggregate = Seq(
      dataplug,
      dataplugTwitter,
      dataplugFitbit
    ),
    settings = Defaults.coreDefaultSettings ++
      // APIDoc.settings ++
      Seq(
        publishLocal := {},
        publishM2 := {},
        publishArtifact := false
      )
  )
}