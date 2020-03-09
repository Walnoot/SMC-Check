package nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers

import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.core2.Query
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.symbolic.SymbolicTrace
import com.uppaal.model.system.symbolic.SymbolicTransition
import nl.utwente.ewi.fmt.uppaalSMC.ChanceEdge
import nl.utwente.ewi.fmt.uppaalSMC.ChanceNode
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.Serialization
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.DeclarationsFactory
import org.muml.uppaal.declarations.system.SystemFactory
import org.muml.uppaal.expressions.*
import org.muml.uppaal.statements.StatementsFactory
import org.muml.uppaal.templates.*

class ConcolicChecker(doc: Document) : ReachabilityChecker(doc) {
    override fun isReachable(condition: String, nsta: NSTA): Pair<CheckResult, (() -> Unit)?> {
        addClockVariable(nsta)

        val abstractNSTA = EcoreUtil.copy(nsta)
        abstractNSTA(abstractNSTA)

//        instantiateTemplates(nsta)

        val (trace, sys) = getSymbolicTrace(abstractNSTA, condition)


        if (trace == null) {
            return Pair(CheckResult.UNREACHABLE, null)
        }

//        val locIndex = annotateLocations(nsta)

        constrainNSTA(nsta, trace)

        val tDoc = toDocument(nsta)

        // for easy debugging
        tDoc.queryList.addLast(Query("simulate [<=66] {__cur_transition}", ""))
        tDoc.save("/home/michiel/Downloads/test.xml")

        val tSys = UppaalUtil.compile(tDoc)

        // TODO: get from specification
        val simTime = 66
        val numSims = 1000

        // simulate N times, filter on the unsafe condition, and stop after one trace is found
        val query = "simulate [<=$simTime;$numSims] {1} :1: ($condition)"
        println(query)

        val (qr, t) = AbstractProperty.engineQueryConcrete(tSys, query, "trace 1")
        println(qr)
        println(qr.exception)
        println(qr.message)

        val result = if (qr.status == QueryResult.OK) CheckResult.REACHABLE else CheckResult.MAYBE_REACHABLE

        return Pair(result, null)
    }

    /**
     * Transform the NSTA such that only location transitions that 'follow' the specified trace are enabled. Currently
     * only constrains the NSTA w.r.t. the process location state in the symbolic trace, and ignores discrete variables
     * and clock constraints.
     */
    private fun constrainNSTA(nsta: NSTA, trace: SymbolicTrace) {
        val currentTransition = UppaalUtil.createVariable("__cur_transition")

        val currentTransitionDecl = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
        currentTransitionDecl.typeDefinition = UppaalUtil.createTypeReference(nsta.int)
        currentTransitionDecl.variable.add(currentTransition)
        nsta.globalDeclarations.declaration.add(currentTransitionDecl)

        val edgeConditions = mutableMapOf<Edge, MutableList<Expression>>()

        for ((transitionIndex, transition) in trace.withIndex()) {
            print("${transitionIndex - 1}: ")
            println(transition.target.locations.map { "${it.processName}.${it.name}" }.joinToString(", "))

            // ignore process indices for now
            val edges = findNSTAEdges(transition, nsta)

            println(edges.map { "${it.source.name} -> ${it.target.name}" })

            val transitionCheck = UppaalUtil.createCompareExpression(
                    CompareOperator.EQUAL,
                    UppaalUtil.createIdentifier(currentTransition),
                    UppaalUtil.createLiteral((transitionIndex - 1).toString())
            )

            for (edge in edges) {
                edgeConditions.putIfAbsent(edge, mutableListOf())
                edgeConditions[edge]!!.add(transitionCheck)
            }
        }

        // track the restrictions added to the edges so they can be used in the added self-loops of urgent/committed
        // locations
        val edgeChecks = mutableMapOf<Edge, Expression>()

        for (edge in nsta.template.flatMap { it.edge }) {
            val conditions = edgeConditions.getOrDefault(edge, emptyList<Expression>())

            val check = UppaalUtil.chainLogicalExpression(conditions.map { EcoreUtil.copy(it) }, LogicalOperator.OR, "false")

            edgeChecks[edge] = check

            if (edge.guard == null) {
                edge.guard = check
            } else {
                edge.guard = UppaalUtil.createLogicalExpression(
                        LogicalOperator.AND,
                        edge.guard,
                        check
                )
            }

            // add transition counter variable update

            // dont add the update to synchronisation receivers because that would cause multiple
            // increments of the variable
            if (edge.synchronization == null || edge.synchronization.kind == SynchronizationKind.SEND) {
                // dont add update to chance edges to avoid a double update
                if (edge !is ChanceEdge) {
                    val update = ExpressionsFactory.eINSTANCE.createPostIncrementDecrementExpression()
                    update.operator = IncrementDecrementOperator.INCREMENT
                    update.expression = UppaalUtil.createIdentifier(currentTransition)
                    edge.update.add(update)
                }
            }
        }

//        val urgentChan = UppaalUtil.createVariable("__u")
//        val declUrgentChan = DeclarationsFactory.eINSTANCE.createChannelVariableDeclaration()
//        declUrgentChan.isUrgent = true
//        declUrgentChan.isBroadcast = true
//        declUrgentChan.typeDefinition = UppaalUtil.createTypeReference(nsta.chan)
//        declUrgentChan.variable.add(urgentChan)
//        nsta.globalDeclarations.declaration.add(declUrgentChan)
//
//        // add self-loops to urgent/committed locations that have restricted outgoing edges
//        for (location in nsta.template.flatMap { it.location }.filter { it !is ChanceNode }) {
//            println("adding self-loop to location ${location.parentTemplate.name}.${location.name}")
//
//            val checks = location.parentTemplate.edge.filter { it.source == location }
//                    .map { edgeChecks[it]!! }
//                    .map { EcoreUtil.copy(it) }
//
//            val inverseGuard = UppaalUtil.chainLogicalExpression(checks, LogicalOperator.OR, "false")
//            val guard = UppaalUtil.negate(inverseGuard)
//
//            val loop = UppaalUtil.createEdge(location, location)
//            loop.guard = guard
//            loop.synchronization = TemplatesFactory.eINSTANCE.createSynchronization()
//            loop.synchronization.kind = SynchronizationKind.SEND
//            loop.synchronization.channelExpression = UppaalUtil.createIdentifier(urgentChan)
//        }

//        for (edge in nsta.template.flatMap { it.edge }) {
//
//            // one of these conditions must be true to be able to fire the edge
//            val conditions = mutableListOf<Expression>()
//
////            val targets = if (edge.target is ChanceNode) {
////                edge.parentTemplate.edge.filter { it.source == edge.target }.map { it.target }
////            } else {
////                listOf(edge.target)
////            }
//
//            for ((newTransitionIndex, transition) in trace.withIndex()) {
//                val transitionIndex = newTransitionIndex - 1
//
//                for (location in transition.target.locations) {
//                    // check any of the locations in the target state of this transition match the target of the edge
//                    val templateName = location.location.template.getPropertyValue("name")
//                    val nstaTemplate = nsta.template.find { it.name == templateName }!!
//                    val nstaLocation = nstaTemplate.location.find { it.name == location.name }!!
//
//                    if (transition.involvesProcess(location.process.index) && nstaLocation == edge.target) {
//                        // check for current transition index
//                        val locationCheck = UppaalUtil.createCompareExpression(
//                                CompareOperator.EQUAL,
//                                UppaalUtil.createIdentifier(currentTransition),
//                                UppaalUtil.createLiteral(transitionIndex.toString())
//                        )
//
//                        // check if the parameters of this template equal the arguments of the compiled system
//                        val parameterChecks = nstaTemplate.parameter.flatMap { it.variableDeclaration.variable }.map {
//                            val argumentValue = location.process.translator.translate(it.name)
//                            UppaalUtil.createCompareExpression(
//                                    CompareOperator.EQUAL,
//                                    UppaalUtil.createIdentifier(it),
//                                    UppaalUtil.createLiteral(argumentValue)
//                            )
//                        }
//
//                        // combine location and parameter check
//                        val transitionCheck = if (parameterChecks.isEmpty()) {
//                            locationCheck
//                        } else {
//                            val parameterCheck = UppaalUtil.chainLogicalExpression(parameterChecks, LogicalOperator.AND, "true")
//                            UppaalUtil.createLogicalExpression(LogicalOperator.AND, locationCheck, parameterCheck)
//                        }
//
//                        conditions.add(transitionCheck)
//                    }
//                }
//            }
//
//            // check if any of the conditions are true
//            val transitionCheck = UppaalUtil.chainLogicalExpression(conditions, LogicalOperator.OR, "false")
//
//            if (edge is ChanceEdge) {
//                // weight is zero unless this edge leads to the location of the current transtion
//
//                val weightExpr = ExpressionsFactory.eINSTANCE.createConditionExpression()
//                weightExpr.ifExpression = transitionCheck
//                weightExpr.thenExpression = UppaalUtil.createLiteral("1")
//                weightExpr.elseExpression = UppaalUtil.createLiteral("0")
//
//                edge.weight = weightExpr
//            } else {
//                // don't restrict guards of edges to chance nodes, this is handled by setting the weights of the
//                // chance edges
//                if (edge.target !is ChanceNode) {
//
//                    // combine with current guard
//                    if (edge.guard == null) {
//                        edge.guard = transitionCheck
//                    } else {
//                        edge.guard = UppaalUtil.createLogicalExpression(LogicalOperator.AND, edge.guard, transitionCheck)
//                    }
//                }
//            }
//
//            // add transition counter variable update
//
//            // dont add the update to synchronisation receivers because that would cause multiple
//            // increments of the variable
//            if (edge.synchronization == null || edge.synchronization.kind == SynchronizationKind.SEND) {
//                // dont add update to chance edges to avoid a double update
//                if (edge !is ChanceEdge) {
//                    val update = ExpressionsFactory.eINSTANCE.createPostIncrementDecrementExpression()
//                    update.operator = IncrementDecrementOperator.INCREMENT
//                    update.expression = UppaalUtil.createIdentifier(currentTransition)
//                    edge.update.add(update)
//                }
//            }
//        }
    }

    private fun findNSTAEdges(transition: SymbolicTransition, nsta: NSTA): List<Edge> {
        val result = mutableListOf<Edge>()

        for ((locationIndex, targetLocation) in transition.target.locations.withIndex()) {
            if (transition.source != null) {
                val sourceLocation = transition.source.locations[locationIndex]
                val templateName = sourceLocation.process.template.getPropertyValue("name")

                val nstaEdges = nsta.template.filter { it.name == templateName }
                        .flatMap { it.edge }
                        .filter { it.source.name == sourceLocation.name }
                        .filter { it.target.name == targetLocation.name }

                result.addAll(nstaEdges)
            }
        }

        return result
    }

    private fun addTransitionConditions(trace: SymbolicTrace, nsta: NSTA, locIndex: Map<Location, Int>, sys: UppaalSystem) {
        // ignore initial state (source of first transition) for now.

        for ((transitionIndex, transition) in trace.withIndex()) {
            val state = transition.target

            val conditions = mutableListOf<Expression>()

            // check process locations
            for (location in state.locations) {
                val templateName = location.location.template.getPropertyValue("name")
                val nstaTemplate = nsta.template.find { it.name == templateName }!!
                val nstaLocation = nstaTemplate.location.find { it.name == location.name }!!

                val index = locIndex[nstaLocation]!!

                val processIndices = nstaTemplate.parameter.map {
                    val parameterName = it.variableDeclaration.variable[0].name
                    location.process.translator.translate(parameterName)
                }

                val locationCheck = ExpressionsFactory.eINSTANCE.createCompareExpression()
                locationCheck.operator = CompareOperator.EQUAL

                val locationVariable = UppaalUtil.createVariable(getLocationVariableName(nstaTemplate))
                val locationExpression = UppaalUtil.createIdentifier(locationVariable)
                for (index in processIndices) {
                    locationExpression.index.add(UppaalUtil.createLiteral(index))
                }

                locationCheck.firstExpr = locationExpression
                locationCheck.secondExpr = UppaalUtil.createLiteral(index.toString())

                conditions.add(locationCheck)
            }

            for (i in state.variableValues.indices) {
                val variableName = sys.getVariableName(i)

                // ignore template-local variables because we cannot access them from the test automaton.
                if (!variableName.contains(".")) {
                    val condition = ExpressionsFactory.eINSTANCE.createCompareExpression()
                    condition.operator = CompareOperator.EQUAL
                    condition.firstExpr = UppaalUtil.createIdentifier(UppaalUtil.createVariable(variableName))
                    condition.secondExpr = UppaalUtil.createLiteral(state.variableValues[i].toString())

                    conditions.add(condition)
                }
            }

            var condition: Expression? = null
            for (expression in conditions) {
                if (condition == null) {
                    condition = expression
                } else {
                    val newCondition = ExpressionsFactory.eINSTANCE.createLogicalExpression()
                    newCondition.operator = LogicalOperator.AND
                    newCondition.firstExpr = condition
                    newCondition.secondExpr = expression

                    condition = newCondition
                }
            }

            // set the condition to true if there are no other conditions
            condition = condition ?: UppaalUtil.createLiteral("true")

            val func = DeclarationsFactory.eINSTANCE.createFunction()
            func.name = "transition_$transitionIndex"
            func.returnType = UppaalUtil.createTypeReference(nsta.bool)
            func.block = StatementsFactory.eINSTANCE.createBlock()

            val returnStatement = StatementsFactory.eINSTANCE.createReturnStatement()
            returnStatement.returnExpression = condition
            func.block.statement.add(returnStatement)

            println(transition.transitionDescription)
            println(Serialization().function(func))

            val funcDecl = DeclarationsFactory.eINSTANCE.createFunctionDeclaration()
            funcDecl.function = func
    //            template.declarations.declaration.add(funcDecl)
            nsta.globalDeclarations.declaration.add(funcDecl)

            // printing contraints for debug

            val constraints = mutableListOf<String>()
            state.polyhedron.getAllConstraints(constraints)
            for (c in constraints) {
                println(c)
            }
        }
    }

    /**
     * Transforms the model by adding an update to every edge storing the current location of a process in a global
     * variable.
     * @return A mapping of every Location to a template-unique identifier.
     */
    private fun annotateLocations(nsta: NSTA): Map<Location, Int> {
        val locIndex = mutableMapOf<Location, Int>()

        for (template in nsta.template) {
            // create variables to track current location for every template
            val decl = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
            val locVarName = getLocationVariableName(template)
            val locVar = UppaalUtil.createVariable(locVarName)

            for (param in template.parameter) {
                val index = DeclarationsFactory.eINSTANCE.createTypeIndex()
                index.typeDefinition = EcoreUtil.copy(param.variableDeclaration.typeDefinition)
                locVar.index.add(index)
            }

            decl.typeDefinition = UppaalUtil.createTypeReference(nsta.int)

            decl.variable.add(locVar)
            nsta.globalDeclarations.declaration.add(decl)

            for ((curIndex, location) in template.location.withIndex()) {
                locIndex[location] = curIndex
            }

            for (edge in template.edge) {
                val idx = locIndex[edge.target]!!

                val assignment = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
                assignment.operator = AssignmentOperator.EQUAL

                val locVar = UppaalUtil.createVariable(locVarName)
                val id = UppaalUtil.createIdentifier(locVar)

                for (param in template.parameter) {
                    id.index.add(UppaalUtil.createIdentifier(param.variableDeclaration.variable[0]))
                }

                assignment.firstExpr = id
                assignment.secondExpr = UppaalUtil.createLiteral(idx.toString())

                edge.update.add(assignment)
            }
        }

        return locIndex
    }

    private fun getLocationVariableName(template: Template) = "__loc_${template.name}"

    /**
     * Transforms the NSTA to create multiple copies of every instantiated template, so that every process maps to
     * exactly one template. Currently unused.
     */
    private fun instantiateTemplates(nsta: NSTA): NSTA {
        val doc = UppaalUtil.toDocument(nsta, Document(PrototypeDocument()))
        val sys = UppaalUtil.compile(doc)

        val result = EcoreUtil.copy(nsta)
        result.template.clear()

        result.systemDeclarations.system.instantiationList[0].template.clear()
        result.systemDeclarations.declaration.clear()

        for (process in sys.processes) {
            val templateName = process.template.getPropertyValue("name")
            val template = nsta.template.find { it.name == templateName }!!

            val newTemplate = EcoreUtil.copy(template)!!
            newTemplate.name = process.name.replace("(", "_").replace(")", "_")

            result.template.add(newTemplate)

            val templateInstance = TemplatesFactory.eINSTANCE.createRedefinedTemplate()
            templateInstance.referredTemplate = newTemplate
            templateInstance.name = newTemplate.name + "_instance"

            templateInstance.declaration = SystemFactory.eINSTANCE.createTemplateDeclaration()

            for (parameter in template.parameter) {
                val parameterName = parameter.variableDeclaration.variable[0].name
                val argumentValue = process.translator.translate(parameterName)
//                templateInstance.parameter.ad

                templateInstance.declaration.argument.add(UppaalUtil.createLiteral(argumentValue))
            }

            result.systemDeclarations.declaration.add(templateInstance.declaration)

            result.systemDeclarations.system.instantiationList[0].template.add(templateInstance)
        }

//        println(Serialization().main(result))
//        UppaalUtil.toDocument(result, Document(PrototypeDocument())).save("test.xml")
        return result
    }

    /**
     * Adds a global clock variable that is never reset to the NSTA.
     */
    private fun addClockVariable(nsta: NSTA) {
        val decl = DeclarationsFactory.eINSTANCE.createClockVariableDeclaration()
        decl.typeDefinition = UppaalUtil.createTypeReference(nsta.clock)
        decl.variable.add(UppaalUtil.createVariable(CLOCK_VARIABLE))

        nsta.globalDeclarations.declaration.add(decl)
    }

    private fun getSymbolicTrace(nsta: NSTA, condition: String): Pair<SymbolicTrace?, UppaalSystem> {
        val sys = toUppaalSystem(nsta)

        val query = "A[] not ($condition)"
        val (_, t) = AbstractProperty.engineQuery(sys, query, "trace 1")

        return Pair(t, sys)
    }

    companion object {
        const val CLOCK_VARIABLE = "__time"
    }
}