package contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import states.Member
import java.security.PublicKey

class MemberContract : Contract {

    companion object {
        @JvmStatic
        val MEMBER_CONTRACT_ID = "contracts.MemberContract"
        val logger = loggerFor<MemberContract>()
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands

    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.Issue -> verifyIssue(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyIssue(tx: LedgerTransaction, ofSigners: Set<PublicKey>) = requireThat {
        val memberOuts = tx.outputsOfType<Member>()
        "At least one output must be produced when verifyIssue member." using (memberOuts.isNotEmpty())

        "Only one member can create in the same time." using (memberOuts.size == 1)
        val memberOut = memberOuts.single()

        "All participants signs together only may sign when create member." using (ofSigners == (keysFromParticipants(memberOut)))
    }

    private fun keysFromParticipants(state: Member): Set<PublicKey> {
        return state.participants.map {
            it.owningKey
        }.toSet()
    }
}