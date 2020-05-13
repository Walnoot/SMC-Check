package nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers

import com.uppaal.model.core2.AbstractTemplate
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Element
import com.uppaal.model.core2.Nail
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.eclipse.emf.ecore.EObject
import org.muml.uppaal.declarations.Declarations
import org.muml.uppaal.declarations.VariableDeclaration
import org.muml.uppaal.expressions.IdentifierExpression
import org.muml.uppaal.expressions.LogicalExpression
import org.muml.uppaal.expressions.LogicalOperator

/**
 * Generic interface for reachability checkers of UPPAAL NSTA models. Given a state formula, the checker attempts to
 * answer if the model can reach a state where the formula is satisfied.
 */
abstract class ReachabilityChecker(val doc: Document) {
    abstract fun isReachable(condition: String, nsta: NSTA): Pair<CheckResult, (() -> Unit)?>

    fun toDocument(nsta: NSTA): Document {
        val tDoc = UppaalUtil.toDocument(nsta, doc)

        // add x, y fields to the document where there are none so that it can be shown in the editor
        extendNode(doc, tDoc)

        return tDoc
    }

    fun toUppaalSystem(nsta: NSTA): UppaalSystem {
        return UppaalUtil.compile(toDocument(nsta))
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
            val templateName = template.getPropertyValue("name") as String
            val locs = UppaalUtil.getLocations(doc, templateName)
            val tLocs = UppaalUtil.getLocations(tDoc, templateName)
            for (i in locs.indices) {
                val origLoc = locs[i]
                val transLoc = tLocs[i]

                setCoords(origLoc, transLoc)

                for (label in locationLabels) {
                    setCoords(origLoc.getProperty(label), transLoc.getProperty(label))
                }
            }

            val edges = UppaalUtil.getEdges(doc, templateName)
            val tEdges = UppaalUtil.getEdges(tDoc, templateName)

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

            val branches = UppaalUtil.getBranches(doc, templateName)
            val tBranches = UppaalUtil.getBranches(tDoc, templateName)
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

    enum class CheckResult {
        REACHABLE, UNREACHABLE, MAYBE_REACHABLE
    }

    companion object {
        /**
         * Abstracts the NSTA to a NTA so that it can be used for symbolic verification.
         */
        fun abstractNSTA(nsta: NSTA) {
            // hide all clocks whose clock rate is variable in one or more locations
            // hide variables of type 'double'
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
                    it.invariant = UppaalUtil.createLiteral("true")
                }
            }

            // hide vars in guards, updates
            nsta.template.flatMap { it.edge }.forEach {
                if (it.guard != null && removeVarReference(it.guard, hideVars)) {
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
                    e.firstExpr = UppaalUtil.createLiteral("true")
                }

                if (removeVarReference(e.secondExpr, hideVars)) {
//                println("change logical expr")
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