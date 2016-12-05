package org.hatdex.dataplug.services

import java.sql.Connection
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.event.Logging
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{ Contexts, LabelExpression, Liquibase }
import org.hatdex.dataplug.actors.IoExecutionContext
import play.api.Logger
import play.api.db.{ Database, _ }

import scala.collection.JavaConverters._
import scala.concurrent.{ Future, blocking }
import scala.util.Try

/**
 * Runs Liquibase based database schema and data migrations. This is the only place for all related
 * modules to run updates from.
 *
 * Liquibase finds its files on the classpath and applies them to DB. If migration fails
 * this class will throw an exception and by default your application should not continue to run.
 *
 * It does not matter which module runs this migration first.
 */
trait SchemaMigration {
  val logger: Logger

  /**
   * Invoke this method to apply all DB migrations.
   */
  def run(changeLogFiles: Seq[String]): Future[Unit]

}

