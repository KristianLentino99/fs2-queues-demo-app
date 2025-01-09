package com.commercetools.fs2QueueDemo

import cats.effect.kernel.Resource
import cats.effect.{IO, Sync}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.{
  AnonymousCredentialsProvider,
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import java.net.URI
import com.commercetools.queue.QueueClient
import com.commercetools.queue.aws.sqs.SQSClient
import pureconfig.generic.derivation.default._
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import cats.effect.kernel.Async

object Configuration {

  final case class AwsCredentials(
      accessKeyId: String,
      secretAccessKey: String
  ) derives ConfigReader

  final case class AwsConfig(
      region: String,
      endpoint: String,
      credentials: AwsCredentials
  ) derives ConfigReader

  final case class QueueConfig(
      url: String
  ) derives ConfigReader

  final case class AppConfig(
      aws: AwsConfig,
      queue: QueueConfig
  ) derives ConfigReader

  def load[F[_]: Sync]: F[AppConfig] =
    ConfigSource.default.load[AppConfig]() match
      case Left(error) =>
        Sync[F].raiseError(new Exception(error.toList.mkString(", ")))
      case Right(config) => Sync[F].pure(config)

  def makeQueueClient[F[_]: Async](
      config: AppConfig
  ): Resource[F, QueueClient[F]] = {
    val credentials = AnonymousCredentialsProvider.create()

    SQSClient.apply[F](
      Region.of(config.aws.region),
      credentials,
      Some(new URI(config.aws.endpoint))
    )
  }

  // Convenience method to create a client with loaded config
  def createClient: Resource[IO, QueueClient[IO]] =
    Resource.eval(load[IO]).flatMap(makeQueueClient[IO])

  val queueName: String = "my-queue"
}
