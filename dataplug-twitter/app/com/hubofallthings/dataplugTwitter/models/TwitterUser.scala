/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugTwitter.models

import play.api.libs.json._

case class TwitterUser(
    created_at: String,
    default_profile: Boolean,
    default_profile_image: Boolean,
    description: Option[String],
    favourites_count: Int,
    followers_count: Int,
    friends_count: Int,
    geo_enabled: Option[Boolean],
    id: Long,
    lang: Option[String],
    listed_count: Int,
    location: Option[String],
    name: Option[String],
    profile_background_image_url_https: Option[String],
    profile_banner_url: Option[String],
    profile_image_url_https: String,
    `protected`: Boolean,
    screen_name: String,
    statuses_count: Int,
    time_zone: Option[String],
    url: Option[String],
    verified: Boolean)

object TwitterUser {
  implicit val twitterUserFormat = Json.format[TwitterUser]
}
