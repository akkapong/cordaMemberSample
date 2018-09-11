package states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import schemas.MemberSchemaV1

data class Member(
        val creator: Party,
        val viewer: Party,
        val title: String?,
        val firstName: String?,
        val lastName: String?,
        override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(MemberSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when(schema) {
            is MemberSchemaV1 -> MemberSchemaV1.MemberEntity(
                    linearId = this.linearId.id.toString(),
                    creator = this.creator.name.toString(),
                    viewer = this.viewer.name.toString(),
                    title = this.title,
                    firstName = this.firstName,
                    lastName = this.lastName
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override val participants: List<AbstractParty> = listOf(creator, viewer)

}