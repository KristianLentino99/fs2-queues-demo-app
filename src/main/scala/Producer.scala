package com.commercetools.fs2QueueDemo
import cats.effect._
import fs2.Stream
import scala.util.Random
import com.commercetools.queue.QueueClient
import cats.implicits.catsSyntaxFlatMapOps
import com.commercetools.fs2QueueDemo.Configuration
import cats.implicits.*
import cats.syntax.all.*
import scala.concurrent.duration.DurationInt
import io.circe._, io.circe.generic.semiauto._, io.circe.syntax._
import org.typelevel.log4cats.Logger

object Producer {
  case class OrderPayload(id: String)

  object OrderPayload {

    implicit val encoder: Encoder[OrderPayload] = deriveEncoder[OrderPayload]
    implicit val decoder: Decoder[OrderPayload] = deriveDecoder[OrderPayload]

  }

  def produce[F[_]: Temporal: Async](
      queue: QueueClient[F]
  )(using logger: Logger[F]): Stream[F, Unit] = {
    val publisher = queue.publish(Configuration.queueName)
    for {
      _ <- Stream.awakeEvery[F](2.seconds)
      order <- Stream.eval(
        Async[F].delay(
          (
            OrderPayload(s"Order-${Random.nextInt(1000)}").asJson.noSpaces,
            Map.empty[String, String]
          )
        )
      )
      _ <- Stream
        .eval(
          Stream
            .emit(order)
            .through(publisher.sink(batchSize = 10))
            .compile
            .drain
        )
        .evalTap(_ => Async[F].delay(logger.info(s"Produced: $order")))
    } yield ()
  }
}
