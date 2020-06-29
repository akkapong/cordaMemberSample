package apis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import contracts.MemberContract.Companion.logger
import flows.CreateMember
import flows.EditMember
import models.MemberModel
import net.corda.core.internal.rootCause
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.getOrThrow
import net.corda.webserver.services.WebServerPluginRegistry
import org.eclipse.jetty.http.HttpStatus
import schemas.MemberSchemaV1
import states.Member
import java.util.function.Function
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("members")
class MemberApi(val services: CordaRPCOps) {

    // Accessible at /api/cleatmembers.
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getMember(
            @QueryParam(value = "title") title: String?,
            @QueryParam(value = "firstName") firstName: String?,
            @QueryParam(value = "lastName") lastName: String?): Response {

        //Create search model from query parameters
        val searchModel = MemberSearchModel(
                title = title,
                firstName = firstName,
                lastName = lastName
        )

        //generate criteria from search model
        val criteria = generateCriteria(searchModel)

        val invoicePageFound = services.vaultQueryBy<Member>(criteria)

        val members = invoicePageFound.states.map { it.state.data }

        return Response.ok(members).build()
    }

    private fun generateCriteria(searchModel: MemberSearchModel): QueryCriteria
    {
        var criteria = QueryCriteria.LinearStateQueryCriteria().and(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))


        if (searchModel.title.isNullOrEmpty().not()) {
            // We append the criteria
            val appendCriteria = QueryCriteria.VaultCustomQueryCriteria(
                    builder { (MemberSchemaV1.MemberEntity::title).equal(searchModel.title) })

            criteria = criteria.and(appendCriteria)
        }

        if (searchModel.firstName.isNullOrEmpty().not()) {
            // We append the criteria
            val appendCriteria = QueryCriteria.VaultCustomQueryCriteria(
                    builder { (MemberSchemaV1.MemberEntity::firstName).like("%${searchModel.firstName}%") })

            criteria = criteria.and(appendCriteria)
        }

        if (searchModel.lastName.isNullOrEmpty().not()) {
            // We append the criteria
            val appendCriteria = QueryCriteria.VaultCustomQueryCriteria(
                    builder { (MemberSchemaV1.MemberEntity::lastName).like("%${searchModel.lastName}%") })

            criteria = criteria.and(appendCriteria)
        }
        return criteria
    }

    // Accessible at /member.
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun postMember(member: MemberModel): Response {

        logger.info("MemberApi.postMember POST members/ : $member ")

        // since we can do a bulk creation atomically, we have to do one by one invoice.
        val (responseStatus, responseMessage) = try {
            val flowHandle = services.startTrackedFlowDynamic(CreateMember.Initiator::class.java, member)
            flowHandle.progress.subscribe({ logger.info("MemberApi.postMember: $member") })
            val stx = flowHandle.use { it.returnValue.getOrThrow() }

            // Create a invoice model pertaining to all information of the states created
            val output = stx.tx.outputsOfType<Member>()

            val http = HttpStatus.CREATED_201
            http to output

        } catch (ex: Exception) {
            logger.error("Exception during invoice: ", ex)
            val error = ex.message ?: ex.rootCause.toString()
            val http = HttpStatus.INTERNAL_SERVER_ERROR_500
            http to error
        }

        return Response.status(responseStatus).entity(responseMessage).build()
    }

    // Accessible at /member.
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    fun putMember(member: MemberModel): Response {

        logger.info("MemberApi.putMember POST members/ : $member ")

        // since we can do a bulk creation atomically, we have to do one by one invoice.
        val (responseStatus, responseMessage) = try {
            val flowHandle = services.startTrackedFlowDynamic(EditMember.Initiator::class.java, member)
            flowHandle.progress.subscribe({ logger.info("MemberApi.putMember: $member") })
            val stx = flowHandle.use { it.returnValue.getOrThrow() }

            // Create a invoice model pertaining to all information of the states created
            val output = stx.tx.outputsOfType<Member>()

            val http = HttpStatus.CREATED_201
            http to output

        } catch (ex: Exception) {
            logger.error("Exception during invoice: ", ex)
            val error = ex.message ?: ex.rootCause.toString()
            val http = HttpStatus.INTERNAL_SERVER_ERROR_500
            http to error
        }

        return Response.status(responseStatus).entity(responseMessage).build()
    }
}

/**
 * Search model object for store filter value
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@CordaSerializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberSearchModel(
        val title: String? = null,
        val firstName: String? = null,
        val lastName: String? = null
)


// ***********
// * Plugins *
// ***********
class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of lambdas that create objects exposing web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::MemberApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
        // This will serve the templateWeb directory in resources to /web/template
        "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)
