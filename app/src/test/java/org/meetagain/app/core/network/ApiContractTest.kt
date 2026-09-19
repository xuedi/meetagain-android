package org.meetagain.app.core.network

import java.io.File
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks each DTO against its schema in the committed copy of the server's API description (`just api-refresh`),
 * so a server change fails here rather than on a member's screen.
 */
class ApiContractTest {
    private val dtos: Map<String, SerialDescriptor> = mapOf(
        "HealthStatus" to Status.serializer().descriptor,
        "EventList" to EventListDto.serializer().descriptor,
        "EventSummary" to EventSummaryDto.serializer().descriptor,
        "EventDetail" to EventDetailDto.serializer().descriptor,
        "EventLocation" to EventLocationDto.serializer().descriptor,
        "GroupList" to GroupListDto.serializer().descriptor,
        "GroupSummary" to GroupSummaryDto.serializer().descriptor,
        "GroupDetail" to GroupDetailDto.serializer().descriptor
    )

    private val schemas: JsonObject by lazy {
        val spec = File(System.getProperty("openapi.spec") ?: error("openapi.spec is not set"))
        Json.parseToJsonElement(spec.readText()).jsonObject.getValue("components").jsonObject
            .getValue("schemas").jsonObject
    }

    @Test
    fun `every DTO matches its schema`() {
        val problems = dtos.flatMap { (name, descriptor) -> check(name, descriptor) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun check(name: String, descriptor: SerialDescriptor): List<String> {
        val schema = schemas[name]?.jsonObject ?: return listOf("$name: no such schema")
        val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        return (0 until descriptor.elementsCount).mapNotNull { index ->
            val field = descriptor.getElementName(index)
            val element = descriptor.getElementDescriptor(index)
            val declared = properties[field]?.jsonObject ?: return@mapNotNull "$name.$field: not in the schema"
            val (property, nullable) = resolve(declared)
            val optional = descriptor.isElementOptional(index)
            when {
                !typeMatches(element.kind, property) ->
                    "$name.$field: ${element.kind} does not match ${property["type"] ?: property["\$ref"]}"

                field !in required && !element.isNullable && !optional ->
                    "$name.$field: optional in the schema, so it needs a default or must be nullable"

                nullable && !element.isNullable -> "$name.$field: nullable in the schema"

                else -> null
            }
        }
    }

    /**
     * The property without its null alternative, and whether null is allowed: OpenAPI 3.0's `nullable`, 3.1's
     * `type: [x, "null"]`, or `oneOf: [x, {type: null}]`.
     */
    private fun resolve(property: JsonObject): Pair<JsonObject, Boolean> {
        val oneOf = property["oneOf"]?.jsonArray?.map { it.jsonObject }
        if (oneOf != null) {
            val nonNull = oneOf.filterNot { it["type"]?.jsonPrimitive?.contentOrNull == "null" }
            return (nonNull.singleOrNull() ?: property) to (nonNull.size < oneOf.size)
        }
        val types = (property["type"] as? JsonArray)?.map { it.jsonPrimitive.content }
        if (types != null) {
            val nonNull = types - "null"
            val single = JsonObject(property + ("type" to JsonPrimitive(nonNull.singleOrNull() ?: nonNull.toString())))
            return single to (nonNull.size < types.size)
        }
        return property to (property["nullable"]?.jsonPrimitive?.boolean == true)
    }

    private fun typeMatches(kind: SerialKind, property: JsonObject): Boolean {
        if (property.containsKey("\$ref")) return kind == StructureKind.CLASS || kind == StructureKind.OBJECT
        return when (property["type"]?.jsonPrimitive?.content) {
            "string" -> kind == PrimitiveKind.STRING
            "integer" -> kind == PrimitiveKind.INT || kind == PrimitiveKind.LONG
            "number" -> kind == PrimitiveKind.DOUBLE || kind == PrimitiveKind.FLOAT
            "boolean" -> kind == PrimitiveKind.BOOLEAN
            "array" -> kind == StructureKind.LIST
            "object" -> kind == StructureKind.CLASS || kind == StructureKind.MAP
            else -> false
        }
    }
}
