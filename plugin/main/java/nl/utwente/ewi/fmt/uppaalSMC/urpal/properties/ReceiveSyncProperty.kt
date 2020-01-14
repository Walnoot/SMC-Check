package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.AbstractTemplate
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.muml.uppaal.declarations.DeclarationsFactory
import org.muml.uppaal.expressions.AssignmentOperator
import org.muml.uppaal.expressions.CompareOperator
import org.muml.uppaal.expressions.ExpressionsFactory
import org.muml.uppaal.expressions.IncrementDecrementOperator
import org.muml.uppaal.templates.LocationKind
import org.muml.uppaal.templates.SynchronizationKind
import org.muml.uppaal.templates.TemplatesFactory
import org.muml.uppaal.types.TypesFactory

@SanityCheck(name = "Receive Syncs", shortName = "receivesyncs")
class ReceiveSyncProperty : SafetyProperty() {
    override fun translateNSTA(nsta: NSTA, properties: Map<String, Any>): String {
		// we need to compile the Document so that we know the amount of instances for each Template.
		val sys = UppaalUtil.compile(UppaalUtil.toDocument(nsta, Document(PrototypeDocument())))

        val checkedChannel = properties["channel"] as String
		val checkedTemplate = properties["template"] as String

		// declare counter variables
        val dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
//        dvd.prefix = DataVariablePrefix.META
        val ref = TypesFactory.eINSTANCE.createTypeReference()
        ref.referredType = nsta.int
        dvd.typeDefinition = ref

		// amount of received syncs
        val receivedVar = UppaalUtil.createVariable("__received_${checkedChannel}")
        dvd.variable.add(receivedVar)

		// amount of sent syncs
        val sentVar = UppaalUtil.createVariable("__sent_${checkedChannel}")
        dvd.variable.add(sentVar)

        nsta.globalDeclarations.declaration.add(dvd)

		// Map from template name to number of instances
		val processCount = mutableMapOf<String, Int>()

		// collect all processes, count number of instances
		var prevTemplate: AbstractTemplate? = null
		for (p in sys.processes) {
			val name = p.template.getPropertyValue("name") as String
			if (p.template != prevTemplate) {
				prevTemplate = p.template
				processCount[name] = 1
			} else {
				processCount[name] = processCount[name]!! + 1
			}
		}

		for (template in nsta.template) {
			for (edge in template.edge) {
				// check if the edge synchronises on the channel were interested in
				if (edge.synchronization != null && edge.synchronization.channelExpression.identifier.name == checkedChannel) {
					if (edge.synchronization.kind == SynchronizationKind.RECEIVE) {
						if (edge.parentTemplate.name == checkedTemplate) {
							// increment received var by one
							val increment = ExpressionsFactory.eINSTANCE.createPostIncrementDecrementExpression()
							increment.operator = IncrementDecrementOperator.INCREMENT
							increment.expression = UppaalUtil.createIdentifier(receivedVar)

							edge.update.add(increment)
						}
					} else {
						// increment sent var by number of template instances
						val assignment = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
						assignment.operator = AssignmentOperator.PLUS_EQUAL
						assignment.firstExpr = UppaalUtil.createIdentifier(sentVar)
						assignment.secondExpr = UppaalUtil.createLiteral(processCount[checkedTemplate].toString())

						edge.update.add(assignment)
					}
				}
			}
		}

		// create an additional template that transitions to an Error location as soon as the sent/received vars are
		// unequal. The automata then immediately transitions back to the starting location, resetting the counter
		// variables.

		val cd = DeclarationsFactory.eINSTANCE.createChannelVariableDeclaration()
		val chanType = TypesFactory.eINSTANCE.createTypeReference()
		chanType.referredType = nsta.chan
		cd.typeDefinition = chanType
		cd.isBroadcast = true
		cd.isUrgent = true

		val urgentChanVar = UppaalUtil.createVariable("__synccheck_chan_urgent")
		cd.variable.add(urgentChanVar)

		nsta.globalDeclarations.declaration.add(cd)

		val templateName = "__synccheck"
		val template = UppaalUtil.createTemplate(nsta, templateName)
		nsta.systemDeclarations.system.instantiationList[0].template.add(template)

		val startLoc = UppaalUtil.createLocation(template, "Start")
		template.init = startLoc

		val errorLocName = "Error"
		val errorLoc = UppaalUtil.createLocation(template, errorLocName)

		errorLoc.locationTimeKind = LocationKind.URGENT

		val errorEdge = UppaalUtil.createEdge(startLoc, errorLoc)

		// expression sentVar!=receivedVar
		val errorGuard = ExpressionsFactory.eINSTANCE.createCompareExpression()
		errorGuard.operator = CompareOperator.UNEQUAL
		errorGuard.firstExpr = UppaalUtil.createIdentifier(receivedVar)
		errorGuard.secondExpr = UppaalUtil.createIdentifier(sentVar)
		errorEdge.guard = errorGuard

		val sync = TemplatesFactory.eINSTANCE.createSynchronization()
		sync.channelExpression = UppaalUtil.createIdentifier(urgentChanVar)
		sync.kind = SynchronizationKind.SEND
		errorEdge.synchronization = sync

		val resetEdge = UppaalUtil.createEdge(errorLoc, startLoc)

		// statement receivedVar = 0
		val resetReceived = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
		resetReceived.operator = AssignmentOperator.EQUAL
		resetReceived.firstExpr = UppaalUtil.createIdentifier(receivedVar)
		resetReceived.secondExpr = UppaalUtil.createLiteral("0")

		resetEdge.update.add(resetReceived)

		// statement sentVar = 0
		val resetSent = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
		resetSent.operator = AssignmentOperator.EQUAL
		resetSent.firstExpr = UppaalUtil.createIdentifier(sentVar)
		resetSent.secondExpr = UppaalUtil.createLiteral("0")

		resetEdge.update.add(resetSent)

		// get ignore condition
		val cond = properties.getOrDefault("condition", "false") as String

		return  "$templateName.$errorLocName and !($cond)"
    }
}



