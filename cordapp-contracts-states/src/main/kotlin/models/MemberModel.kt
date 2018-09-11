package models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import net.corda.core.serialization.CordaSerializable

@JsonIgnoreProperties(ignoreUnknown = true)
@CordaSerializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberModel(
        val creator: String?= null,
        val viewer: String?= null,
        val title: String? = null,
        val firstName: String? = null,
        val lastName: String? = null)