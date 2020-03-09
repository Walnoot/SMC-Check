package nl.utwente.ewi.fmt.uppaalSMC.urpal.checkers

import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.EditorUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil

class ConcreteChecker(doc: Document) : ReachabilityChecker(doc) {
    override fun isReachable(condition: String, nsta: NSTA): Pair<CheckResult, (() -> Unit)?> {
        val tDoc = toDocument(nsta)
        val tSys = UppaalUtil.compile(tDoc)

        // TODO: get from specification
        val simTime = 66
        val numSims = 1000

        // simulate N times, filter on the unsafe condition, and stop after one trace is found
        val query = "simulate [<=$simTime;$numSims] {0} :1: ($condition)"
        println(query)

        val (qr, t) = AbstractProperty.engineQueryConcrete(tSys, query, "trace 1")

        println(qr)
        println(qr.exception)
        println(qr.message)

        val showTrace: (() -> Unit)? = if (t != null) {
            {
                println("Loading concrete trace")

                EditorUtil.runQueryGUI(query, tDoc, tSys)
                EditorUtil.showPane("ConcreteSimulator")

                // Loading concrete traces using the same method as symbolic traces is unsupported at the moment
//                MainUI.getSystemr().set(tSys)
//                MainUI.getConcreteTracer().set(t)
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