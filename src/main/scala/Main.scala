package com.commercetools.fs2QueueDemo
import cats.effect.kernel.Ref
import cats.effect.std.Random
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all.*
import fs2.Stream
import cats.implicits.*
import com.commercetools.queue.{Decision, Message, MessageHandler, QueueClient}
import com.commercetools.queue.aws.sqs.SQSClient
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import cats.effect.unsafe.implicits.global
import java.net.URI
import scala.jdk.CollectionConverters.*
import scala.concurrent.duration.DurationInt
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.util.UUID
import org.typelevel.log4cats.Logger

object DemoApp extends IOApp.Simple {

  val queueName: String = Configuration.queueName
  val queueClient: Resource[IO, QueueClient[IO]] =
    Configuration.createClient
  given unsafeLogger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = queueClient.use { client =>
    for {
      _ <- createQueue(client)
      numberOfConsumers = List(1, 2, 3)
      _ <- (
        Producer.produce(client) merge
          Stream
            .emits(numberOfConsumers)
            .map(id => Consumer.consume[IO](client, id))
            .parJoin(
              numberOfConsumers.size
            ) // allow multiple consumers to run in parallel
      ).compile.drain
      _ <- IO.never
    } yield ()
  }

  private def createQueue(client: QueueClient[IO]): IO[Unit] =
    client.administration
      .exists(queueName)
      .ifM(
        IO.unit,
        client.administration.create(queueName, 10.minutes, 10.seconds)
      )
}
