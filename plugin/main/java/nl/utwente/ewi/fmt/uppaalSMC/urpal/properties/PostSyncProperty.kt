package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ValidationSpec
import org.muml.uppaal.declarations.Variable
import org.muml.uppaal.declarations.global.ChannelPriority
import org.muml.uppaal.declarations.global.GlobalFactory
import org.muml.uppaal.declarations.system.SystemFactory
import org.muml.uppaal.templates.LocationKind
import org.muml.uppaal.templates.SynchronizationKind
import org.muml.uppaal.templates.TemplatesFactory

@SanityCheck(name = "Synchronization post-condition", shortName = "sync-post")
class PostSyncProperty : SafetyProperty() {
    override fun translateNSTA(nsta: NSTA, config: ValidationSpec.PropertyConfiguration): String {
        // could add check to see if channel exists and is broadcast

        val channel = config.parameters["channel"]!!
        val condition = config.parameters["condition"]!!

        val isConcreteChecking = config.parameters[SafetyProperty.CHECKER_TYPE] == "concrete"

        addCheckAutomaton(nsta, UppaalUtil.createVariable(channel), isConcreteChecking)

        return "$TEMPLATE_NAME.$CHECK_LOCATION_NAME imply $condition"
    }

    /**
     * Adds a test automaton to the NSTA that synchronizes with the specified channel. After the synchronisation, it
     * enters a location with one outgoing edge back to the starting location. This edge synchronizes on a broadcast
     * channel with the highest priority, which ensures that no other process is able to take a transition while this
     * automaton is in the check location.
     */
    private fun addCheckAutomaton(nsta: NSTA, channelVariable: Variable, concreteChecking: Boolean) {
        val template = UppaalUtil.createTemplate(nsta, TEMPLATE_NAME)

        // add template to system declaration
        nsta.systemDeclarations.system.instantiationList.last().template.add(template)

        val waitLocation = UppaalUtil.createLocation(template, "Wait")
        template.init = waitLocation

        // the check location is marked as committed so that no delay transitions are possible
        val checkLocation = UppaalUtil.createLocation(template, CHECK_LOCATION_NAME)
        checkLocation.locationTimeKind = LocationKind.COMMITED

        // enter check location when the broadcast is sent
        val receiveEdge = UppaalUtil.createEdge(waitLocation, checkLocation)
        receiveEdge.synchronization = TemplatesFactory.eINSTANCE.createSynchronization()
        receiveEdge.synchronization.channelExpression = UppaalUtil.createIdentifier(channelVariable)
        receiveEdge.synchronization.kind = SynchronizationKind.RECEIVE

        // add return transition
        val returnEdge = UppaalUtil.createEdge(checkLocation, waitLocation)

        if (!concreteChecking) {
            // if SMC is used for checking, channel priorities are not supported. The best we can do is mark the check
            // location as committed. This leaves the possibility for another process to take a switch transition from
            // a committed location, potentially causing false negatives in specific circumstances.

            val priorityChannel = addPriorityChannel(nsta)
            returnEdge.synchronization = TemplatesFactory.eINSTANCE.createSynchronization()
            returnEdge.synchronization.channelExpression = UppaalUtil.createIdentifier(priorityChannel)
            returnEdge.synchronization.kind = SynchronizationKind.SEND
        }
    }

    /**
     * Adds a channel to the global declarations with the highest channel priority.
     */
    private fun addPriorityChannel(nsta: NSTA): Variable {
        // the channel is marked as broadcast because the are no receiving edges, and sending must not be blocking
        val priorityChannelDecl = UppaalUtil.createChannelDeclaration(nsta, "__priority_channel")
        priorityChannelDecl.isBroadcast = true

        nsta.globalDeclarations.declaration.add(priorityChannelDecl)

        // access the variable from the declaration
        val variable = priorityChannelDecl.variable[0]!!

        // add a channel priority declaration if there is not one already
        var priority: ChannelPriority? = nsta.globalDeclarations.channelPriority
        if (priority == null) {
            priority = GlobalFactory.eINSTANCE.createChannelPriority()!!
            priority.item.add(GlobalFactory.eINSTANCE.createDefaultChannelPriority())
            nsta.globalDeclarations.channelPriority = priority
        }

        // create a channel list containing only our newly created channel and append it to the end of the channel
        // priority list
        val channelList = GlobalFactory.eINSTANCE.createChannelList()
        channelList.channelExpression.add(UppaalUtil.createIdentifier(variable))
        priority.item.add(channelList)

        return variable
    }

    override fun getParameters(): List<PropertyParameter> {
        val params = listOf(PropertyParameter("channel", "Channel", ArgumentType.STRING),
                PropertyParameter("condition", "Condition", ArgumentType.STRING))

        return super.getParameters() + params
    }

    companion object {
        const val TEMPLATE_NAME = "__check_sync_post_condition"
        const val CHECK_LOCATION_NAME = "Check"
    }
}