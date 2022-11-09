package org.octopusden.f1.automation.artifactory.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class BuildInfoResponse(@JsonProperty("buildInfo") val buildInfo: BuildInfo)
class BuildInfo(@JsonProperty("name") val name: String,
                @JsonProperty("number") val number: String,
                @JsonProperty("modules") val modules: Collection<Module>,
                @JsonProperty("statuses") val statuses: Collection<Status>?,
) {
    override fun toString(): String {
        return "$name:$number"
    }
}

class Promote(@JsonProperty("status") val status: String,
              @JsonProperty("ciUser") val user: String,
              @JsonProperty("timestamp") val timestamp: Date,
              @JsonProperty("dryRun") val dryRun: Boolean,
              @JsonProperty("targetRepo") val targetRepo: String,
              @JsonProperty("copy") val copy: Boolean,
              @JsonProperty("artifacts") val artifacts: Boolean,
              @JsonProperty("dependencies") val dependencies: Boolean,
              @JsonProperty("failFast") val failFast: Boolean
)

class Artifact(@JsonProperty("type") val type: String,
               @JsonProperty("name") val name: String
)

class Module(@JsonProperty("type") val type: String?,
             @JsonProperty("id") val id: String,
             @JsonProperty("artifacts") val artifacts: Collection<Artifact>
)


class Status(@JsonProperty("status") val status: String)