package com.jc.user.view.module.api

import com.jc.auth.JwtAuthenticator
import com.jc.auth.api.GrpcJwtAuth
import com.jc.user.domain.proto.{GetUserViewReq, GetUserViewRes}
import com.jc.user.domain.proto.ZioUserView.RCUserViewApiService
import com.jc.user.view.module.kafka.KafkaStreamsApp
import io.grpc.Status
import scalapb.zio_grpc.RequestContext
import zio.{Has, ZIO, ZLayer}

object UserViewGrpcApiHandler {

  def toStatus(e: Throwable): Status = {
    Status.INTERNAL.withDescription(e.getMessage)
  }

  final case class LiveUserViewApiService(
    kafkaStreamsApp: KafkaStreamsApp.Service,
    jwtAuthenticator: JwtAuthenticator.Service)
      extends RCUserViewApiService[Any] {

    override def getUserView(request: GetUserViewReq): ZIO[Any with Has[RequestContext], Status, GetUserViewRes] = {
      for {
        _ <- GrpcJwtAuth.authenticated(jwtAuthenticator)
        res <- kafkaStreamsApp.getUserView(request.id).mapError(toStatus)
      } yield GetUserViewRes(res)
    }
  }

  val live: ZLayer[KafkaStreamsApp with JwtAuthenticator, Nothing, UserViewGrpcApiHandler] =
    ZLayer.fromServices[KafkaStreamsApp.Service, JwtAuthenticator.Service, RCUserViewApiService[Any]] {
      (kafkaStreamsApp, jwtAuth) =>
        LiveUserViewApiService(kafkaStreamsApp, jwtAuth)
    }
}
