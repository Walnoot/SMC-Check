package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import nl.utwente.ewi.fmt.uppaalSMC.NSTA

@SanityCheck(name = "Check Condition", shortName = "condition")
class ConditionProperty : SafetyProperty() {
    override fun translateNSTA(nsta: NSTA, properties: Map<String, Any>): String {
        return properties["condition"] as String
    }
}