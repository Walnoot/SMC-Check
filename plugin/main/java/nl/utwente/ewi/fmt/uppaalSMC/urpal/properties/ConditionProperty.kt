package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ValidationSpec

@SanityCheck(name = "Check Condition", shortName = "condition")
class ConditionProperty : SafetyProperty() {
    override fun translateNSTA(nsta: NSTA, config: ValidationSpec.PropertyConfiguration): String {
        return config.parameters["condition"] ?: error("Missing parameter 'condition'")
    }

    override fun getParameters(): List<PropertyParameter> {
        return super.getParameters() + listOf(PropertyParameter("condition", "Condition", ArgumentType.STRING))
    }
}