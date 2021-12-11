package com.jc.user.view.module

import zio.Has
import org.apache.kafka.streams.{KafkaStreams}

package object kafka {
  type KafkaStreamsApp = Has[KafkaStreamsApp.Service]
}
