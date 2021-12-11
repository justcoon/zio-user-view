package com.jc.user.view.module.api

import com.jc.user.view.model.config.HttpApiConfig
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import org.http4s.server.{Router, Server}
import org.http4s.implicits._
import zio.interop.catz._
import zio.{Has, RIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import eu.timepit.refined.auto._

object HttpApiServer {

  type ServerEnv = Clock with Blocking with Logging

  private def httpRoutes(): HttpRoutes[RIO[ServerEnv, *]] =
    Router[RIO[ServerEnv, *]](
      "/" -> HealthCheckApi.httpRoutes
    )

  private def httpApp(): HttpApp[RIO[ServerEnv, *]] =
    HttpServerLogger.httpApp[RIO[ServerEnv, *]](true, true)(httpRoutes().orNotFound)

  def create(config: HttpApiConfig): ZLayer[ServerEnv, Throwable, Has[Server]] = {
    ZLayer.fromManaged(
      BlazeServerBuilder[RIO[ServerEnv, *]]
        .bindHttp(config.port, config.address)
        .withHttpApp(httpApp())
        .resource
        .toManagedZIO
    )
  }

}