package com.jc.user.view.model.config

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import pureconfig.generic.semiauto.deriveReader

final case class KafkaConfig(
  addresses: Addresses,
  userTopic: TopicName,
  departmentTopic: TopicName,
  userViewTopic: TopicName,
  applicationId: String Refined NonEmpty,
  stateDir: Option[String Refined NonEmpty] = None)

object KafkaConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[KafkaConfig]
}
