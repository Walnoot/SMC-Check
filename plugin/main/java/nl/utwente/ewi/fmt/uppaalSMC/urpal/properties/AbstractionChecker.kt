package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.system.IdentifierTranslator
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.symbolic.SymbolicTrace
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.Serialization
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.DeclarationsFactory
import org.muml.uppaal.declarations.system.SystemFactory
import org.muml.uppaal.expressions.*
import org.muml.uppaal.statements.StatementsFactory
import org.muml.uppaal.templates.*

class AbstractionChecker {
    val CLOCK_VARIABLE = "__time"

    fun checkCondition(condition: String, nsta: NSTA): CheckResult {
        addClockVariable(nsta)

        val abstractNSTA = EcoreUtil.copy(nsta)
        SafetyProperty.abstractNSTA(abstractNSTA)

//        instantiateTemplates(nsta)

        val (trace, sys) = getSymbolicTrace(abstractNSTA, condition)

        if (trace == null) {
            return CheckResult.UNREACHABLE
        }

        val locIndex = annotateLocations(nsta)

        addTestAutomaton(nsta, trace, sys, locIndex)

        return CheckResult.MAYBE_REACHABLE
    }

    private fun addTestAutomaton(nsta: NSTA, trace: SymbolicTrace, sys: UppaalSystem, locIndex: Map<Location, Int>) {
        val currentTransition = UppaalUtil.createVariable("__cur_transition")

        val currentTransitionDecl = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
        currentTransitionDecl.typeDefinition = UppaalUtil.createTypeReference(nsta.int)
        currentTransitionDecl.variable.add(currentTransition)
        nsta.globalDeclarations.declaration.add(currentTransitionDecl)

        // ignore initial state (source of first transition) for now.

        for ((transitionIndex, transition) in trace.withIndex()) {
            val state = transition.target

            var conditions = mutableListOf<Expression>()

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

        for (template in nsta.template) {
            for (edge in template.edge) {

                // one of these conditions must be true to be able to fire the edge
                var conditions = mutableListOf<Expression>()

                for ((newTransitionIndex, transition) in trace.withIndex()) {
                    val transitionIndex = newTransitionIndex - 1

                    for (location in transition.target.locations) {
                        // check any of the locations in the target state of this transition match the target of the edge
                        val templateName = location.location.template.getPropertyValue("name")
                        val nstaTemplate = nsta.template.find { it.name == templateName }!!
                        val nstaLocation = nstaTemplate.location.find { it.name == location.name }!!

                        if (nstaLocation == edge.target) {
                            // check for current transition index
                            val locationCheck = UppaalUtil.createCompareExpression(
                                    CompareOperator.EQUAL,
                                    UppaalUtil.createIdentifier(currentTransition),
                                    UppaalUtil.createLiteral(transitionIndex.toString())
                            )

                            // check if the parameters of this template equal the arguments of the compiled system
                            val parameterChecks = nstaTemplate.parameter.flatMap { it.variableDeclaration.variable }.map {
                                val argumentValue = location.process.translator.translate(it.name)
                                UppaalUtil.createCompareExpression(
                                        CompareOperator.EQUAL,
                                        UppaalUtil.createIdentifier(it),
                                        UppaalUtil.createLiteral(argumentValue)
                                )
                            }

                            // combine location and parameter check
                            val transitionCheck = if (parameterChecks.isEmpty()) {
                                locationCheck
                            } else {
                                val parameterCheck = UppaalUtil.chainLogicalExpression(parameterChecks, LogicalOperator.AND, "true")
                                UppaalUtil.createLogicalExpression(LogicalOperator.AND, locationCheck, parameterCheck)
                            }

                            conditions.add(transitionCheck)
                        }
                    }
                }

                // check if any of the conditions are true
                val transitionCheck = UppaalUtil.chainLogicalExpression(conditions, LogicalOperator.OR, "false")

                // combine with current guard
                if (edge.guard == null) {
                    edge.guard = transitionCheck
                } else {
                    edge.guard = UppaalUtil.createLogicalExpression(LogicalOperator.AND, edge.guard, transitionCheck)
                }

                // add transition counter variable update

                // dont add the update to synchronisation receivers because that would cause multiple
                // increments of the variable
                if (edge.synchronization == null || edge.synchronization.kind == SynchronizationKind.SEND) {
                    val update = ExpressionsFactory.eINSTANCE.createPostIncrementDecrementExpression()
                    update.operator = IncrementDecrementOperator.INCREMENT
                    update.expression = UppaalUtil.createIdentifier(currentTransition)
                    edge.update.add(update)
                }
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
        val doc = UppaalUtil.toDocument(nsta, Document(PrototypeDocument()))
        val sys = UppaalUtil.compile(doc)

        val query = "A[] not ($condition)"
        val (_, t) = AbstractProperty.engineQuery(sys, query, "trace 1")

        return Pair(t, sys)
    }

    enum class CheckResult {
        REACHABLE, UNREACHABLE, MAYBE_REACHABLE
    }
}