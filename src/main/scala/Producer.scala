package com.commercetools.fs2QueueDemo

import cats.effect.*
import fs2.{Pipe, Stream, text}

import scala.util.Random
import com.commercetools.queue.QueueClient
import cats.implicits.*
import com.commercetools.fs2QueueDemo.Configuration

import scala.concurrent.duration.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.effect.unsafe.implicits.global

import java.time.LocalDateTime

object Producer extends IOApp {

  case class OrderPayload(id: String, createdAt: LocalDateTime)

  object OrderPayload {
    implicit val encoder: Encoder[OrderPayload] = deriveEncoder[OrderPayload]
    implicit val decoder: Decoder[OrderPayload] = deriveDecoder[OrderPayload]
  }

  given unsafeLogger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def produce[F[_]: Temporal: Async](
      queue: QueueClient[F]
  )(using logger: Logger[F]): Stream[F, Unit] = {
    def createOrder: (String, Map[String, String]) = (
      OrderPayload(
        s"Order-${Random.nextInt(1000)}",
        LocalDateTime.now()
      ).asJson.noSpaces,
      Map.empty[String, String]
    )

    val publisher = queue.publish(Configuration.queueName)

    Stream.fixedRate[F](3.seconds).evalMap { _ =>
      val order = createOrder
      logger.info(s"Preparing to publish: $order") >>
        Stream
          .emit(order)
          .through(publisher.sink(batchSize = 1))
          .compile
          .drain >>
        logger.info("Order published successfully")
    }
  }

  private def createQueue(
      client: QueueClient[IO]
  )(using logger: Logger[IO]): IO[Unit] =
    client.administration
      .exists(Configuration.queueName)
      .ifM(
        IO.unit,
        client.administration
          .create(Configuration.queueName, 10.minutes, 10.seconds)
          .flatTap(_ =>
            logger.info(s"Queue ${Configuration.queueName} created")
          )
          .handleErrorWith { error =>
            logger.error(s"Failed to create queue: ${error.getMessage}")
          }
      )

  val queueClient: Resource[IO, QueueClient[IO]] =
    Configuration.createClient
  override def run(args: List[String]): IO[ExitCode] = queueClient.use {
    client =>
      createQueue(client).flatMap { _ =>
        produce(client).compile.drain
          .as(ExitCode.Success)
      }
  }
}
