// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez
package mock

import scala.jdk.CollectionConverters._
import cats.effect.{Resource, Sync}
import cats.implicits._
import io.opentracing.log.Fields
import io.{opentracing => ot}
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.tag.Tags
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}

import java.net.URI

final case class MockSpan[F[_] : Sync](
  tracer: ot.mock.MockTracer,
  span: ot.mock.MockSpan)
  extends Span[F] {

  def kernel: F[Kernel] =
    Sync[F].delay {
      val m = new java.util.HashMap[String, String]
      tracer.inject(
        span.context,
        Format.Builtin.HTTP_HEADERS,
        new TextMapAdapter(m)
        )
      Kernel(m.asScala.toMap)
    }

  def put(fields: (String, TraceValue)*): F[Unit] =
    fields.toList.traverse_ {
      case (k, StringValue(v)) => Sync[F].delay(span.setTag(k, v))
      case (k, NumberValue(v)) => Sync[F].delay(span.setTag(k, v))
      case (k, BooleanValue(v)) => Sync[F].delay(span.setTag(k, v))
    }

  def attachError(err: Throwable): F[Unit] = {
    put(
      Tags.ERROR.getKey -> true
    ) >>
      Sync[F].delay {
        span.log(
          Map(
            Fields.EVENT -> "error",
            Fields.ERROR_OBJECT -> err,
            Fields.ERROR_KIND -> err.getClass.getSimpleName,
            Fields.MESSAGE -> err.getMessage,
            Fields.STACK -> err.getStackTrace.mkString
          ).asJava
        )
      }.void
  }

  override def log(fields: (String, TraceValue)*): F[Unit] = {
    val map = fields.map {case (k, v) => k -> v.value }.toMap.asJava
    Sync[F].delay(span.log(map)).void
  }

  override def log(event: String): F[Unit] = {
    Sync[F].delay(span.log(event)).void
  }

  def span(name: String): Resource[F, Span[F]] =
    Resource
      .make {
        Sync[F].delay(tracer.buildSpan(name).asChildOf(span).start)
      } { s =>
        Sync[F].delay(s.finish())
      }
      .map(MockSpan(tracer, _))

  def traceId: F[Option[String]] =
    span.context.toTraceId.some.pure[F]

  def spanId: F[Option[String]] =
    span.context.toSpanId.some.pure[F]

  def traceUri: F[Option[URI]] =
    none.pure[F]

}