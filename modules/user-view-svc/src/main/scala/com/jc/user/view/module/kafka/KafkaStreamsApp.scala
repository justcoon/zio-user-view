package com.jc.user.view.module.kafka

import com.jc.user.domain.DepartmentEntity.{DepartmentId, DepartmentIdTag}
import com.jc.user.domain.UserEntity.{UserId, UserIdTag}
import com.jc.user.domain.proto.{
  Department,
  DepartmentEntityState,
  DepartmentPayloadEvent,
  User,
  UserEntityState,
  UserPayloadEvent,
  UserView,
  UserViewEnvelope,
  UserViewEvent
}
import com.jc.user.view.model.config.KafkaConfig
import eu.timepit.refined.auto._
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.{KafkaStreams, StoreQueryParameters, StreamsConfig}
import org.apache.kafka.streams.kstream.ValueJoiner
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{KTable, Materialized}
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.state.{KeyValueStore, QueryableStoreTypes, ReadOnlyKeyValueStore}
import shapeless.tag
import shapeless.tag.@@
import zio.logging.{Logger, Logging}
import zio.{Task, ZIO, ZLayer, ZManaged}

import java.time.Instant
import java.util.Properties
import scala.util.Try

object KafkaStreamsApp {

  private def taggedStringSerde[T]: Serde[String @@ T] = {
    val ss = Serdes.stringSerde.serializer()
    val serializer = (t: String, v: String @@ T) => ss.serialize(t, v)
    val sd = Serdes.stringSerde.deserializer()
    val deserializer = (t: String, b: Array[Byte]) => Option(sd.deserialize(t, b)).map(tag[T][String])
    Serdes.fromFn[String @@ T](serializer, deserializer)
  }

  private implicit val userIdSerde: Serde[UserId] = taggedStringSerde[UserIdTag]

  private implicit val userEventSerde: Serde[UserPayloadEvent] = {
    val serializer = (v: UserPayloadEvent) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(UserPayloadEvent.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  private implicit val userSerde: Serde[User] = {
    val serializer = (v: User) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(User.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  private implicit val userStateSerde: Serde[UserEntityState] = {
    val serializer = (v: UserEntityState) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(UserEntityState.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  private implicit val departmentStateSerde: Serde[DepartmentEntityState] = {
    val serializer = (v: DepartmentEntityState) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(DepartmentEntityState.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  private implicit val departmentIdSerde: Serde[DepartmentId] = taggedStringSerde[DepartmentIdTag]

  private implicit val departmentEventSerde: Serde[DepartmentPayloadEvent] = {
    val serializer = (v: DepartmentPayloadEvent) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(DepartmentPayloadEvent.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  private implicit val userViewEnvelopeSerde: Serde[UserViewEnvelope] = {
    val serializer = (v: UserViewEnvelope) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(UserViewEnvelope.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  private implicit val userViewEventSerde: Serde[UserViewEvent] = {
    val serializer = (v: UserViewEvent) => v.toByteArray
    val deserializer = (b: Array[Byte]) => Some(UserViewEvent.parseFrom(b))
    Serdes.fromFn(serializer, deserializer)
  }

  def createUser(id: UserId): User = User(id, "", "", "")

  def createDepartment(id: DepartmentId): Department = Department(id, "", "")

  def aggregateUser(id: UserId, event: UserPayloadEvent, state: UserEntityState): UserEntityState = {
    val newEntity = state.entity.flatMap { user =>
      event match {
        case UserPayloadEvent(entityId, _, payload: UserPayloadEvent.Payload.Created, _) =>
          Some(
            User(
              entityId,
              payload.value.username,
              payload.value.email,
              payload.value.pass,
              payload.value.address,
              payload.value.department))
        case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.EmailUpdated, _) =>
          val newUser = user.withEmail(payload.value.email)
          Some(newUser)
        case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.PasswordUpdated, _) =>
          val newUser = user.withPass(payload.value.pass)
          Some(newUser)
        case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.AddressUpdated, _) =>
          val newUser = payload.value.address match {
            case Some(a) => user.withAddress(a)
            case None => user.clearAddress
          }
          Some(newUser)
        case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.DepartmentUpdated, _) =>
          val newUser = payload.value.department match {
            case Some(d) => user.withDepartment(d)
            case None => user.clearDepartment
          }
          Some(newUser)
        case UserPayloadEvent(_, _, _: UserPayloadEvent.Payload.Removed, _) =>
          None
        case _ =>
          Some(user)
      }
    }.orElse {
      event match {
        case UserPayloadEvent(entityId, _, payload: UserPayloadEvent.Payload.Created, _) =>
          Some(
            User(
              entityId,
              payload.value.username,
              payload.value.email,
              payload.value.pass,
              payload.value.address,
              payload.value.department))
        case _ => None
      }
    }
    UserEntityState(newEntity)
  }

  def aggregateDepartment(
    id: DepartmentId,
    event: DepartmentPayloadEvent,
    state: DepartmentEntityState): DepartmentEntityState = {
    val newEntity = state.entity.flatMap { department =>
      event match {
        case DepartmentPayloadEvent(entityId, _, payload: DepartmentPayloadEvent.Payload.Created, _) =>
          Some(Department(entityId, payload.value.name, payload.value.description))
        case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Updated, _) =>
          val newDepartment = department.withName(payload.value.name).withDescription(payload.value.description)
          Some(newDepartment)
        case DepartmentPayloadEvent(_, _, _: DepartmentPayloadEvent.Payload.Removed, _) =>
          None
        case _ =>
          Some(department)
      }
    }.orElse {
      event match {
        case DepartmentPayloadEvent(entityId, _, payload: DepartmentPayloadEvent.Payload.Created, _) =>
          Some(Department(entityId, payload.value.name, payload.value.description))
        case _ =>
          None
      }
    }
    DepartmentEntityState(newEntity)
  }

  def getUserViewStoreName(config: KafkaConfig) = {
    config.userViewTopic + "-store"
  }

  // https://www.confluent.io/blog/data-enrichment-with-kafka-streams-foreign-key-joins/
  // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Join+Semantics#KafkaStreamsJoinSemantics-KTable-KTableForeign-KeyJoin(v2.4.xandnewer)
  def streamTopology(config: KafkaConfig) = {
    val builder = new StreamsBuilder()

    val usersTable = builder
      .stream[UserId, UserPayloadEvent](config.userTopic)
      .groupByKey
      .aggregate[UserEntityState](UserEntityState())(aggregateUser)

    val departmentsTable = builder
      .stream[DepartmentId, DepartmentPayloadEvent](config.departmentTopic)
      .groupByKey
      .aggregate(DepartmentEntityState())(aggregateDepartment)

    // FIXME kafka FK left join issue https://issues.apache.org/jira/browse/KAFKA-12317
    // if FK is null, UserView record is not created
    val getUserDepartmentId: UserEntityState => DepartmentId = state => {
      val depId = state.entity.flatMap(_.department).map(_.id)

      depId.orNull // null value - java api
    }

    val userViewJoiner: ValueJoiner[UserEntityState, DepartmentEntityState, UserViewEnvelope] =
      (userState, departmentState) => {
        // null value - java api
        val department = Option(departmentState).flatMap(_.entity)
        val view = userState.entity.map { user =>
          import io.scalaland.chimney.dsl._
          user.into[UserView].withFieldConst(_.department, department).transform
        }
        UserViewEnvelope(view)
      }

    val userViewMaterialized =
      Materialized.as[UserId, UserViewEnvelope, KeyValueStore[Bytes, Array[Byte]]](getUserViewStoreName(config))

    val usersDepartmentTable =
      usersTable.leftJoin(departmentsTable, getUserDepartmentId, userViewJoiner, userViewMaterialized)

    usersDepartmentTable.toStream.map { (id, view) =>
      id -> UserViewEvent(id, Instant.now(), view.entity)
    }.to(config.userViewTopic)

    builder.build()
  }

  def streamConfig(config: KafkaConfig): StreamsConfig = {
    val props = new Properties
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-view-app")
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.addresses.mkString(","))
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.stringSerde.getClass)
    new StreamsConfig(props)
  }

  trait Service {
    def getUserView(id: UserId): Task[Option[UserView]]
  }

  final case class LiveKafkaStreamsAppService(config: KafkaConfig, kafkaStreams: KafkaStreams) extends Service {

    private val storeQuery =
      StoreQueryParameters.fromNameAndType(
        getUserViewStoreName(config),
        QueryableStoreTypes.keyValueStore[UserId, UserViewEnvelope]())

    private def getUserViewStore(): Task[ReadOnlyKeyValueStore[UserId, UserViewEnvelope]] = {
      ZIO.fromTry(Try {
        kafkaStreams.store(storeQuery)
      })
    }

    override def getUserView(id: UserId): Task[Option[UserView]] = {
      for {
        store <- getUserViewStore()
        res <- ZIO.succeed(Option(store.get(id)))
      } yield res.flatMap(_.entity)
    }
  }

  def live(config: KafkaConfig): ZLayer[Logging, Throwable, KafkaStreamsApp] = {
    ZLayer.fromServiceManaged[Logger[String], Any, Throwable, KafkaStreamsApp.Service] { logger =>
      ZManaged.make {
        for {
          _ <- logger.info("kafka app starting ...")
          topology <- ZIO.succeed(streamTopology(config))
          cfg <- ZIO.succeed(streamConfig(config))
          kafkaStreams <- ZIO.succeed(new KafkaStreams(topology, cfg))
          _ <- logger.info("kafka app created, topology: " + topology.describe())
          //          _ <- ZIO.fromTry(Try(kafkaStreams.cleanUp()))
          _ <- ZIO.fromTry(Try(kafkaStreams.start()))
          _ <- logger.info("kafka app started")
        } yield {
          LiveKafkaStreamsAppService(config, kafkaStreams)
        }
      }(app => ZIO.succeed(app.kafkaStreams.close()))
    }
  }

}
