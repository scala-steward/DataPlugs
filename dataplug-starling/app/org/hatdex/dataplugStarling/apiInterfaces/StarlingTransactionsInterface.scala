package org.hatdex.dataplugStarling.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplugStarling.apiInterfaces.authProviders.StarlingProvider
import org.hatdex.dataplugStarling.models.StarlingTransaction
import org.joda.time.{ DateTime, DateTimeZone }
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class StarlingTransactionsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: StarlingProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "starling"
  val endpoint: String = "transactions"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = StarlingTransactionsInterface.defaultApiEndpoint
  val defaultApiDateFormat: DateTimeFormatter = StarlingTransactionsInterface.defaultApiDateFormat

  val refreshInterval: FiniteDuration = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    params.queryParameters.get("changesSince") match {
      case None =>
        val fromDate = "2017-01-01T00:00:00.000Z"

        logger.debug(s"Initial transactions fetch from $fromDate")
        val updatedParams = params.copy(queryParameters = params.queryParameters +
          ("changesSince" -> fromDate))

        Some(updatedParams)

      case Some(value) =>
        val transactionList = Try((content \ "feedItems").as[JsArray])
        logger.debug(s"Value is: $value")
        logger.debug(s"first item transactionTime: ${(content \ "feedItems").asOpt[Seq[StarlingTransaction]].getOrElse(Seq.empty[StarlingTransaction]).head.transactionTime}")

        if (transactionList.isSuccess && transactionList.get.value.nonEmpty) {

          val firstItem = (content \ "feedItems").asOpt[Seq[StarlingTransaction]].getOrElse(Seq.empty[StarlingTransaction])
          if (firstItem.isEmpty || (value == firstItem.head.transactionTime)) {
            logger.debug(s"No more data available - stopping continuation")
            None
          }
          else {
            logger.debug(s"Continuing transactions fetching from ${firstItem.head.transactionTime}")
            val updatedParams = params.copy(queryParameters = params.queryParameters + ("changesSince" -> firstItem.head.transactionTime))

            Some(updatedParams)
          }
        }
        else {
          logger.debug(s"No more data available - stopping continuation")
          None
        }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    val fromDate = DateTime.now(DateTimeZone.forID("Europe/London"))

    params.copy(queryParameters = params.queryParameters +
      ("changesSince" -> fromDate.toString(defaultApiDateFormat)))
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "feedItems").toOption.map {
      case data: JsArray if data.validate[List[StarlingTransaction]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.validate[List[StarlingTransaction]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error obtaining 'items' list: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object StarlingTransactionsInterface {
  val defaultApiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api-sandbox.starlingbank.com",
    s"/api/v2/feed/account/[accountUid]/category/[categoryUid]",
    ApiEndpointMethod.Get("Get"),
    Map("accountUid" -> "primary", "categoryUid" -> "primary"),
    Map(),
    Map(),
    Some(Map()))
}
