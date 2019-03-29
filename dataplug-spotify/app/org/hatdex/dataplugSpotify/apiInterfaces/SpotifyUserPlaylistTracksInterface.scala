package org.hatdex.dataplugSpotify.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import org.hatdex.dataplugSpotify.models.SpotifyPlaylistTrack
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class SpotifyUserPlaylistTracksInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: SpotifyProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "playlists/tracks"
  protected val logger: Logger = Logger(this.getClass)
  val defaultApiEndpoint: ApiEndpointCall = SpotifyProfileInterface.defaultApiEndpoint
  val refreshInterval: FiniteDuration = 365.days

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val nextQueryParams = for {
      nextLink <- Try((content \ "next").asOpt[String].getOrElse("")) if nextLink.nonEmpty
      queryParams <- Try(Uri(nextLink).query().toMap) if queryParams.size == 2
    } yield queryParams

    (nextQueryParams, params.storage.get("offset")) match {
      case (Success(qp), Some(_)) =>
        logger.debug(s"Next continuation params: $qp")
        Some(params.copy(queryParameters = params.queryParameters ++ qp))

      case (Success(qp), None) =>
        val offsetParameter = (content \ "offset").as[Int].toString
        logger.debug(s"Next continuation params: $qp, setting offset to $offsetParameter")
        Some(params.copy(queryParameters = params.queryParameters ++ qp, storageParameters = Some(params.storage +
          ("offset" -> offsetParameter))))

      case (Failure(e), _) =>
        logger.debug(s"Next link NOT found. Terminating continuation. $e")
        None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.storage.get("offset").map { offSet => // After date set (there was at least one continuation step)
      Try((content \ "items").as[JsArray].value) match {
        case Success(_) => // Did continuation but there was no `nextPage` found, if track array present it's a successful completion
          val nextStorage = params.storage - "offset"
          val nextQuery = params.queryParameters - "before" + ("offset" -> offSet)
          params.copy(queryParameters = nextQuery, storageParameters = Some(nextStorage))

        case Failure(e) => // Cannot extract tracks array value, most likely an error was returned by the API, continue from where finished previously
          logger.error(s"Provider API request error while performing continuation: $content.The error is: $e")
          params
      }
    }.getOrElse { // After date not set, no continuation steps took place
      val offsetParameter = (content \ "offset").as[String]
      val nextQuery = params.queryParameters - "before" + ("offset" -> offsetParameter)
      params.copy(queryParameters = nextQuery)
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val dataValidation =
      transformData(content, fetchParameters)
        .map(validateMinDataStructure)
        .getOrElse(Failure(SourceDataProcessingException("Source data malformed, could not insert date in to the structure")))

    for {
      validatedData <- FutureTransformations.transform(dataValidation)
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(rawData: JsValue, fetchParameters: ApiEndpointCall): JsResult[JsObject] = {
    val playlistId = fetchParameters.storage.get("playlistId")
    val transformation = (__ \ "items").json.update(
      __.read[JsArray].map { o =>
        val updatedValue = o.value.map { item =>
          playlistId match {
            case Some(value) => item.as[JsObject] ++ JsObject(Map("hat_updated_time" -> JsString(DateTime.now.toString), "playlistId" -> JsString(value)))
            case None        => item.as[JsObject] ++ JsObject(Map("hat_updated_time" -> JsString(DateTime.now.toString)))
          }
        }
        JsArray(updatedValue)
      })

    rawData.transform(transformation)
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    rawData match {
      case data: JsObject if (data \ "items").as[JsArray].validate[Seq[SpotifyPlaylistTrack]].isSuccess =>
        logger.info(s"Validated JSON spotify profile object.")
        Success((data \ "items").as[JsArray])
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object SpotifyUserPlaylistTracksInterface {
  lazy val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    s"/v1/playlists/[playlistId]/tracks",
    ApiEndpointMethod.Get("Get"),
    Map("playlistId" -> "primary"),
    Map("limit" -> "50"),
    Map(),
    Some(Map()))
}