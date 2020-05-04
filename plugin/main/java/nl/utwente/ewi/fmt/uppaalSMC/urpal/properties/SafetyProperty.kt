package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers.ConcolicChecker
import nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers.ConcreteChecker
import nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers.ReachabilityChecker
import nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers.SymbolicChecker
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ValidationSpec
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.*
import org.muml.uppaal.expressions.*
import org.muml.uppaal.templates.LocationKind
import org.muml.uppaal.types.TypesFactory

abstract class SafetyProperty: AbstractProperty() {
    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, config: ValidationSpec.PropertyConfiguration): SanityCheckResult {
        val transNSTA = EcoreUtil.copy(nsta)

        // for inexplicable reasons the name of the location is stored in the comment field
        // so we set the name field to the comment value so the correct values show up in the serialized model
        for (loc in transNSTA.template.flatMap { it.location }) {
            if (loc.comment != null) {
                loc.name = loc.comment
            }
        }

        val checkType = config.parameters.getOrDefault(CHECKER_TYPE, "symbolic")
        val checker = when(checkType) {
            "symbolic" -> SymbolicChecker(doc)
            "concrete" -> ConcreteChecker(doc)
            "concolic" -> ConcolicChecker(doc)
            else -> throw IllegalArgumentException("Unknown checker type $checkType")
        }

//        if (symbolic) {
//            abstractNSTA(transNSTA)

            if (config.spec.timeLimitEnabled) {
                val time = config.spec.timeLimit

                addTimeLimitTemplate(transNSTA, time)
            }
//        }

        // override any constants in the global declarations if applicable
        setConstants(transNSTA, config.spec.overrideConstants)

        // translate the NSTA and get a query that specifies the unsafe state
        val cond =  translateNSTA(transNSTA, config)

        val (reachable, showTrace) = checker.isReachable(cond, transNSTA)

        val message = getMessage(config, reachable)

        return SanityCheckResult(message, reachable == ReachabilityChecker.CheckResult.UNREACHABLE, showTrace)

//        val tDoc = UppaalUtil.toDocument(transNSTA, doc)
//
////        translateDocument(tDoc, transNSTA, sys)
//
//        // add x, y fields to the document where there are none so that it can be shown in the editor
//        extendNode(doc, tDoc)
//
//        tDoc.save("/home/michiel/test.xml")
//
//        val tSys = UppaalUtil.compile(tDoc)
//        UppaalUtil.reconnect()
//
//        if (symbolic) {
//            // symbolic query, in all states the safety condition is false
//            val query = "A[] not ($cond)"
//            println(query)
//
//            val (qr, t) = engineQuery(tSys, query, "trace 2")
//
//            println(qr)
//            println(qr.exception)
//            println(qr.message)
//
//            val showTrace: (() -> Unit)? = if (t != null) {
//                {
//                    println("Loading trace")
//
//                    EditorUtil.runQueryGUI(query, tDoc, tSys)
//                    EditorUtil.showPane("Simulator")
//
////                    MainUI.getDocument().set(tDoc)
////                    MainUI.getSystemr().set(tSys)
////                    MainUI.getTracer().set(t)
//                }
//            } else {
//                null
//            }
//
//            if (qr.status == QueryResult.OK) {
//                return SanityCheckResult("Query '$query' satisfied", true, showTrace)
//            } else {
//                return SanityCheckResult("Query '$query' not satisfied", false, showTrace)
//            }
//        } else {
//            // TODO: get from specification
//            val simTime = 66
//            val numSims = 1000
//
//            // simulate N times, filter on the unsafe condition, and stop after one trace is found
//            val query = "simulate [<=$simTime;$numSims] {1} :1: ($cond)"
//            println(query)
//
//            val (qr, t) = engineQueryConcrete(tSys, query, "trace 1")
//
//            println(qr)
//            println(qr.exception)
//            println(qr.message)
//
//            val showTrace: (() -> Unit)? = if (t != null) {
//                {
//                    println("Loading concrete trace")
//
//                    EditorUtil.runQueryGUI(query, tDoc, tSys)
//                    EditorUtil.showPane("ConcreteSimulator")
//
//                    // Loading concrete traces using the same method as symbolic traces is unsupported at the moment
////                    MainUI.getSystemr().set(tSys)
////                    MainUI.getConcreteTracer().set(t)
//                }
//            } else {
//                null
//            }
//
//            if (qr.status == QueryResult.OK) {
//                return SanityCheckResult("Query '$query' satisfied", true, showTrace)
//            } else {
//                return SanityCheckResult("Query '$query' not satisfied", false, showTrace)
//            }
//        }
    }

    override fun getParameters(): List<PropertyParameter> {
        return listOf(PropertyParameter(CHECKER_TYPE, "Check type", ArgumentType.CHECK_TYPE))
    }

    /**
     * Add a process to the NSTA that blocks after the specified time has passed.
     */
    private fun addTimeLimitTemplate(nsta: NSTA, time: String) {
        val template = UppaalUtil.createTemplate(nsta, "__limit_time")
        val clockDecl = DeclarationsFactory.eINSTANCE.createClockVariableDeclaration()

        val tr = TypesFactory.eINSTANCE.createTypeReference()
        tr.referredType = nsta.clock
        clockDecl.typeDefinition = tr

        val clock = UppaalUtil.createVariable("__time")
        clockDecl.variable.add(clock)
        template.declarations = DeclarationsFactory.eINSTANCE.createLocalDeclarations()
        template.declarations.declaration.add(clockDecl)

        nsta.systemDeclarations.system.instantiationList[0].template.add(template)

        val start = UppaalUtil.createLocation(template, "__start")
        template.init = start

        val invariant = ExpressionsFactory.eINSTANCE.createCompareExpression()
        invariant.operator = CompareOperator.LESS_OR_EQUAL
        invariant.firstExpr = UppaalUtil.createIdentifier(clock)
        invariant.secondExpr = UppaalUtil.createLiteral(time)
        start.invariant = invariant

        val end = UppaalUtil.createLocation(template, "__end")
        end.locationTimeKind = LocationKind.URGENT

        val edge = UppaalUtil.createEdge(start, end)
        val guard = ExpressionsFactory.eINSTANCE.createCompareExpression()
        guard.operator = CompareOperator.GREATER_OR_EQUAL
        guard.firstExpr = UppaalUtil.createIdentifier(clock)
        guard.secondExpr = UppaalUtil.createLiteral(time)
        edge.guard = guard
    }

    protected abstract fun translateNSTA(nsta: NSTA, config: ValidationSpec.PropertyConfiguration): String

    open fun getMessage (config: ValidationSpec.PropertyConfiguration, result: ReachabilityChecker.CheckResult): String {
        return when (result) {
            ReachabilityChecker.CheckResult.REACHABLE -> "Property reachable."
            ReachabilityChecker.CheckResult.UNREACHABLE -> "Property not reachable."
            ReachabilityChecker.CheckResult.MAYBE_REACHABLE -> "Property may be reachable."
        }
    }

    /**
     * Override constants in the global specification
     */
    private fun setConstants(nsta: NSTA, properties: Map<String, Any>) {
        UppaalUtil.getConstants(nsta)
                .filter { properties.containsKey(it.name) } // if it is overwritten in the spec
                .forEach {
                    // change the initializer to the specified value as a literal
                    val initializer = it.initializer
                    if (initializer is ExpressionInitializer) {
                        initializer.expression = UppaalUtil.createLiteral(properties[it.name] as String)

                        println("Set variable ${it.name} to ${properties[it.name]}")
                    }
                }
    }

    /**
     * Unused
     * Set the name of unnamed locations to due UPPAAL EMF parsing oddities.
     */
    private fun translateDocument(doc: Document, nsta: NSTA, sys: UppaalSystem) {
        for (template in nsta.template) {
            for (process in sys.processes) {
                if (process.template.getPropertyValue("name") == template.name) {
                    for (i in process.locations.indices) {
                        val lname = template.name
                        val pname = process.locations[i].name
                        val tname = template.location[i].name

                        if (!pname.isNullOrEmpty()) {
                            var loc = doc.getTemplate(lname).first
                            while (loc != null) {
                                if (loc.getPropertyValue("name") == tname) {
                                    //println("found loc in document")
                                    println("orig=$pname, trans=$tname")
                                    loc.setProperty("name", pname)
                                    break
                                }
                                loc = loc.next
                            }
                        }
                    }

                    break
                }
            }
        }
    }

    companion object {
        const val CHECKER_TYPE = "check_type"
    }
}