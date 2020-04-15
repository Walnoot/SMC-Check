package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.io.IOException

import com.uppaal.engine.EngineException
import com.uppaal.engine.QueryFeedback
import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Query
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.concrete.ConcreteTrace
import com.uppaal.model.system.symbolic.SymbolicState
import com.uppaal.model.system.symbolic.SymbolicTrace

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ValidationSpec
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


abstract class AbstractProperty {

    protected abstract fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, config: ValidationSpec.PropertyConfiguration): SanityCheckResult

    fun check(nsta: NSTA, doc: Document, sys: UppaalSystem, config: ValidationSpec.PropertyConfiguration): SanityCheckResult {
        var result: SanityCheckResult? = null

        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit {
            result = doCheck(nsta, doc, sys, config)
        }
        try {
            future.get(timeout.toLong(), TimeUnit.SECONDS)
            return result!!
        } catch (e: TimeoutException) {
            future.cancel(true)
            UppaalUtil.engine.cancel()
            return SanityCheckResult("Timeout", false, null)
        } catch (e: InterruptedException) {
            future.cancel(true)
            UppaalUtil.engine.cancel()
            return SanityCheckResult("Cancelled", false, null)
        } catch (e: Exception) {
//            return SanityCheckResult("Exception", false)
            throw e
        }
    }

    fun shortName() = javaClass.getAnnotation(SanityCheck::class.java).shortName

    fun name() = javaClass.getAnnotation(SanityCheck::class.java).name

    open fun getParameters(): List<PropertyParameter> = emptyList()

    companion object {
        val properties = arrayOf(
            ConditionProperty(),
            ReceiveSyncProperty(),
			DeadlockProperty(),
            TemplateLocationReachabilityMeta()
//			SystemLocationReachabilityMeta(),
//          SystemEdgeReachabilityMeta(),
//          TemplateEdgeReachabilityMeta(),
//          InvariantViolationProperty()
//			UnusedDeclarationsProperty()
		)
        internal const val DEFAULT_OPTIONS_DFS = "order 1\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 1\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01"
        internal const val DEFAULT_OPTIONS_BFS = "order 0\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 1\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01"
        var stateSpaceSize = 0
        var timeout = 300
        var simTime = 100

        internal var maxMem: Long = 0

        @Throws(IOException::class, EngineException::class)
        internal fun engineQuery(sys: UppaalSystem, init: SymbolicState?, query: String, options: String): Pair<QueryResult, SymbolicTrace?> {
            var trace: SymbolicTrace? = null

            val qf = object : QueryFeedback {

                override fun setTrace(paramChar: Char, paramString: String?, paramConcreteTrace: ConcreteTrace, paramQueryResult: QueryResult) {
                }

                override fun setTrace(paramChar: Char, paramString: String?, paramSymbolicTrace: SymbolicTrace, paramQueryResult: QueryResult) {
                    trace = paramSymbolicTrace
                }

                override fun setSystemInfo(paramLong1: Long, paramLong2: Long, paramLong3: Long) {}

                override fun setResultText(paramString: String) {}

                override fun setProgressAvail(paramBoolean: Boolean) {}

                override fun setProgress(paramInt: Int, paramLong1: Long, memory: Long, paramLong3: Long, paramLong4: Long,
                                         paramLong5: Long, paramLong6: Long, millis: Long, paramLong8: Long, paramLong9: Long) {
                    if (maxMem < memory) {
                        maxMem = memory
                    }
                }

                override fun setLength(paramInt: Int) {}

                override fun setFeedback(paramString: String?) {}

                override fun setCurrent(paramInt: Int) {}

                override fun appendText(paramString: String) {}
            }
            val result = if (init == null)
                UppaalUtil.engine.query(sys, options, Query(query, ""), qf)
            else
                UppaalUtil.engine.query(sys, init, options, Query(query, ""), qf)

            return Pair(result, trace)
        }

        @Throws(IOException::class, EngineException::class)
        internal fun engineQueryConcrete(sys: UppaalSystem, query: String, options: String): Pair<QueryResult, ConcreteTrace?> {
            var trace : ConcreteTrace? = null

            val qf = object : QueryFeedback {

                override fun setTrace(paramChar: Char, paramString: String?, paramConcreteTrace: ConcreteTrace, paramQueryResult: QueryResult) {
                    trace = paramConcreteTrace
                }

                override fun setTrace(paramChar: Char, paramString: String?, paramSymbolicTrace: SymbolicTrace, paramQueryResult: QueryResult) {
                }

                override fun setSystemInfo(paramLong1: Long, paramLong2: Long, paramLong3: Long) {}

                override fun setResultText(paramString: String) {}

                override fun setProgressAvail(paramBoolean: Boolean) {}

                override fun setProgress(paramInt: Int, paramLong1: Long, memory: Long, paramLong3: Long, paramLong4: Long,
                                         paramLong5: Long, paramLong6: Long, millis: Long, paramLong8: Long, paramLong9: Long) {
                    if (maxMem < memory) {
                        maxMem = memory
                    }
                }

                override fun setLength(paramInt: Int) {}

                override fun setFeedback(paramString: String?) {}

                override fun setCurrent(paramInt: Int) {}

                override fun appendText(paramString: String) {}
            }
            val result = UppaalUtil.engine.query(sys, options, Query(query, ""), qf)

            return Pair(result, trace)
        }

        @Throws(IOException::class, EngineException::class)
        internal fun engineQuery(sys: UppaalSystem, query: String, options: String): Pair<QueryResult, SymbolicTrace?> {
            return engineQuery(sys, null, query, options)
        }
    }
}
