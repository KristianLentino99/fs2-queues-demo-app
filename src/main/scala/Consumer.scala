package com.commercetools.fs2QueueDemo
import cats.effect.IOApp
import cats.effect.IO
import cats.effect.implicits.*
import cats.effect.ExitCode
import cats.implicits.*
import scala.concurrent.duration.DurationInt
import com.commercetools.queue.Message
import com.commercetools.queue.Decision
import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import com.commercetools.queue.QueueClient
import java.time.temporal.Temporal
import cats.effect.kernel.Async
import fs2.Stream
import java.util.UUID
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import io.circe.Encoder
import com.commercetools.fs2QueueDemo.Producer.OrderPayload
import io.circe._, io.circe.generic.semiauto._, io.circe.syntax._
import io.circe.parser._
import org.typelevel.log4cats.Logger

object Consumer {

  def parsePayload[F[_]: Async](payloadF: F[String]): F[OrderPayload] = {
    payloadF.map(payload => {
      val result = parse(payload).getOrElse(
        throw new Exception(s"Failed to parse payload: $payload")
      )
      result
        .as[OrderPayload]
        .getOrElse(throw new Exception(s"Failed to parse payload: $payload"))
    })
  }

  def consume[F[_]](client: QueueClient[F], id: Int)(using
      F: Async[F],
      logger: Logger[F]
  ): Stream[F, Either[Throwable, String]] = {

    def processMessage(message: Message[F, String]): F[Decision[String]] = {
      for {
        _ <- logger.info(
          s"Consumer $id processing message ${message.messageId.value}"
        )
        parsed <- parsePayload[F](message.payload)
        result <- message.metadata.get("retries").flatMap(_.toIntOption) match {
          case None =>
            logger.info(
              s"First attempt for message ${message.messageId.value}"
            ) *>
              Async[F].pure(
                Decision.Reenqueue(
                  Some(
                    Map(
                      "retries" -> "1",
                      "originalMessageId" -> message.messageId.value
                    )
                  )
                )
              )

          case Some(retries) if retries < 3 =>
            logger.info(
              s"Retry attempt $retries for original message ${message.metadata.get("originalMessageId").getOrElse(message.messageId.value)}"
            ) *>
              Async[F].pure(
                Decision.Reenqueue(
                  Some(
                    Map(
                      "retries" -> (retries + 1).toString,
                      "originalMessageId" -> message.messageId.value
                    )
                  )
                )
              )

          case Some(_) =>
            logger.info(
              s"✅ Max retries reached, Ack original message ${message.metadata.get("originalMessageId").getOrElse("unknown")}"
            ) *>
              Async[F].pure(Decision.Ok(message.messageId.value))
        }
      } yield result
    }

    Stream.eval(logger.info(s"Consumer $id started")) >>
      client
        .subscribe(Configuration.queueName)
        .process[String](
          10,
          1.seconds,
          client.publish(Configuration.queueName)
        )(msg => processMessage(msg))
  }
}
