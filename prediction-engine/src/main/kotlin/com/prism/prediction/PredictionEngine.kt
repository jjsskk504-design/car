package com.prism.prediction

object PredictionEngine {
    fun predict(
        frames: List<VehicleFrame>,
        config: PredictionConfig = PredictionConfig(),
        includeEgo: Boolean = false,
        egoPoses: List<Pose2D>? = null,
    ): PredictionFrame {
        val ordered = History.prepare(frames)
        val normalized = History.normalizeToCurrentEgo(ordered, egoPoses)
        val current = normalized.last()
        val knownIds = current.vehicles.map { it.id }.toSet()
        val predictions = current.vehicles
            .asSequence()
            .filter { includeEgo || it.id != current.egoId }
            .map { vehicle ->
                VehiclePrediction(
                    vehicleId = vehicle.id,
                    trajectories = Modes.candidates(History.observations(normalized, vehicle.id), config),
                )
            }
            .toList()
        return PredictionFrame(timestampMs = current.timestampMs, predictions = predictions).also {
            validatePredictionFrame(it, knownIds)
        }
    }

    fun predictJsonl(
        vehicleFramesJsonl: String,
        config: PredictionConfig = PredictionConfig(),
        includeEgo: Boolean = false,
    ): String = ContractJson.encodePredictionFrame(
        predict(ContractJson.decodeVehicleFramesJsonl(vehicleFramesJsonl), config, includeEgo),
    )

    fun validatePredictionFrame(frame: PredictionFrame, knownVehicleIds: Set<String>? = null) {
        if (frame.schemaVersion != "1.0") throw ContractException("unsupported schema_version")
        if (frame.timestampMs < 0) throw ContractException("timestamp_ms must be non-negative")
        frame.predictions.forEach { prediction ->
            if (prediction.vehicleId.isBlank()) throw ContractException("vehicle_id must be non-empty")
            if (knownVehicleIds != null && prediction.vehicleId !in knownVehicleIds) {
                throw ContractException("prediction references unknown vehicle '${prediction.vehicleId}'")
            }
            if (prediction.trajectories.isEmpty()) throw ContractException("at least one trajectory is required")
            prediction.trajectories.forEach { trajectory ->
                if (trajectory.mode.isBlank()) throw ContractException("trajectory mode must be non-empty")
                if (trajectory.confidence != null && trajectory.confidence !in 0.0..1.0) {
                    throw ContractException("confidence must be null or between 0 and 1")
                }
                if (trajectory.points.isEmpty()) throw ContractException("trajectory points must be non-empty")
                trajectory.points.forEach { point ->
                    if (point.dtMs !in 1..5000) throw ContractException("dt_ms must be in 1..5000")
                    if (!point.x.isFinite() || !point.y.isFinite() || !point.headingRad.isFinite()) {
                        throw ContractException("prediction point contains a non-finite number")
                    }
                }
            }
        }
    }
}
