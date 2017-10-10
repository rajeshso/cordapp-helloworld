package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("template")
class TemplateApi(val rpcOps: CordaRPCOps) {
    // Accessible at /api/template/hello.
    @GET
    @Path("hello")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {

        val otherParty = rpcOps.partiesFromName("PartyB", false).single()
        val flowHandle = rpcOps.startFlowDynamic(Initiator::class.java, otherParty)
        val responseMsg = flowHandle.returnValue.get()

        return Response.ok(responseMsg).build()
    }
}

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val TEMPLATE_CONTRACT_ID = "com.template.TemplateContract"

open class TemplateContract : Contract {
    // The verify() function of the contract for each of the transaction's input and output states must not throw an
    // exception for a transaction to be considered valid.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
    }
}

// *********
// * State *
// *********
class HelloState(val owner: Party) : ContractState {
    override val participants = listOf(owner)
}

class Hello(val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // TODO: Send hello to other party
    }
}

class RespondToHello(val myMsg: String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // TODO: Look in vault for hello messages

        // TODO: For each hello message, send back myMsg
    }

}


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class Initiator(val otherParty: Party) : FlowLogic<String>() {
    companion object {
        object WELCOME: ProgressTracker.Step("Ready to Say Hello")

        fun tracker() = ProgressTracker(WELCOME)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call() : String  {
        progressTracker.currentStep = WELCOME

        val session = initiateFlow(party=otherParty)
        val msg = session.sendAndReceive<String>("Hello").unwrap { it -> it }
        logger.info(msg)

        return msg
    }
}

@InitiatedBy(Initiator::class)
class Responder(val session: FlowSession) : FlowLogic<String>() { //This is other party Session

    companion object {
        object RECEIVE_HELLO: ProgressTracker.Step("Receive Hello")
        object VERIFY_HELLO: ProgressTracker.Step("Verify Hello")
        fun tracker() = ProgressTracker(RECEIVE_HELLO, VERIFY_HELLO)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call() : String {
      progressTracker.currentStep = RECEIVE_HELLO

        val greeting = session.receive<String>().unwrap { greeting ->
            progressTracker.currentStep = VERIFY_HELLO
            logger.info(greeting)
            greeting
        }
        session.send("Hi " + greeting)
        return "Hi "
    }
}

// Serialization whitelist (only needed for 3rd party classes, but we use a local example here).
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// Not annotated with @CordaSerializable just for use with manual whitelisting above.
data class TemplateData(val payload: String)

class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}
