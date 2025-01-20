package com.commercetools.fs2QueueDemo
import cats.effect.IO
import cats.effect.IOApp
import com.commercetools.queue.QueueStatistics
import scala.concurrent.duration.DurationInt

object Statistics extends IOApp.Simple {
  override def run: IO[Unit] = {
    val queueClient = Configuration.createClient
    queueClient.use { client =>
      client
        .statistics(Configuration.queueName)
        .stream(5.seconds)
        .evalMap {
          case Right(value) =>
            IO.println(
              s"""
              |Messages: ${value.messages}
              |Messages in flight: ${value.inflight}
              |Messages delayed: ${value.delayed}
              |""".stripMargin
            )
          case Left(error) =>
            IO.println(s"Statistics failed to be retrieved: $error")
        }
        .compile
        .drain
    }
  }
}
