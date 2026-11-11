package com.prism.prediction

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object ContractJson {
    val codec: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decodeVehicleFramesJsonl(text: String): List<VehicleFrame> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                runCatching { codec.decodeFromString<VehicleFrame>(line) }
                    .getOrElse { throw ContractException("line ${index + 1}: invalid VehicleFrame JSON: ${it.message}") }
            }
            .toList()

    fun decodePredictionFrame(text: String): PredictionFrame = codec.decodeFromString(text)

    fun encodePredictionFrame(frame: PredictionFrame): String = codec.encodeToString(frame) + "\n"
}
