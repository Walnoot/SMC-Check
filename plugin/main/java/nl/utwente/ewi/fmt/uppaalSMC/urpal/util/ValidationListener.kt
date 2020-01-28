package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult

interface ValidationListener {
    fun onCheckFinished(result: SanityCheckResult)
}