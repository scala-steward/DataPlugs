/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.models

import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointTableStructure
import play.api.libs.json._

case class TwitterUser(
    created_at: String,
    default_profile: Boolean,
    default_profile_image: Boolean,
    description: Option[String],
    favourites_count: Int,
    followers_count: Int,
    friends_count: Int,
    geo_enabled: Boolean,
    id: Long,
    lang: String,
    listed_count: Int,
    location: Option[String],
    name: Option[String],
    profile_background_image_url_https: String,
    profile_banner_url: String,
    profile_image_url_https: String,
    `protected`: Boolean,
    screen_name: String,
    statuses_count: Int,
    time_zone: Option[String],
    url: Option[String],
    verified: Boolean
) extends ApiEndpointTableStructure {
  val dummyEntity = TwitterUser.dummyEntity

  implicit val twitterUserFormat = TwitterUser.twitterUserFormat

  def toJson: JsValue = Json.toJson(this)
}

object TwitterUser extends ApiEndpointTableStructure {
  val dummyEntity = TwitterUser(
    "created time",
    true,
    true,
    Some("profile description"),
    23,
    15,
    9,
    false,
    12345.toLong,
    "en",
    23,
    Some("San Francisco, CA"),
    Some("username"),
    "background image",
    "profile banner",
    "profile image",
    false,
    "screen name",
    437,
    Some("timezone"),
    Some("profile url"),
    false
  )

  implicit val twitterUserFormat = Json.format[TwitterUser]

  def toJson: JsValue = Json.toJson(dummyEntity)
}