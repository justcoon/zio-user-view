package com.jc.user.view.module.api

import com.jc.logging.api.LoggingSystemGrpcApiHandler
import com.jc.logging.proto.ZioLoggingSystemApi.RCLoggingSystemApiService
import com.jc.user.domain.proto.ZioUserView.RCUserViewApiService
import com.jc.user.view.model.config.HttpApiConfig
import scalapb.zio_grpc.{Server => GrpcServer, ServerLayer => GrpcServerLayer, ServiceList => GrpcServiceList}
import io.grpc.ServerBuilder
import zio.ZLayer
import eu.timepit.refined.auto._

object GrpcApiServer {

  def create(
    config: HttpApiConfig): ZLayer[LoggingSystemGrpcApiHandler with UserViewGrpcApiHandler, Throwable, GrpcServer] = {
    GrpcServerLayer.fromServiceList(
      ServerBuilder.forPort(config.port),
      GrpcServiceList.access[RCLoggingSystemApiService[Any]].access[RCUserViewApiService[Any]])
  }
}
