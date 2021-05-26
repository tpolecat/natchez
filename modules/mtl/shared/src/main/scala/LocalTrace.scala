// Copyright (c) 2019-2020 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez

package mtl

import cats.mtl.Local
import cats.effect._
import cats.syntax.all._
import java.net.URI

private[mtl] class LocalTrace[F[_]](local: Local[F, Span[F]])(
  implicit ev: Bracket[F, Throwable]
) extends Trace[F] {
  type Sp[A] = F[A]

  def liftSp[A](fa: F[A]): F[A] = fa

  def kernel: F[Kernel] =
    local.ask.flatMap(_.kernel)

  def put(fields: (String, TraceValue)*): F[Unit] =
    local.ask.flatMap(_.put(fields: _*))

  def span[A](name: String)(k: F[A]): F[A] =
    local.ask.flatMap { span =>
      span.span(name).use(local.scope(k))
    }

  def traceId: F[Option[String]] =
    local.ask.flatMap(_.traceId)

  def traceUri: F[Option[URI]] =
    local.ask.flatMap(_.traceUri)

  def current: F[Span[F]] = local.ask

  def runWith[A](span: Span[F])(k: F[A]): F[A] = local.scope(k)(span)
}
