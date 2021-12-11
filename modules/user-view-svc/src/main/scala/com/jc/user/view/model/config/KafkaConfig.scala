package com.jc.user.view.model.config

import pureconfig.generic.semiauto.deriveReader

final case class KafkaConfig(
  addresses: Addresses,
  userTopic: TopicName,
  departmentTopic: TopicName,
  userViewTopic: TopicName)

object KafkaConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[KafkaConfig]
}
