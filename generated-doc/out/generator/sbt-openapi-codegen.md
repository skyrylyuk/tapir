# Generate endpoint definitions from an OpenAPI YAML

```eval_rst
.. note::

  This is a really early alpha implementation.
```

## Installation steps

Add the sbt plugin to the `project/plugins.sbt`:

```scala
addSbtPlugin("com.softwaremill.sttp.tapir" % "sbt-openapi-codegen" % "1.6.1")
```

Enable the plugin for your project in the `build.sbt`:

```scala
enablePlugins(OpenapiCodegenPlugin)
```

Add your OpenApi file to the project, and override the `openapiSwaggerFile` setting in the `build.sbt`:

```scala
openapiSwaggerFile := baseDirectory.value / "swagger.yaml"
```

At this point your compile step will try to generate the endpoint definitions 
to the `sttp.tapir.generated.TapirGeneratedEndpoints` object, where you can access the 
defined case-classes and endpoint definitions.

## Usage and options

The generator currently supports these settings, you can override them in the `build.sbt`;

```eval_rst
=================== ==================================== ===========================================
setting             default value                        description                             
=================== ==================================== ===========================================
openapiSwaggerFile  baseDirectory.value / "swagger.yaml" The swagger file with the api definitions.
openapiPackage      sttp.tapir.generated                 The name for the generated package.
openapiObject       TapirGeneratedEndpoints              The name for the generated object.
=================== ==================================== ===========================================
```

The general usage is;

```scala
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.generated._
import sttp.tapir.docs.openapi._

val docs = TapirGeneratedEndpoints.generatedEndpoints.toOpenAPI("My Bookshop", "1.0")
```

### Limitations

Currently, the generated code depends on `"io.circe" %% "circe-generic"`. In the future probably we will make the encoder/decoder json lib configurable (PRs welcome).

We currently miss a lot of OpenApi features like:
 - tags
 - enums/ADTs
 - missing model types and meta descriptions (like date, minLength)
 - file handling

