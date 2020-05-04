package nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers

import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.EditorUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil

class SymbolicChecker(doc: Document) : ReachabilityChecker(doc) {
    override fun isReachable(condition: String, nsta: NSTA): Pair<CheckResult, (() -> Unit)?> {
        abstractNSTA(nsta)

        val tDoc = toDocument(nsta)
        val tSys = UppaalUtil.compile(tDoc)

        // symbolic query, in all states the safety condition is true
        val query = "E<> !($condition)"
        println(query)

        val (qr, t) = AbstractProperty.engineQuery(tSys, query, "trace 2")

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

        val reachable = when(qr.status) {
            QueryResult.OK -> CheckResult.REACHABLE
            QueryResult.NOT_OK -> CheckResult.UNREACHABLE
            else -> CheckResult.MAYBE_REACHABLE
        }

        return Pair(reachable, showTrace)
    }
}