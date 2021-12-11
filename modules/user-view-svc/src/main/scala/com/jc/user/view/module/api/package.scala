package com.jc.user.view.module

import com.jc.user.domain.proto.ZioUserView.RCUserViewApiService
import zio.Has

package object api {

  type UserViewGrpcApiHandler = Has[RCUserViewApiService[Any]]
}
