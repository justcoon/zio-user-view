package com.jc.user.view.module.api

import org.http4s.{HttpRoutes, Response, Status}
import org.http4s.dsl.Http4sDsl

import zio.RIO
import zio.clock.Clock

object HealthCheckApi {

  def httpRoutes[R <: Clock](isReady: () => RIO[R, Boolean]): HttpRoutes[RIO[R, *]] = {
    import zio.interop.catz._
    val dsl = Http4sDsl[RIO[R, *]]
    import dsl._
    HttpRoutes.of[RIO[R, *]] {
      case GET -> Root / "ready" =>
        isReady().map { res =>
          if (res) {
            Response(Status.Ok).withEntity("OK")
          } else {
            Response(Status.ServiceUnavailable)
          }
        }
      case GET -> Root / "alive" =>
        Ok("OK")
    }
  }
}
