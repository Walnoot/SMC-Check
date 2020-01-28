package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.AbstractTemplate
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Element
import com.uppaal.model.core2.Nail
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.EditorUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.*
import org.muml.uppaal.expressions.*
import org.muml.uppaal.templates.LocationKind
import org.muml.uppaal.types.TypesFactory

abstract class SafetyProperty: AbstractProperty() {
    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, properties: Map<String, Any>): SanityCheckResult {
        val transNSTA = EcoreUtil.copy(nsta)

        // for inexplicable reasons the name of the location is stored in the comment field
        // so we set the name field to the comment value so the correct values show up in the serialized model
        for (loc in transNSTA.template.flatMap { it.location }) {
            if (loc.comment != null) {
                loc.name = loc.comment
            }
        }

        val checkType = properties.getOrDefault("check_type", "symbolic") as String
        val symbolic = checkType == "symbolic"

        if (symbolic) {
            abstractNSTA(transNSTA)

            if (properties.containsKey("time")) {
                val time = properties["time"].toString()

                addTimeLimitTemplate(transNSTA, time)
            }
        }

        // override any constants in the global declarations if applicable
        setConstants(transNSTA, properties)

        // translate the NSTA and get a query that specifies the unsafe state
        val cond =  translateNSTA(transNSTA, properties)

        println(AbstractionChecker().checkCondition(cond, transNSTA))

        val tDoc = UppaalUtil.toDocument(transNSTA, doc)

//        translateDocument(tDoc, transNSTA, sys)

        // add x, y fields to the document where there are none so that it can be shown in the editor
        extendNode(doc, tDoc)

        tDoc.save("/home/michiel/test.xml")

        val tSys = UppaalUtil.compile(tDoc)
        UppaalUtil.reconnect()

        if (symbolic) {
            // symbolic query, in all states the safety condition is false
            val query = "A[] not ($cond)"
            println(query)

            val (qr, t) = engineQuery(tSys, query, "trace 2")

            println(qr)
            println(qr.exception)
            println(qr.message)

            val showTrace: (() -> Unit)? = if (t != null) {
                {
                    println("Loading trace")

                    EditorUtil.runQueryGUI(query, tDoc, tSys)
                    EditorUtil.showPane("Simulator")

//                    MainUI.getDocument().set(tDoc)
//                    MainUI.getSystemr().set(tSys)
//                    MainUI.getTracer().set(t)
                }
            } else {
                null
            }

            if (qr.status == QueryResult.OK) {
                return SanityCheckResult("Query '$query' satisfied", true, showTrace)
            } else {
                return SanityCheckResult("Query '$query' not satisfied", false, showTrace)
            }
        } else {
            // TODO: get from specification
            val simTime = 66
            val numSims = 1000

            // simulate N times, filter on the unsafe condition, and stop after one trace is found
            val query = "simulate [<=$simTime;$numSims] {1} :1: ($cond)"
            println(query)

            val (qr, t) = engineQueryConcrete(tSys, query, "trace 1")

            println(qr)
            println(qr.exception)
            println(qr.message)

            val showTrace: (() -> Unit)? = if (t != null) {
                {
                    println("Loading concrete trace")

                    EditorUtil.runQueryGUI(query, tDoc, tSys)
                    EditorUtil.showPane("ConcreteSimulator")

                    // Loading concrete traces using the same method as symbolic traces seems to be unsupported at the moment
//                    MainUI.getSystemr().set(tSys)
//                    MainUI.getConcreteTracer().set(t)
                }
            } else {
                null
            }

            if (qr.status == QueryResult.OK) {
                return SanityCheckResult("Query '$query' satisfied", true, showTrace)
            } else {
                return SanityCheckResult("Query '$query' not satisfied", false, showTrace)
            }
        }
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

    protected abstract fun translateNSTA(nsta: NSTA, properties: Map<String, Any>): String

    /**
     * Override constants in the global specification
     */
    private fun setConstants(nsta: NSTA, properties: Map<String, Any>) {
        nsta.globalDeclarations.declaration
                .filterIsInstance<DataVariableDeclaration>()
                .filter { it.prefix == DataVariablePrefix.CONST } // for every constant variable
                .flatMap { it.variable }
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

    /**
     * Sets the positions of elements in the translated documents to the corresponding positions in the original
     * Document. Allows the translated Document to be shown in the editor without errors.
     */
    private fun extendNode(doc: Document, tDoc: Document) {
        val locationLabels = listOf("name", "init", "urgent", "committed", "invariant", "exponentialrate", "comments")
        val edgeLabels = listOf("select", "guard", "synchronisation", "assignment", "comments", "probability")

        var template = doc.templates
        while (template != null) {
            val locs = UppaalUtil.getLocations(doc, template.getPropertyValue("name") as String)
            val tLocs = UppaalUtil.getLocations(tDoc, template.getPropertyValue("name") as String)
            for (i in locs.indices) {
                val origLoc = locs[i]
                val transLoc = tLocs[i]

                setCoords(origLoc, transLoc)

                for (label in locationLabels) {
                    setCoords(origLoc.getProperty(label), transLoc.getProperty(label))
                }
            }

            val edges = UppaalUtil.getEdges(doc, template.getPropertyValue("name") as String)
            val tEdges = UppaalUtil.getEdges(tDoc, template.getPropertyValue("name") as String)
            for (i in edges.indices) {
                val origEdge = edges[i]
                val transEdge = tEdges[i]

                setCoords(origEdge, transEdge)

                for (label in edgeLabels) {
                    setCoords(origEdge.getProperty(label), transEdge.getProperty(label))
                }

                // clear previous nails
                transEdge.first = null

                var curNail = origEdge.nails
                var prevNail : Nail? = null
                while (curNail != null) {
                    val newNail = transEdge.createNail()
                    newNail.setProperty("x", curNail.x)
                    newNail.setProperty("y", curNail.y)
                    transEdge.insert(newNail, prevNail)
                    prevNail = newNail
//                    transEdge.insert(newNail, null)

                    curNail = curNail.next as Nail?
                }
            }

            val branches = UppaalUtil.getBranches(doc, template.getPropertyValue("name") as String)
            val tBranches = UppaalUtil.getBranches(tDoc, template.getPropertyValue("name") as String)
            for (i in branches.indices) {
                setCoords(branches[i], tBranches[i])
            }

            template = template.next as AbstractTemplate?
        }
    }

    private fun setCoords(el1: Element, el2: Element) {
        if (el1.getProperty("x") != null) {
            el2.setProperty("x", el1.x)
            el2.setProperty("y", el1.y)
        }
    }

    companion object {
        /**
         * Abstracts the NSTA to a NTA so that it can be used for symbolic verification.
         */
        fun abstractNSTA(nsta: NSTA) {
            // hide all clocks whose clock rate is variable in one or more locations
            // ignore name space issues for now (variables in templates with the same name as a global variable)
            val hideVars = mutableSetOf<String>()
            nsta.eAllContents().forEach {
                if (it is IdentifierExpression && it.isClockRate) {
                    hideVars.add(it.identifier.name)
                }

                if (it is VariableDeclaration && it.typeDefinition.baseType == nsta.double.baseType) {
                    it.variable.forEach { hideVars.add(it.name) }
                }
            }

            // hide vars in invariants
            nsta.template.flatMap { it.location }.forEach {
                if (it.invariant != null && removeVarReference(it.invariant, hideVars)) {
//                println("remove invariant in $it")
                    it.invariant = UppaalUtil.createLiteral("true")
                }
            }

            // hide vars in guards, update
            nsta.template.flatMap { it.edge }.forEach {
                if (it.guard != null && removeVarReference(it.guard, hideVars)) {
//                println("remove guard in $it")
                    it.guard = UppaalUtil.createLiteral("true")
                }

                if (it.update != null) {
                    for (update in it.update.toList()) {
                        if(removeVarReference(update, hideVars)) {
                            it.update.remove(update)
                        }
                    }
                }
            }

            // hide vars in global and template declarations
            hideDeclaration(nsta.globalDeclarations, hideVars)
            nsta.template.forEach {
                if (it.declarations != null) {
                    hideDeclaration(it.declarations, hideVars)
                }
            }
        }

        private fun removeVarReference (e: EObject, hideVars: Collection<String>): Boolean {
            if (e is IdentifierExpression && e.identifier.name in hideVars) {
//            println("found hidden var")
                return true
            }

            if (e is LogicalExpression) {
                if (removeVarReference(e.firstExpr, hideVars)) {
//                println("change logical expr")

                    e.operator = LogicalOperator.AND
                    e.firstExpr = UppaalUtil.createLiteral("true")
                }

                if (removeVarReference(e.secondExpr, hideVars)) {
//                println("change logical expr")

                    e.operator = LogicalOperator.AND
                    e.secondExpr = UppaalUtil.createLiteral("true")
                }
            } else {
                e.eAllContents().forEach {
                    if (removeVarReference(it, hideVars)) {
//                    println("hidden var in child")
                        return true
                    }
                }
            }

            return false
        }

        private fun hideDeclaration(declarations: Declarations, hideVars: Collection<String>) {
            declarations.declaration.toList().filterIsInstance<VariableDeclaration>().forEach {
                for (v in it.variable.toList()) {
                    if (v.name in hideVars) {
                        it.variable.remove(v)
                    }
                }

                if (it.variable.isEmpty()) {
                    declarations.declaration.remove(it)
                }
            }
        }
    }
}