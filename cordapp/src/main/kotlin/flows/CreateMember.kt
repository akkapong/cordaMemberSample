package flows

import co.paralleluniverse.fibers.Suspendable
import contracts.MemberContract
import models.MemberModel
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import states.Member

object CreateMember {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val memberModel: MemberModel) : FlowLogic<SignedTransaction>() {
        companion object {
            object INITIALISING : ProgressTracker.Step("Performing initial steps.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("Signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        val firstNotary
            get() = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw FlowException("No available notary.")

        @Suspendable
        override fun call() : SignedTransaction {
            // Flow implementation goes here
            logger.info("CreateMember.memberModel: $memberModel")
            // Step 1. Verify first
            inspect()

            // Step 2. Initialisation. Initiated by creator
            progressTracker.currentStep = INITIALISING
            val member = createMember(memberModel)

            val ourSigningKey = member.creator.owningKey

            // Step 3. Building.
            val participants = listOf(member.creator, member.viewer)
            val listKey = participants.map { it.owningKey }
            progressTracker.currentStep = BUILDING
            val utx = TransactionBuilder(firstNotary)
                    // outputs
                    .withItems(StateAndContract(member, MemberContract.MEMBER_CONTRACT_ID))

            utx.addCommand(MemberContract.Commands.Issue(), listKey)

            // Step 4. Sign the transaction.
            progressTracker.currentStep = SIGNING
            // Borrower initiate the flow and therefore sign it
            val ptx = serviceHub.signInitialTransaction(utx, ourSigningKey)

            // Step 5. Get the counter-party signature.
            // create session
            val everyoneExceptMe = participants.toSet().minus(ourIdentity)
            val sessions = everyoneExceptMe.map { (initiateFlow(it)) }.toSet()
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    sessions,
                    listOf(ourSigningKey),
                    COLLECTING.childProgressTracker())
            )

            // Step 6. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }

        private fun createMember(memberModel: MemberModel): Member {

            // Step 1. Query identity service for viewer based on X500Name
            val viewer = serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse(memberModel.viewer!!))!!

            val result = Member(
                    creator = ourIdentity,
                    viewer = viewer,
                    title = memberModel.title,
                    firstName = memberModel.firstName,
                    lastName = memberModel.lastName
            )
            return result
        }

        private fun inspect() {
            requireThat {
                "The title cannot be empty" using (memberModel.title.isNullOrEmpty().not())
                "The first name cannot be empty" using (memberModel.firstName.isNullOrEmpty().not())
                "The last name cannot be empty" using (memberModel.lastName.isNullOrEmpty().not())
            }
        }
    }

    @InitiatedBy(CreateMember.Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(counterpartySession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    val members = stx.tx.outputsOfType<Member>()
                    check(members.isNotEmpty()) { "Member in transaction must not be empty." }
                    check(members.all { resolveIdentity(it.creator)!!.name.organisation == "PartyA" }) {"Only Party A can create the member."}
                    check(members.all { counterpartySession.counterparty == resolveIdentity(it.creator) }) { "The initiator must be the creator of the state." }
                    return
                }
            }
            val stx = subFlow(flow)
            val waitForLedgerCommit = waitForLedgerCommit(stx.id)
            return waitForLedgerCommit
        }

        /**
        * A function to resolve abstract party to known party
        */
        @Suspendable
        protected fun resolveIdentity(abstractParty: AbstractParty): Party? {
            return serviceHub.identityService.wellKnownPartyFromAnonymous(abstractParty)
        }
    }
}