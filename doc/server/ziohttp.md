# Running as a zio-http server

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with
[ZIO](https://zio.dev) and tapir. Moreover, `tapir-zio-http-server` contains an interpreter useful when
exposing the endpoints using the [ZIO Http](https://github.com/dream11/zio-http) server.

The `*-zio` modules depend on ZIO 2.x. For ZIO 1.x support, use modules with the `*-zio1` suffix.

You'll need the following dependency for the `ZServerEndpoint` type alias and helper classes:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "@VERSION@"
```

or just add the zio-http integration which already depends on `tapir-zio`:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % "@VERSION@"
```

Next, instead of the usual `import sttp.tapir._`, you should import (or extend the `ZTapir` trait, see [MyTapir](../mytapir.md)):

```scala mdoc:compile-only
import sttp.tapir.ztapir._
```

This brings into scope all the [basic](../endpoint/basics.md) input/output descriptions, which can be used to define an endpoint.

```eval_rst
.. note::

  You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
  ``import sttp.tapir.ztapir._`` import is meant as a complete replacement of ``import sttp.tapir._``.
```

## Exposing endpoints

```scala mdoc:compile-only
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
```

The `ZioHttpInterpreter` objects contains the `toHttp` method, which requires a `ZServerEndpoint` (see below). For 
example:

```scala mdoc:compile-only
import sttp.tapir.PublicEndpoint
import sttp.tapir.ztapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{HttpApp, Request, Response}
import zio._

def countCharacters(s: String): ZIO[Any, Nothing, Int] =
  ZIO.succeed(s.length)

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersHttp: HttpApp[Any, Throwable] =
  ZioHttpInterpreter().toHttp(countCharactersEndpoint.zServerLogic(countCharacters))
```

```eval_rst
.. note::

  A single ZIO-Http application can contain both tapir-managed and ZIO-Http-managed routes. However, because of the 
  routing implementation in ZIO Http, the shape of the paths that tapir and other ZIO Http handlers serve should not 
  overlap. The shape of the path includes exact path segments, single- and multi-wildcards. Otherwise, request handling 
  will throw an exception. We don't expect users to encounter this as a problem, however the implementation here 
  diverges a bit comparing to other interpreters.
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the
[standard ones](logic.md):

* `def zServerLogic[R](logic: I => ZIO[R, E, O]): ZServerEndpoint[R, C]` for public endpoints
* `def zServerSecurityLogic[R, U](f: A => ZIO[R, E, U]): ZPartialServerEndpoint[R, A, U, I, E, O, C]` for secure endpoints

The first defines complete server logic, while the second and third allow defining server logic in parts.

```eval_rst
.. note::

  When using Scala 3, it's best to provide the type of the environment explicitly to avoid type inferencing issues.
  E.g.: ``myEndpoint.zServerLogic[Any](...)``.
```

## Streaming

The zio-http interpreter accepts streaming bodies of type `Stream[Throwable, Byte]`, as described by the `ZioStreams`
capability. Both response bodies and request bodies can be streamed. Usage: `streamBody(ZioStreams)(schema, format)`.

The capability can be added to the classpath independently of the interpreter through the
`"com.softwaremill.sttp.shared" %% "zio"` dependency.

## Configuration

The interpreter can be configured by providing an `ZioHttpServerOptions` value, see
[server options](options.md) for details.
