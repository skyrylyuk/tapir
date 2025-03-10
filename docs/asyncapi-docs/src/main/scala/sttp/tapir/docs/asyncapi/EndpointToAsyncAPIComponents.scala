package sttp.tapir.docs.asyncapi

import sttp.apispec.{ReferenceOr, Schema => ASchema}
import sttp.apispec.asyncapi.{Components, Message}
import sttp.tapir.docs.apispec.SecuritySchemes
import sttp.tapir.docs.apispec.schema.SchemaId
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

private[asyncapi] class EndpointToAsyncAPIComponents(
    idToSchema: ListMap[SchemaId, ReferenceOr[ASchema]],
    keyToMessage: ListMap[MessageKey, Message],
    securitySchemes: SecuritySchemes
) {
  def components: Option[Components] = {
    if (idToSchema.nonEmpty || securitySchemes.nonEmpty || keyToMessage.nonEmpty)
      Some(
        Components(
          idToSchema,
          keyToMessage.map { case (k, m) => (k, Right(m)) },
          securitySchemes.values.toMap.mapValues(Right(_)).toListMap,
          ListMap.empty,
          ListMap.empty,
          ListMap.empty,
          ListMap.empty
        )
      )
    else None
  }
}
