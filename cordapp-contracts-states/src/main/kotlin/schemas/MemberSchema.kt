package schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object MemberSchema

@CordaSerializable
object MemberSchemaV1 : MappedSchema(schemaFamily = MemberSchema.javaClass,
        version = 1,
        mappedTypes = listOf(MemberEntity::class.java)) {

    @Entity
    @Table(name = "member_states")
    class MemberEntity(

            @Column(name = "linear_id")
            var linearId: String? = null,

            @Column(name = "creator")
            var creator: String? = null,

            @Column(name = "viewer")
            var viewer: String? = null,

            @Column(name = "observer")
            var observer: String? = null,

            @Column(name = "title")
            var title: String? = null,

            @Column(name = "first_name")
            var firstName: String? = null,

            @Column(name = "last_name")
            var lastName: String? = null

    ) : PersistentState()

}